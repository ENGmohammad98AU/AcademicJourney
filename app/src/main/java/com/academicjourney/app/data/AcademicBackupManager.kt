package com.academicjourney.app.data

import android.content.Context
import android.net.Uri
import com.academicjourney.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Portable, user-controlled backup of editable academic data.
 *
 * Curriculum-owned fields (IDs, credit hours and passed-without-grade flags) are intentionally
 * not restored. This lets a backup from an older release be applied safely over a newer
 * curriculum without undoing Room migrations or future corrections.
 */
object AcademicBackupManager {
    private const val FORMAT_ID = "com.academicjourney.app.backup"
    private const val SCHEMA_VERSION = 1
    private const val MAX_BACKUP_CHARACTERS = 20_000_000

    data class RestorePlan(
        val courses: List<CourseEntity>,
        val highSchoolGrades: List<HighSchoolGradeEntity>,
        val restoredCourseCount: Int,
        val restoredHighSchoolCount: Int,
        val skippedCount: Int
    )

    fun write(
        context: Context,
        uri: Uri,
        universities: List<UniversityEntity>,
        programs: List<ProgramEntity>,
        courses: List<CourseEntity>,
        highSchoolGrades: List<HighSchoolGradeEntity>
    ) {
        val universitiesById = universities.associateBy { it.id }
        val programsById = programs.associateBy { it.id }

        val courseItems = JSONArray()
        courses.forEach { course ->
            val program = programsById[course.programId]
                ?: error("تعذر العثور على برنامج المقرر ${course.name}.")
            val university = universitiesById[program.universityId]
                ?: error("تعذر العثور على جامعة المقرر ${course.name}.")

            courseItems.put(JSONObject().apply {
                put("university", university.name)
                put("program", program.name)
                put("courseName", course.name)
                put("academicYear", course.academicYear)
                put("semester", course.semester)
                put("code", course.code)
                put("language", course.language)
                putNullable("practicalGrade", course.practicalGrade)
                putNullable("theoryGrade", course.theoryGrade)
                putNullable("assignmentGrade", course.assignmentGrade)
                putNullable("examGrade", course.examGrade)
                putNullable("studentWorkGrade", course.studentWorkGrade)
                putNullable("practicalExamGrade", course.practicalExamGrade)
                put("notes", course.notes)
                putNullable("creditHoursAtExport", course.creditHours)
                put("passedWithoutGradeAtExport", course.passedWithoutGrade)
            })
        }

        val highSchoolItems = JSONArray()
        highSchoolGrades.forEach { item ->
            highSchoolItems.put(JSONObject().apply {
                put("branch", item.branch)
                put("subject", item.subject)
                putNullable("grade", item.grade)
                put("maxGradeAtExport", item.maxGrade)
            })
        }

        val root = JSONObject().apply {
            put("formatId", FORMAT_ID)
            put("schemaVersion", SCHEMA_VERSION)
            put("applicationId", BuildConfig.APPLICATION_ID)
            put("applicationName", "مسيرتي الأكاديمية")
            put("applicationVersion", BuildConfig.VERSION_NAME)
            put("createdAtUtc", utcTimestamp())
            put("courses", courseItems)
            put("highSchoolGrades", highSchoolItems)
        }

        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.writer(Charsets.UTF_8).buffered().use { it.write(root.toString(2)) }
        } ?: error("تعذر فتح الملف المحدد للكتابة.")
    }

    fun readAndPlan(
        context: Context,
        uri: Uri,
        universities: List<UniversityEntity>,
        programs: List<ProgramEntity>,
        currentCourses: List<CourseEntity>,
        currentHighSchoolGrades: List<HighSchoolGradeEntity>
    ): RestorePlan {
        val json = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.reader(Charsets.UTF_8).buffered().use { it.readText() }
        } ?: error("تعذر فتح ملف النسخة الاحتياطية.")
        require(json.length <= MAX_BACKUP_CHARACTERS) { "ملف النسخة الاحتياطية أكبر من الحد المسموح." }

        val root = runCatching { JSONObject(json) }
            .getOrElse { error("الملف المحدد ليس نسخة احتياطية صالحة للتطبيق.") }
        require(root.optString("formatId") == FORMAT_ID) {
            "هوية ملف النسخة الاحتياطية لا تطابق تطبيق مسيرتي الأكاديمية."
        }
        val schemaVersion = root.optInt("schemaVersion", -1)
        require(schemaVersion in 1..SCHEMA_VERSION) {
            "إصدار ملف النسخة الاحتياطية غير مدعوم ($schemaVersion)."
        }

        val universitiesById = universities.associateBy { it.id }
        val programsById = programs.associateBy { it.id }
        val courseContexts = currentCourses.mapNotNull { course ->
            val program = programsById[course.programId] ?: return@mapNotNull null
            val university = universitiesById[program.universityId] ?: return@mapNotNull null
            CourseContext(course, university.name, program.name)
        }
        val restoredCourses = linkedMapOf<Long, CourseEntity>()
        var skipped = 0

        val courseItems = root.optJSONArray("courses") ?: JSONArray()
        for (index in 0 until courseItems.length()) {
            val item = courseItems.optJSONObject(index)
            if (item == null) {
                skipped++
                continue
            }
            val university = item.optString("university")
            val program = item.optString("program")
            val courseName = item.optString("courseName")
            val year = item.optInt("academicYear", -1)
            val semester = item.optInt("semester", -1)
            val exportedCode = item.optString("code")

            val exact = courseContexts.firstOrNull {
                it.university == university && it.program == program &&
                    it.course.name == courseName && it.course.academicYear == year &&
                    it.course.semester == semester
            }
            val byStableCode = if (exact == null && exportedCode.isNotBlank()) {
                courseContexts.firstOrNull {
                    it.university == university && it.program == program &&
                        it.course.code == exportedCode
                }
            } else null
            val current = (exact ?: byStableCode)?.course
            if (current == null) {
                skipped++
                continue
            }

            val code = item.optString("code", current.code).also {
                require(it.length <= 120) { "رقم/رمز مقرر أطول من الحد المسموح." }
            }
            val notes = item.optString("notes", current.notes).also {
                require(it.length <= 50_000) { "ملاحظة مقرر أطول من الحد المسموح." }
            }
            restoredCourses[current.id] = current.copy(
                code = code,
                practicalGrade = item.nullableGrade("practicalGrade"),
                theoryGrade = item.nullableGrade("theoryGrade"),
                assignmentGrade = item.nullableGrade("assignmentGrade"),
                examGrade = item.nullableGrade("examGrade"),
                studentWorkGrade = item.nullableGrade("studentWorkGrade"),
                practicalExamGrade = item.nullableGrade("practicalExamGrade"),
                notes = notes
            )
        }

        val highSchoolByIdentity = currentHighSchoolGrades.associateBy { it.branch to it.subject }
        val restoredHighSchool = linkedMapOf<Long, HighSchoolGradeEntity>()
        val highSchoolItems = root.optJSONArray("highSchoolGrades") ?: JSONArray()
        for (index in 0 until highSchoolItems.length()) {
            val item = highSchoolItems.optJSONObject(index)
            if (item == null) {
                skipped++
                continue
            }
            val current = highSchoolByIdentity[item.optString("branch") to item.optString("subject")]
            if (current == null) {
                skipped++
                continue
            }
            val grade = item.nullableInt("grade")
            require(grade == null || grade in 0..current.maxGrade) {
                "درجة ${current.subject} خارج المجال 0–${current.maxGrade}."
            }
            restoredHighSchool[current.id] = current.copy(grade = grade)
        }

        return RestorePlan(
            courses = restoredCourses.values.toList(),
            highSchoolGrades = restoredHighSchool.values.toList(),
            restoredCourseCount = restoredCourses.size,
            restoredHighSchoolCount = restoredHighSchool.size,
            skippedCount = skipped
        )
    }

    private data class CourseContext(
        val course: CourseEntity,
        val university: String,
        val program: String
    )

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.nullableGrade(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val value = getDouble(key)
        require(value.isFinite() && value in 0.0..100.0) { "قيمة غير صالحة في الحقل $key." }
        return value
    }

    private fun JSONObject.nullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return getInt(key)
    }

    private fun utcTimestamp(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())
}
