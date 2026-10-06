package com.academicjourney.app.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.academicjourney.app.BuildConfig
import com.academicjourney.app.data.*
import com.academicjourney.app.domain.CourseSearch
import com.academicjourney.app.domain.GradeExplanation
import com.academicjourney.app.platform.JourneyReminders
import com.academicjourney.app.platform.JourneyWidget
import org.json.JSONObject
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.UUID

fun journeyDate(millis: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

@Composable
fun JourneyMoreScreen(vm: AcademicViewModel, programs: List<ProgramEntity>, universities: List<UniversityEntity>,
    pinned: Long, lastBackup: Long, backupMessage: String?, onExport: () -> Unit, onImport: () -> Unit,
    onDates: () -> Unit, onHistory: () -> Unit, onArchives: () -> Unit, onHighSchool: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    JourneyPage("المزيد", selected = "more") { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { PearlCard {
                JourneyHeading("برنامجي المثبّت", "يظهر في الرئيسية وودجت الشاشة")
                Box {
                    OutlinedButton(onClick = { menu = true }, Modifier.fillMaxWidth()) {
                        Text(programs.firstOrNull { it.id == pinned }?.name ?: "اختر برنامجك", Modifier.weight(1f))
                        Icon(Icons.Rounded.ExpandMore, null)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                        programs.forEach { p -> DropdownMenuItem(text = {
                            Column { Text(p.name); Text(universities.firstOrNull { it.id == p.universityId }?.name.orEmpty(), style = MaterialTheme.typography.bodySmall) }
                        }, onClick = { vm.pinProgram(p.id); menu = false }) }
                    }
                }
                Text("الودجت يعرض تقدّم البرنامج وأقرب امتحان من مواعيدك.", style = MaterialTheme.typography.bodySmall)
                if (Build.VERSION.SDK_INT >= 26 && AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported) {
                    TextButton(onClick = { AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, JourneyWidget::class.java), null, null) }) {
                        Text("إضافة الودجت للشاشة الرئيسية")
                    }
                } else Text("لإضافته: اضغط مطولًا على شاشة الهاتف، ثم اختر الأدوات المصغّرة ← مسيرتي الأكاديمية.", style = MaterialTheme.typography.bodySmall)
            } }
            item { PearlCard {
                JourneyHeading("النسخ الاحتياطي والاستعادة", if (lastBackup > 0) "آخر تصدير ناجح: ${journeyDate(lastBackup)}" else "لم تُصدّر نسخة احتياطية من هذا الإصدار بعد.")
                Text("تشمل النسخة العلامات والملاحظات والمواد الحالية والمواعيد وسجل التعديلات والأرشيف.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onExport, Modifier.fillMaxWidth()) { Text("تصدير نسخة احتياطية") }
                OutlinedButton(onClick = onImport, Modifier.fillMaxWidth()) { Text("معاينة نسخة واستعادتها") }
                if (backupMessage != null) Text(backupMessage, style = MaterialTheme.typography.bodySmall)
            } }
            item { PearlCard(onClick = onDates) { JourneyHeading("مواعيد الامتحانات والوظائف", "عرض المواعيد والتنبيهات وتعديلها") } }
            item { PearlCard(onClick = onHistory) { JourneyHeading("سجل تعديل الدرجات", "قبل التعديل وبعده، مع إمكانية التراجع") } }
            item { PearlCard(onClick = onArchives) { JourneyHeading("أرشيف الفصول", "لقطات ثابتة لنتائجك السابقة") } }
            item { PearlCard(onClick = onHighSchool) { JourneyHeading("علامات الثانوية") } }
            item { Text("مسيرتي الأكاديمية • ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun EventsScreen(events: List<AcademicEventEntity>, courses: List<CourseEntity>, programs: List<ProgramEntity>,
    vm: AcademicViewModel, filterCourse: Long?, onBack: () -> Unit) {
    var editor by remember { mutableStateOf<AcademicEventEntity?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var past by rememberSaveable { mutableStateOf(false) }
    var deletion by remember { mutableStateOf<AcademicEventEntity?>(null) }
    val selected = events.filter { (filterCourse == null || it.courseId == filterCourse) && (past || it.startsAt >= System.currentTimeMillis()) }
    JourneyPage(if (filterCourse == null) "مواعيدي" else courses.firstOrNull { it.id == filterCourse }?.name ?: "مواعيد المادة", onBack) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Button(onClick = { adding = true }, enabled = courses.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("إضافة موعد امتحان أو تسليم")
            } }
            item { FilterChip(past, { past = !past }, label = { Text("إظهار المواعيد السابقة") }) }
            if (selected.isEmpty()) item { PearlCard { Text("لا توجد مواعيد ${if (past) "مسجّلة" else "قادمة"}.") } }
            items(selected, key = { it.id }) { e ->
                PearlCard {
                    JourneyHeading(e.title, courses.firstOrNull { it.id == e.courseId }?.name)
                    Text("${if (e.kind == "EXAM") "امتحان" else "تسليم وظيفة"} • ${journeyDate(e.startsAt)}", color = MaterialTheme.colorScheme.primary)
                    if (e.place.isNotBlank()) Text(e.place, style = MaterialTheme.typography.bodySmall)
                    Text(reminderLabel(e.reminderMinutes), style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { editor = e }) { Text("تعديل") }
                        TextButton(onClick = { deletion = e }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    if (adding || editor != null) EventEditor(editor, filterCourse, courses, programs, vm) { adding = false; editor = null }
    deletion?.let { event ->
        AlertDialog(onDismissRequest = { deletion = null }, title = { Text("حذف الموعد؟") },
            text = { Text(event.title + "\n" + journeyDate(event.startsAt)) },
            confirmButton = { TextButton(onClick = { vm.deleteEvent(event.id); deletion = null }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { deletion = null }) { Text("إلغاء") } })
    }
}

private fun reminderLabel(minutes: Int?) = when (minutes) {
    null -> "دون تنبيه"
    0 -> "تنبيه عند الموعد"
    15 -> "تنبيه قبل ربع ساعة"
    30 -> "تنبيه قبل نصف ساعة"
    60 -> "تنبيه قبل ساعة"
    1440 -> "تنبيه قبل يوم"
    2880 -> "تنبيه قبل يومين"
    else -> "تنبيه قبل أسبوع"
}

@Composable
private fun EventEditor(event: AcademicEventEntity?, initialCourse: Long?, courses: List<CourseEntity>,
    programs: List<ProgramEntity>, vm: AcademicViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var courseId by rememberSaveable { mutableStateOf(event?.courseId ?: initialCourse ?: 0L) }
    var kind by rememberSaveable { mutableStateOf(event?.kind ?: "EXAM") }
    var title by rememberSaveable { mutableStateOf(event?.title ?: "امتحان") }
    var place by rememberSaveable { mutableStateOf(event?.place.orEmpty()) }
    var startsAt by rememberSaveable { mutableStateOf(event?.startsAt ?: Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis) }
    var minutes by rememberSaveable { mutableStateOf(event?.reminderMinutes) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var reminderMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<AcademicEventEntity?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.let { e ->
            vm.saveEvent(if (granted) e else e.copy(reminderMinutes = null))
            if (!granted) Toast.makeText(context, "حُفظ الموعد دون تنبيه لأن إذن الإشعارات غير متاح.", Toast.LENGTH_LONG).show()
        }
        pending = null
        onDismiss()
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (event == null) "موعد جديد" else "تعديل الموعد") },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.heightIn(max = 460.dp)) {
            item { OutlinedButton(onClick = { choosing = !choosing }, Modifier.fillMaxWidth()) {
                Text(courses.firstOrNull { it.id == courseId }?.name ?: "اختر المادة")
            } }
            if (choosing) {
                item { OutlinedTextField(query, { query = it }, label = { Text("بحث عن المادة") }, singleLine = true) }
                items(courses.filter { CourseSearch.matches(it, query) }, key = { it.id }) { c ->
                    TextButton(onClick = { courseId = c.id; choosing = false }) {
                        Column { Text(c.name); Text("${c.code} • ${programs.firstOrNull { it.id == c.programId }?.name.orEmpty()}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            } else {
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(kind == "EXAM", { kind = "EXAM"; if (title == "تسليم وظيفة") title = "امتحان" }, label = { Text("امتحان") })
                    FilterChip(kind == "ASSIGNMENT", { kind = "ASSIGNMENT"; if (title == "امتحان") title = "تسليم وظيفة" }, label = { Text("وظيفة") })
                } }
                item { OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("عنوان الموعد") }, singleLine = true) }
                item { Text(journeyDate(startsAt), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                item { Row {
                    TextButton(onClick = {
                        val c = Calendar.getInstance().apply { timeInMillis = startsAt }
                        DatePickerDialog(context, { _, y, m, d ->
                            startsAt = Calendar.getInstance().apply { timeInMillis = startsAt; set(Calendar.YEAR, y); set(Calendar.MONTH, m); set(Calendar.DAY_OF_MONTH, d) }.timeInMillis
                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                    }) { Text("اختيار التاريخ") }
                    TextButton(onClick = {
                        val c = Calendar.getInstance().apply { timeInMillis = startsAt }
                        TimePickerDialog(context, { _, h, m ->
                            startsAt = Calendar.getInstance().apply { timeInMillis = startsAt; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
                    }) { Text("اختيار الساعة") }
                } }
                item { OutlinedTextField(place, { place = it }, Modifier.fillMaxWidth(), label = { Text("المكان أو ملاحظة (اختياري)") }) }
                item { Box {
                    OutlinedButton(onClick = { reminderMenu = true }, Modifier.fillMaxWidth()) { Text(reminderLabel(minutes)) }
                    DropdownMenu(reminderMenu, { reminderMenu = false }) {
                        listOf(null, 0, 15, 30, 60, 1440, 2880, 10080).forEach { value ->
                            DropdownMenuItem(text = { Text(reminderLabel(value)) }, onClick = { minutes = value; reminderMenu = false })
                        }
                    }
                } }
                item { Text("الموعد بتوقيت جهازك. التنبيه اختياري وقد يؤخره نظام الهاتف قليلًا لتوفير البطارية.", style = MaterialTheme.typography.bodySmall) }
                if (error != null) item { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
            }
        } },
        confirmButton = { TextButton(onClick = {
            when {
                courses.none { it.id == courseId } -> error = "اختر المادة أولًا."
                title.isBlank() -> error = "أدخل عنوان الموعد."
                title.length > 2000 || place.length > 2000 -> error = "النص أطول من الحد المسموح."
                startsAt <= System.currentTimeMillis() -> error = "اختر موعدًا في المستقبل."
                else -> {
                    val value = AcademicEventEntity(event?.id ?: UUID.randomUUID().toString(), courseId, kind, title.trim(), place.trim(), startsAt, minutes)
                    if (minutes != null && !JourneyReminders.allowed(context)) {
                        if (Build.VERSION.SDK_INT >= 33) { pending = value; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        else { vm.saveEvent(value.copy(reminderMinutes = null)); Toast.makeText(context, "الإشعارات معطلة في إعدادات الهاتف؛ حُفظ الموعد دون تنبيه.", Toast.LENGTH_LONG).show(); onDismiss() }
                    } else { vm.saveEvent(value); onDismiss() }
                }
            }
        }, enabled = pending == null) { Text("حفظ الموعد") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

@Composable
fun HistoryScreen(history: List<GradeChangeEntity>, courses: List<CourseEntity>, school: List<HighSchoolGradeEntity>,
    vm: AcademicViewModel, onBack: () -> Unit) {
    var undo by remember { mutableStateOf<GradeChangeEntity?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    JourneyPage("سجل تعديل الدرجات", onBack) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("يسجل التطبيق التعديلات بدءًا من هذا الإصدار. التراجع متاح عندما تطابق البيانات الحالية نتيجة التعديل.") }
            item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("بحث في السجل") }) }
            if (history.isEmpty()) item { PearlCard { Text("سيظهر أول تعديل تحفظه هنا.") } }
            items(history.filter { query.isBlank() || it.title.contains(query, true) }, key = { it.id }) { h ->
                val current = if (h.targetKind == "COURSE") courses.firstOrNull { it.id == h.targetId }?.let { GradeSnapshot.encode(it) }
                    else school.firstOrNull { it.id == h.targetId }?.let { GradeSnapshot.highSchool(it.grade) }
                val canUndo = !h.undone && current != null && GradeSnapshot.same(current, h.afterJson)
                PearlCard {
                    JourneyHeading(h.title, journeyDate(h.createdAt))
                    GradeSnapshot.differences(h.beforeJson, h.afterJson).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    TextButton(onClick = { undo = h }, enabled = canUndo) { Text(if (h.undone) "تم التراجع" else "التراجع عن هذا التعديل") }
                    if (!h.undone && !canUndo) Text("توجد بيانات أحدث؛ ابدأ بالتعديل الأحدث.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    undo?.let { h -> AlertDialog(onDismissRequest = { undo = null }, title = { Text("التراجع عن التعديل؟") },
        text = { Text(h.title + "\nسيعيد القيم السابقة ويسجل عملية التراجع.") },
        confirmButton = { TextButton(onClick = { vm.undo(h.id); undo = null }) { Text("تراجع") } },
        dismissButton = { TextButton(onClick = { undo = null }) { Text("إلغاء") } }) }
}

@Composable
fun ArchiveScreen(archives: List<SemesterArchiveEntity>, onBack: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    JourneyPage("أرشيف الفصول", onBack) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("لقطات ثابتة كما كانت عند الحفظ. أنشئ لقطة من داخل الفصل الدراسي.") }
            if (archives.isEmpty()) item { PearlCard { Text("لا توجد فصول مؤرشفة بعد.") } }
            items(archives, key = { it.id }) { a ->
                val data = remember(a.snapshotJson) { JSONObject(a.snapshotJson) }
                PearlCard {
                    TextButton(onClick = { expanded = if (a.id in expanded) expanded - a.id else expanded + a.id }) {
                        Text(a.label, Modifier.weight(1f)); Icon(if (a.id in expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }
                    Text(journeyDate(a.createdAt), style = MaterialTheme.typography.bodySmall)
                    Text("المعدل ${if (data.isNull("average")) "—" else GradeExplanation.number(data.getDouble("average"))} • ${data.getInt("passed")} من ${data.getInt("total")} ناجح")
                    if (a.id in expanded) {
                        val rows = data.getJSONArray("courses")
                        for (i in 0 until rows.length()) {
                            val c = rows.getJSONObject(i)
                            HorizontalDivider()
                            Text(c.getString("name") + " • " + c.getString("code"), fontWeight = FontWeight.Bold)
                            Text(when {
                                c.getBoolean("transferred") -> "ناجح دون علامة"
                                c.isNull("grade") -> "الدرجة غير مكتملة"
                                else -> "${GradeExplanation.number(c.getDouble("grade"))} • ${if (c.optBoolean("passed")) "ناجح" else "راسب"} • مساعدة ${c.optInt("assistance")}"
                            })
                            val values = c.getJSONObject("values")
                            GradeSnapshot.fields.forEach { (key, label) -> if (values.has(key) && !values.isNull(key) && values.get(key).toString().isNotBlank()) {
                                Text("$label: ${values.get(key)}", style = MaterialTheme.typography.bodySmall)
                            } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RestorePreviewDialog(plan: AcademicBackupManager.RestorePlan, busy: Boolean, onApply: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onCancel() }, title = { Text("معاينة الاستعادة") },
        text = { LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("تاريخ النسخة (UTC): ${plan.createdAt}") }
            item { Text("${plan.restoredCourseCount} مقررًا مطابقًا • ${plan.restoredHighSchoolCount} مادة ثانوية • ${plan.skippedCount} سجلًا غير مطابق") }
            item { Text("${plan.history.size} تعديلًا جديدًا في السجل • ${plan.archives.size} لقطة جديدة في الأرشيف • ${plan.events.size} موعدًا في الملف") }
            item { Text("التغييرات: ${plan.differences.size}", fontWeight = FontWeight.Bold) }
            if (plan.differences.isEmpty()) item { Text("لا تغييرات على العلامات والمواد الحالية والمواعيد.") }
            items(plan.differences) { Text(it, style = MaterialTheme.typography.bodySmall) }
            item { Text("الحقول الفارغة في النسخة تمسح قيمتها الحالية عند التطبيق. سجلات الأرشيف والتعديلات القائمة تبقى محفوظة، وتُسجّل تغييرات العلامات لتتمكن من التراجع عنها.", style = MaterialTheme.typography.bodySmall) }
        } },
        confirmButton = { TextButton(onClick = onApply, enabled = !busy) { Text(if (busy) "جارٍ الاستعادة…" else "تطبيق الاستعادة") } },
        dismissButton = { TextButton(onClick = onCancel, enabled = !busy) { Text("إلغاء") } })
}
