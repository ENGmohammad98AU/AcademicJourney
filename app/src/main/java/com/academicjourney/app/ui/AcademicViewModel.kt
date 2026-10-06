package com.academicjourney.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.academicjourney.app.data.*
import com.academicjourney.app.platform.JourneyReminders
import com.academicjourney.app.platform.JourneyWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

class AcademicViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = app.applicationContext
    private val database = AcademicDatabase.get(app)
    private val dao = database.academicDao()
    private val prefs = app.getSharedPreferences("journey_preferences", 0)
    val universities = dao.observeUniversities().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val programs = dao.observePrograms().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val courses = dao.observeCourses().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val highSchoolGrades = dao.observeHighSchoolGrades().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val events = dao.observeEvents().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history = dao.observeHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val archives = dao.observeArchives().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pinnedProgram = MutableStateFlow(prefs.getLong("pinned_program", 0))
    val lastCourse = MutableStateFlow(prefs.getLong("last_course", 0))
    val lastBackup = MutableStateFlow(prefs.getLong("last_backup", 0))
    val message = MutableStateFlow<String?>(null)
    val restorePreview = MutableStateFlow<AcademicBackupManager.RestorePlan?>(null)
    private var restoreOriginalEvents = emptyList<AcademicEventEntity>()
    var restoreBusy = MutableStateFlow(false)
        private set

    fun ensureSeeded() = viewModelScope.launch {
        runCatching {
            SeedData.seed(dao)
            HighSchoolSeedData.seed(dao)
            JourneyReminders.reschedule(appContext, dao.getEvents())
            refreshWidget()
        }.onFailure { message.value = "تعذر تحميل البيانات: ${it.message}" }
    }

    private suspend fun refreshWidget() = withContext(Dispatchers.IO) { JourneyWidget.refresh(appContext) }
    private fun mutation(success: String? = null, work: suspend () -> Unit) = viewModelScope.launch {
        runCatching { work() }.onSuccess {
            if (success != null) message.value = success
            runCatching { refreshWidget() }
        }.onFailure { message.value = it.message ?: "تعذر حفظ التغيير." }
    }

    fun pinProgram(id: Long) {
        prefs.edit().putLong("pinned_program", id).apply()
        pinnedProgram.value = id
        mutation { refreshWidget() }
    }
    fun rememberCourse(id: Long) {
        prefs.edit().putLong("last_course", id).apply()
        lastCourse.value = id
    }
    fun setCurrent(id: Long, selected: Boolean) = mutation {
        dao.setCurrentSemester(id, selected)
    }

    private suspend fun record(before: CourseEntity, after: CourseEntity, title: String = after.name) {
        val a = GradeSnapshot.encode(before)
        val b = GradeSnapshot.encode(after)
        if (!GradeSnapshot.same(a, b)) dao.insertHistory(listOf(
            GradeChangeEntity(UUID.randomUUID().toString(), "COURSE", after.id, title, a, b, System.currentTimeMillis())
        ))
    }
    private suspend fun recordSchool(before: HighSchoolGradeEntity, after: HighSchoolGradeEntity) {
        if (before.grade != after.grade) dao.insertHistory(listOf(
            GradeChangeEntity(UUID.randomUUID().toString(), "SCHOOL", after.id, after.subject,
                GradeSnapshot.highSchool(before.grade), GradeSnapshot.highSchool(after.grade), System.currentTimeMillis())
        ))
    }

    fun saveCourse(course: CourseEntity, onSaved: (CourseEntity, CourseEntity) -> Unit = { _, _ -> }) = mutation("تم حفظ الدرجات.") {
        val pair = database.withTransaction {
            val before = dao.getCourse(course.id) ?: error("المقرر غير موجود.")
            val after = GradeSnapshot.apply(before, GradeSnapshot.encode(course))
            val program = dao.getPrograms().first { it.id == before.programId }
            JourneyBackup.validateCourse(after, program)
            record(before, after)
            dao.updateCourse(after)
            before to after
        }
        rememberCourse(course.id)
        onSaved(pair.first, pair.second)
    }

    fun saveNotes(id: Long, notes: String) = mutation("تم حفظ الملاحظة.") {
        require(notes.length <= 50_000) { "الملاحظة أطول من الحد المسموح." }
        database.withTransaction {
            val before = dao.getCourse(id) ?: error("المقرر غير موجود.")
            val after = before.copy(notes = notes)
            record(before, after)
            dao.updateCourse(after)
        }
    }

    fun saveHighSchoolGrade(item: HighSchoolGradeEntity) = mutation {
        database.withTransaction {
            val before = dao.getHighSchoolGrade(item.id) ?: error("المادة غير موجودة.")
            require(item.grade == null || item.grade in 0..before.maxGrade)
            val after = before.copy(grade = item.grade)
            recordSchool(before, after)
            dao.updateHighSchoolGrade(after)
        }
    }

    fun undo(id: String) = mutation("تم التراجع عن التعديل. سُجّلت العملية في السجل.") {
        database.withTransaction {
            val change = dao.getChange(id) ?: error("التعديل غير موجود.")
            require(!change.undone) { "سبق التراجع عن هذا التعديل." }
            if (change.targetKind == "COURSE") {
                val current = dao.getCourse(change.targetId) ?: error("المقرر غير موجود.")
                require(GradeSnapshot.same(GradeSnapshot.encode(current), change.afterJson)) {
                    "تغيّرت بيانات المقرر بعد هذا التعديل. تراجع عن التعديل الأحدث أولًا."
                }
                val restored = GradeSnapshot.apply(current, change.beforeJson)
                JourneyBackup.validateCourse(restored, dao.getPrograms().first { it.id == current.programId })
                record(current, restored, "تراجع • ${current.name}")
                dao.updateCourse(restored)
            } else {
                val current = dao.getHighSchoolGrade(change.targetId) ?: error("المادة غير موجودة.")
                require(GradeSnapshot.same(GradeSnapshot.highSchool(current.grade), change.afterJson)) {
                    "تغيّرت الدرجة بعد هذا التعديل. تراجع عن التعديل الأحدث أولًا."
                }
                val json = JSONObject(change.beforeJson)
                val grade = if (json.isNull("grade")) null else json.getInt("grade")
                require(grade == null || grade in 0..current.maxGrade)
                val restored = current.copy(grade = grade)
                recordSchool(current, restored)
                dao.updateHighSchoolGrade(restored)
            }
            dao.markUndone(id)
        }
    }

    fun saveEvent(event: AcademicEventEntity) = mutation("تم حفظ الموعد.") {
        require(event.title.isNotBlank() && event.title.length <= 2000 && event.place.length <= 2000)
        require(event.kind in listOf("EXAM", "ASSIGNMENT"))
        require(event.startsAt > System.currentTimeMillis()) { "اختر موعدًا في المستقبل." }
        require(event.reminderMinutes == null || event.reminderMinutes in listOf(0, 15, 30, 60, 1440, 2880, 10080))
        require(dao.getCourse(event.courseId) != null)
        dao.saveEvent(event.copy(notifiedAt = null))
        JourneyReminders.schedule(appContext, event.copy(notifiedAt = null))
    }
    fun deleteEvent(id: String) = mutation("تم حذف الموعد.") {
        dao.deleteEvent(id)
        JourneyReminders.cancel(appContext, id)
    }

    fun archive(programId: Long, year: Int, semester: Int, label: String) = mutation("حُفظت لقطة الفصل في الأرشيف.") {
        database.withTransaction {
            val p = dao.getPrograms().first { it.id == programId }
            val selected = dao.getCourses().filter { it.programId == programId && it.academicYear == year && it.semester == semester }
            dao.insertArchives(listOf(SemesterSnapshots.create(p, selected, year, semester, label)))
        }
    }

    fun exportBackup(uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        val result = runCatching {
            withContext(Dispatchers.IO) {
                database.withTransaction {
                    AcademicBackupManager.write(appContext, uri, dao.getUniversities(), dao.getPrograms(),
                        dao.getCourses(), dao.getHighSchoolGrades(), dao.getEvents(), dao.getHistory(), dao.getArchives())
                }
            }
            val now = System.currentTimeMillis()
            prefs.edit().putLong("last_backup", now).apply()
            lastBackup.value = now
        }
        onResult(result.fold({ "تم حفظ النسخة الاحتياطية: الدرجات والملاحظات وفصلي الحالي والمواعيد والسجل والأرشيف." },
            { "تعذر تصدير النسخة الاحتياطية: ${it.message}" }))
    }

    fun importBackup(uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        if (restoreBusy.value) return@launch
        restoreBusy.value = true
        val result = runCatching {
            withContext(Dispatchers.IO) {
                database.withTransaction {
                    val plan = AcademicBackupManager.readAndPlan(appContext, uri, dao.getUniversities(),
                        dao.getPrograms(), dao.getCourses(), dao.getHighSchoolGrades())
                    restoreOriginalEvents = dao.getEvents()
                    val eventDifferences = plan.events.mapNotNull { e ->
                        val old = restoreOriginalEvents.firstOrNull { it.id == e.id }
                        if (old == e) null else "${e.title} • ${if (old == null) "إضافة موعد" else "تغيير موعد محفوظ"}"
                    }
                    val historyIds = dao.getHistory().map { it.id }.toSet()
                    val archiveIds = dao.getArchives().map { it.id }.toSet()
                    plan.copy(differences = plan.differences + eventDifferences,
                        history = plan.history.filter { it.id !in historyIds },
                        archives = plan.archives.filter { it.id !in archiveIds })
                }
            }
        }
        result.onSuccess { restorePreview.value = it }
        onResult(result.fold({ "راجِع الفروق ثم اختر تطبيق الاستعادة." }, { "تعذر قراءة النسخة: ${it.message}" }))
        restoreBusy.value = false
    }

    fun cancelRestore() { if (!restoreBusy.value) restorePreview.value = null }
    fun confirmRestore() = viewModelScope.launch {
        val plan = restorePreview.value ?: return@launch
        if (restoreBusy.value) return@launch
        restoreBusy.value = true
        val result = runCatching {
            database.withTransaction {
                require(dao.getCourses() == plan.originalCourses && dao.getHighSchoolGrades() == plan.originalHighSchool &&
                    dao.getEvents() == restoreOriginalEvents) { "تغيّرت البيانات منذ المعاينة. أعد فتح النسخة لمراجعة الفروق الجديدة." }
                plan.courses.forEach { c -> record(plan.originalCourses.first { it.id == c.id }, c, "استعادة • ${c.name}") }
                plan.highSchoolGrades.forEach { h -> recordSchool(plan.originalHighSchool.first { it.id == h.id }, h) }
                dao.updateCourses(plan.courses)
                dao.updateHighSchoolGrades(plan.highSchoolGrades)
                plan.events.forEach { dao.saveEvent(it) }
                dao.insertHistory(plan.history)
                dao.insertArchives(plan.archives)
            }
            runCatching { JourneyReminders.reschedule(appContext, dao.getEvents()) }
            refreshWidget()
        }
        message.value = result.fold({ "تمت الاستعادة. العلامات والسجل والأرشيف محفوظة." }, { "لم تكتمل الاستعادة: ${it.message}" })
        restorePreview.value = null
        restoreBusy.value = false
    }
}
