package com.academicjourney.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.academicjourney.app.data.*
import com.academicjourney.app.domain.*

val LocalJourneyNavigation = staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
fun JourneyNavigation(selected: String, onNavigate: (String) -> Unit = LocalJourneyNavigation.current) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        listOf(Triple("home", "الرئيسية", Icons.Rounded.Home),
            Triple("current", "فصلي الحالي", Icons.Rounded.MenuBook),
            Triple("stats", "الإحصائيات", Icons.Rounded.Insights),
            Triple("more", "المزيد", Icons.Rounded.MoreHoriz)).forEach { (key, label, icon) ->
            NavigationBarItem(selected == key, { onNavigate(key) },
                icon = { Icon(icon, null) }, label = { Text(label, maxLines = 1) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyPage(title: String, onBack: (() -> Unit)? = null, selected: String? = null,
    content: @Composable (PaddingValues) -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text(title, maxLines = 2, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
        bottomBar = { if (selected != null) JourneyNavigation(selected) }, content = content)
}

@Composable
fun PearlCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun JourneyHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun JourneyDashboard(universities: List<UniversityEntity>, programs: List<ProgramEntity>, courses: List<CourseEntity>,
    events: List<AcademicEventEntity>, pinned: Long, lastCourse: Long, onUniversity: (Long) -> Unit,
    onProgram: (Long) -> Unit, onCourse: (Long) -> Unit, onDates: () -> Unit, onHighSchool: () -> Unit) {
    val program = programs.firstOrNull { it.id == pinned }
        ?: courses.firstOrNull { it.id == lastCourse }?.let { c -> programs.firstOrNull { it.id == c.programId } }
        ?: programs.firstOrNull { p -> courses.any { it.programId == p.id && it.isCurrentSemester } }
    val upcoming = events.filter { it.startsAt > System.currentTimeMillis() }.take(3)
    val partial = courses.filter { c -> programs.firstOrNull { it.id == c.programId }?.let { PartialGradePreviewBuilder.forCourse(c, it) != null } == true }
    JourneyPage("مسيرتي الأكاديمية", selected = "home") { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { JourneyHeading("خطوة جديدة في مسيرتك", "درجاتك ومواعيدك وتقدّمك، في مكان واحد") }
            if (program != null) item {
                val pc = courses.filter { it.programId == program.id }
                val standing = StudentStandingCalculator.calculate(universities.firstOrNull { it.id == program.universityId }?.name.orEmpty(), program, pc)
                Surface(Modifier.fillMaxWidth().clickable { onProgram(program.id) },
                    color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(28.dp)) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("متابعة مسيرتي", color = Color(0xFFE2C991), style = MaterialTheme.typography.labelLarge)
                        Text(program.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(standing.title + " • ${standing.passedCourses} من ${pc.size} مقررًا")
                        LinearProgressIndicator(progress = { standing.passedCourses.toFloat() / maxOf(pc.size, 1) },
                            modifier = Modifier.fillMaxWidth().height(7.dp), color = Color(0xFFE2C991), trackColor = Color(0xFF487F84))
                        Text("المعدل  ${GradeExplanation.number(GradeCalculator.average(pc, program))}", style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1, softWrap = false)
                    }
                }
            } else item { PearlCard { Text("ابدأ باختيار جامعتك وبرنامجك. يمكنك تثبيت برنامجك من «المزيد» ليظهر هنا وفي ودجت الهاتف.") } }
            courses.firstOrNull { it.id == lastCourse }?.let { last ->
                item { PearlCard(onClick = { onCourse(last.id) }) { JourneyHeading("أكمل من حيث توقفت", last.name) } }
            }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("المواعيد القادمة", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDates) { Text("إدارة المواعيد") }
            } }
            if (upcoming.isEmpty()) item { PearlCard(onClick = onDates) { Text("أضف موعد امتحان أو تسليم وظيفة، واختر التنبيه المناسب لك.") } }
            items(upcoming, key = { "event:${it.id}" }) { e ->
                PearlCard(onClick = { onCourse(e.courseId) }) {
                    JourneyHeading(e.title, courses.firstOrNull { it.id == e.courseId }?.name)
                    Text(journeyDate(e.startsAt), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            item { JourneyHeading("جامعاتي", "اختر الجامعة لعرض برامجها") }
            items(universities.chunked(2)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { u -> PearlCard(Modifier.weight(1f), onClick = { onUniversity(u.id) }) {
                        Icon(Icons.Rounded.School, null, tint = MaterialTheme.colorScheme.primary)
                        Text(u.name, fontWeight = FontWeight.Bold, minLines = 2)
                        Text("${programs.count { it.universityId == u.id }} برامج", style = MaterialTheme.typography.bodySmall)
                    } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (partial.isNotEmpty()) {
                item { JourneyHeading("درجات بانتظار الإكمال", "${partial.size} مقررًا يحتوي درجات جزئية محفوظة") }
                items(partial.take(3), key = { "partial:${it.id}" }) { c ->
                    PearlCard(onClick = { onCourse(c.id) }) { Text(c.name); Text("متابعة إدخال الدرجات", color = MaterialTheme.colorScheme.primary) }
                }
            }
            item { OutlinedButton(onClick = onHighSchool, Modifier.fillMaxWidth()) { Text("علامات الثانوية") } }
        }
    }
}

@Composable
fun CourseSummary(c: CourseEntity, p: ProgramEntity) {
    val r = GradeCalculator.calculate(c, p)
    val partial = PartialGradePreviewBuilder.forCourse(c, p)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(c.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(listOfNotNull(c.code.takeIf { it.isNotBlank() }, c.creditHours?.let { "$it ساعات" }).joinToString(" • "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(when {
            r.passedWithoutGrade -> "ناجح دون علامة"
            r.finalGrade != null -> "${GradeExplanation.number(r.finalGrade)} / 100 • ${if (r.isPassed == true) "ناجح" else "راسب"}" +
                if (r.assistancePoints > 0) " • مساعدة +${r.assistancePoints}" else ""
            partial != null -> partial.entered.joinToString(" • ") { "${it.label}: ${GradeExplanation.number(it.value)}" }
            else -> "لم تُدخل الدرجات"
        }, color = if (r.isPassed == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        if (partial != null) Text(partial.missingNotice, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun CourseAccordion(c: CourseEntity, p: ProgramEntity, expanded: Boolean, onToggle: () -> Unit,
    onSave: (CourseEntity) -> Unit, vm: AcademicViewModel, onSchedule: (Long) -> Unit) {
    val holder = rememberSaveableStateHolder()
    PearlCard {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { CourseSummary(c, p) }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded) "طي التفاصيل" else "عرض التفاصيل")
        }
        if (expanded) holder.SaveableStateProvider(c.id) {
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(c.isCurrentSemester, onCheckedChange = { vm.setCurrent(c.id, it) })
                Text("ضمن فصلي الحالي", Modifier.weight(1f))
            }
            GradeEntryCard(c, p, onSave)
            var explain by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { explain = !explain }) { Text("كيف حُسبت نتيجتي؟") }
            if (explain) Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradeExplanation.course(c, p).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            var note by rememberSaveable(c.id, c.notes) { mutableStateOf(c.notes) }
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظاتي") }, minLines = 2)
            TextButton(onClick = { vm.saveNotes(c.id, note) }, enabled = note != c.notes) { Text("حفظ الملاحظة") }
            OutlinedButton(onClick = { onSchedule(c.id) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Event, null); Spacer(Modifier.width(8.dp)); Text("مواعيد الامتحانات والوظائف")
            }
        }
    }
}

@Composable
fun ExpandableYearScreen(program: ProgramEntity?, year: Int, courses: List<CourseEntity>, vm: AcademicViewModel,
    onBack: () -> Unit, onSave: (CourseEntity) -> Unit, onSchedule: (Long) -> Unit) {
    if (program == null) return
    var query by rememberSaveable { mutableStateOf("") }
    var semesters by rememberSaveable { mutableStateOf(listOf(1)) }
    var expanded by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var archiveSemester by rememberSaveable { mutableStateOf<Int?>(null) }
    var archiveLabel by rememberSaveable { mutableStateOf("") }
    JourneyPage("السنة $year • ${program.name}", onBack) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("بحث بالاسم أو الرقم أو الرمز") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true) }
            item { Row {
                TextButton(onClick = { semesters = listOf(1, 2); expanded = courses.map { it.id } }) { Text("فتح الكل") }
                TextButton(onClick = { semesters = emptyList(); expanded = emptyList() }) { Text("طي الكل") }
            } }
            listOf(1, 2).forEach { semester ->
                val sc = courses.filter { it.semester == semester }
                val filtered = sc.filter { CourseSearch.matches(it, query) }
                if (query.isBlank() || filtered.isNotEmpty()) {
                    item(key = "semester:$semester") {
                        PearlCard(onClick = { semesters = if (semester in semesters) semesters - semester else semesters + semester }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(if (semester == 1) "الفصل الأول • ف1" else "الفصل الثاني • ف2", fontWeight = FontWeight.Bold)
                                    Text("${sc.size} مقررات • المعدل ${GradeExplanation.number(GradeCalculator.average(sc, program))}",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                Icon(if (semester in semesters) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                            }
                        }
                    }
                    if (semester in semesters || query.isNotBlank()) {
                        item(key = "average:$semester") {
                            Text(GradeExplanation.average(sc, program), style = MaterialTheme.typography.bodySmall)
                        }
                        items(filtered, key = { it.id }) { c ->
                            CourseAccordion(c, program, c.id in expanded,
                                { expanded = if (c.id in expanded) expanded - c.id else expanded + c.id; vm.rememberCourse(c.id) },
                                onSave, vm, onSchedule)
                        }
                        item(key = "archive:$semester") {
                            OutlinedButton(onClick = { archiveSemester = semester }, enabled = sc.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.Inventory2, null); Spacer(Modifier.width(8.dp)); Text("حفظ لقطة هذا الفصل في الأرشيف")
                            }
                        }
                    }
                }
            }
            if (query.isNotBlank() && courses.none { CourseSearch.matches(it, query) }) item { Text("لا توجد مادة مطابقة.") }
        }
    }
    archiveSemester?.let { semester ->
        AlertDialog(onDismissRequest = { archiveSemester = null },
            title = { Text("أرشفة الفصل $semester") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("تُحفظ لقطة ثابتة للدرجات الحالية وتاريخها. يمكنك متابعة تعديل درجاتك بعد الأرشفة.")
                OutlinedTextField(archiveLabel, { archiveLabel = it }, label = { Text("اسم اللقطة (اختياري)") })
            } },
            confirmButton = { TextButton(onClick = { vm.archive(program.id, year, semester, archiveLabel); archiveSemester = null; archiveLabel = "" }) { Text("حفظ اللقطة") } },
            dismissButton = { TextButton(onClick = { archiveSemester = null }) { Text("إلغاء") } })
    }
}

@Composable
fun CurrentSemesterScreen(programs: List<ProgramEntity>, universities: List<UniversityEntity>, courses: List<CourseEntity>,
    vm: AcademicViewModel, onSave: (CourseEntity) -> Unit, onSchedule: (Long) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    val selected = courses.filter { it.isCurrentSemester }
    val shown = (if (choosing) courses else selected).filter { CourseSearch.matches(it, query) }
    JourneyPage("فصلي الحالي", selected = "current") { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { JourneyHeading("${selected.size} مقررًا في خطتك", "اجمع موادك من أي جامعة أو سنة؛ تبقى كل مادة في موضعها الأصلي.") }
            item { FilledTonalButton(onClick = { choosing = !choosing }, Modifier.fillMaxWidth()) { Text(if (choosing) "تم اختيار المواد" else "اختيار مواد الفصل") } }
            item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("ابحث بالاسم أو الرمز") }, singleLine = true) }
            if (shown.isEmpty()) item { PearlCard { Text(if (choosing) "لا توجد مادة مطابقة." else "اختر موادك أولًا لتظهر درجاتها ومواعيدها هنا.") } }
            programs.forEach { p ->
                val group = shown.filter { it.programId == p.id }
                if (group.isNotEmpty()) {
                    item(key = "program:${p.id}") { JourneyHeading(p.name, universities.firstOrNull { it.id == p.universityId }?.name) }
                    items(group, key = { it.id }) { c ->
                        if (choosing) PearlCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(c.isCurrentSemester, { vm.setCurrent(c.id, it) })
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, fontWeight = FontWeight.Bold)
                                    Text("${c.code} • السنة ${c.academicYear} • ف${c.semester}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else CourseAccordion(c, p, c.id in expanded,
                            { expanded = if (c.id in expanded) expanded - c.id else expanded + c.id; vm.rememberCourse(c.id) },
                            onSave, vm, onSchedule)
                    }
                }
            }
        }
    }
}
