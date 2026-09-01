package com.academicjourney.app.ui

import kotlin.math.roundToInt
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.academicjourney.app.R
import com.academicjourney.app.data.CourseEntity
import com.academicjourney.app.data.ProgramEntity
import com.academicjourney.app.data.UniversityEntity
import com.academicjourney.app.domain.GradeCalculator
import java.util.Locale

private sealed interface Screen {
    data object Universities : Screen
    data object Statistics : Screen
    data class University(val id: Long) : Screen
    data class Program(val id: Long) : Screen
    data class Semester(val programId: Long, val year: Int, val semester: Int) : Screen
    data class Course(val id: Long) : Screen
}

@Composable
fun AcademicApp(vm: AcademicViewModel) {
    val universities by vm.universities.collectAsState()
    val programs by vm.programs.collectAsState()
    val courses by vm.courses.collectAsState()
    var screen by remember { mutableStateOf<Screen>(Screen.Universities) }

    LaunchedEffect(Unit) { vm.ensureSeeded() }

    BackHandler(enabled = screen !is Screen.Universities) {
        screen = when (val s = screen) {
            is Screen.University -> Screen.Universities
            is Screen.Program -> programs.firstOrNull { it.id == s.id }?.let { Screen.University(it.universityId) } ?: Screen.Universities
            is Screen.Semester -> Screen.Program(s.programId)
            is Screen.Course -> {
                val c = courses.firstOrNull { it.id == s.id }
                if (c != null) Screen.Semester(c.programId, c.academicYear, c.semester) else Screen.Universities
            }
            Screen.Statistics -> Screen.Universities
            Screen.Universities -> Screen.Universities
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AcademicJourneyTheme {
            when (val s = screen) {
                Screen.Universities -> UniversitiesScreen(
                    universities = universities,
                    programs = programs,
                    courses = courses,
                    onUniversity = { screen = Screen.University(it) },
                    onStatistics = { screen = Screen.Statistics }
                )
                Screen.Statistics -> StatisticsScreen(
                    universities = universities,
                    programs = programs,
                    courses = courses,
                    onHome = { screen = Screen.Universities },
                    onProgram = { screen = Screen.Program(it) }
                )
                is Screen.University -> UniversityScreen(
                    university = universities.firstOrNull { it.id == s.id },
                    programs = programs.filter { it.universityId == s.id },
                    courses = courses,
                    onBack = { screen = Screen.Universities },
                    onProgram = { screen = Screen.Program(it) }
                )
                is Screen.Program -> ProgramScreen(
                    program = programs.firstOrNull { it.id == s.id },
                    courses = courses.filter { it.programId == s.id },
                    onBack = {
                        val p = programs.firstOrNull { it.id == s.id }
                        screen = p?.let { Screen.University(it.universityId) } ?: Screen.Universities
                    },
                    onSemester = { year, semester -> screen = Screen.Semester(s.id, year, semester) },
                    onCourse = { screen = Screen.Course(it) }
                )
                is Screen.Semester -> SemesterScreen(
                    program = programs.firstOrNull { it.id == s.programId },
                    year = s.year,
                    semester = s.semester,
                    courses = courses.filter { it.programId == s.programId && it.academicYear == s.year && it.semester == s.semester },
                    onBack = { screen = Screen.Program(s.programId) },
                    onCourse = { screen = Screen.Course(it) }
                )
                is Screen.Course -> {
                    val course = courses.firstOrNull { it.id == s.id }
                    val program = course?.let { c -> programs.firstOrNull { it.id == c.programId } }
                    CourseScreen(
                        course = course,
                        program = program,
                        onBack = {
                            if (course != null) screen = Screen.Semester(course.programId, course.academicYear, course.semester)
                            else screen = Screen.Universities
                        },
                        onSave = vm::saveCourse
                    )
                }
            }
        }
    }
}

@Composable
private fun RootNavigationBar(homeSelected: Boolean, onHome: () -> Unit, onStatistics: () -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = homeSelected,
            onClick = onHome,
            icon = { Text("⌂", style = MaterialTheme.typography.titleLarge) },
            label = { Text("الرئيسية") }
        )
        NavigationBarItem(
            selected = !homeSelected,
            onClick = onStatistics,
            icon = { Text("▥", style = MaterialTheme.typography.titleLarge) },
            label = { Text("الإحصائيات") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UniversitiesScreen(
    universities: List<UniversityEntity>,
    programs: List<ProgramEntity>,
    courses: List<CourseEntity>,
    onUniversity: (Long) -> Unit,
    onStatistics: () -> Unit
) {
    val gradedCount = courses.count { c -> programs.firstOrNull { it.id == c.programId }?.let { GradeCalculator.calculate(c, it).finalGrade != null } == true }
    val passedCount = courses.count { c -> programs.firstOrNull { it.id == c.programId }?.let { GradeCalculator.calculate(c, it).isPassed == true } == true }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("مسيرتي الأكاديمية", fontWeight = FontWeight.Bold)
                        Text("لوحة التحكم الشخصية", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        },
        bottomBar = { RootNavigationBar(true, onHome = {}, onStatistics = onStatistics) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "ملخص سريع",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryCard("البرامج", programs.size.toString(), Modifier.weight(1f))
                SummaryCard("المواد", courses.size.toString(), Modifier.weight(1f))
                SummaryCard("مُقيّمة", gradedCount.toString(), Modifier.weight(1f))
                SummaryCard("ناجحة", passedCount.toString(), Modifier.weight(1f))
            }
            Text("جامعاتي", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("اضغط على الجامعة للدخول إلى برامجها", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(universities, key = { it.id }) { university ->
                    val universityPrograms = programs.filter { it.universityId == university.id }
                    val programIds = universityPrograms.map { it.id }.toSet()
                    val universityCourses = courses.filter { it.programId in programIds }
                    val graded = universityCourses.count { c -> programs.firstOrNull { it.id == c.programId }?.let { GradeCalculator.calculate(c, it).finalGrade != null } == true }
                    val passed = universityCourses.count { c -> programs.firstOrNull { it.id == c.programId }?.let { GradeCalculator.calculate(c, it).isPassed == true } == true }
                    UniversityCard(university, universityPrograms.size, universityCourses.size, graded, passed) { onUniversity(university.id) }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun UniversityCard(university: UniversityEntity, programCount: Int, courseCount: Int, gradedCount: Int, passedCount: Int, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 232.dp)) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().height(120.dp)
            ) {
                Image(
                    painter = painterResource(universityLogo(university.name)),
                    contentDescription = "شعار ${university.name}",
                    modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(university.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("$programCount برنامج • $courseCount مقرر", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            AcademicProgressRing(passed = passedCount, total = courseCount, modifier = Modifier.size(68.dp))
            Text("$passedCount ناجحة • $gradedCount مُقيّمة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@DrawableRes
private fun universityLogo(name: String): Int = when {
    "الافتراضية" in name -> R.drawable.logo_svu
    "الأندلس" in name -> R.drawable.logo_andalus
    "دمشق" in name -> R.drawable.logo_damascus
    "اللاذقية" in name -> R.drawable.logo_latakia
    else -> R.drawable.logo_svu
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UniversityScreen(
    university: UniversityEntity?,
    programs: List<ProgramEntity>,
    courses: List<CourseEntity>,
    onBack: () -> Unit,
    onProgram: (Long) -> Unit
) {
    Scaffold(topBar = { TopAppBar(title = { Text(university?.name ?: "الجامعة", fontWeight = FontWeight.Bold) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            university?.let { u ->
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(universityLogo(u.name)),
                            contentDescription = "شعار ${u.name}",
                            modifier = Modifier.fillMaxWidth().height(160.dp).padding(16.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
            item {
                val programIds = programs.map { it.id }.toSet()
                val universityCourses = courses.filter { it.programId in programIds }
                val passed = universityCourses.count { c -> programs.firstOrNull { it.id == c.programId }?.let { GradeCalculator.calculate(c, it).isPassed == true } == true }
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("التقدم في الجامعة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        AcademicProgressBar(passed = passed, total = universityCourses.size)
                        Text("متبقي للإنجاز: ${universityCourses.size - passed} مادة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { SectionHeader("البرامج", "اختر البرنامج لعرض سنواته وفصوله ومقرراته") }
            items(programs, key = { it.id }) { p ->
                val pc = courses.filter { it.programId == p.id }
                val graded = pc.mapNotNull { GradeCalculator.calculate(it, p).finalGrade }
                val passed = pc.count { GradeCalculator.calculate(it, p).isPassed == true }
                val progress = if (pc.isEmpty()) 0f else passed.toFloat() / pc.size
                ElevatedCard(onClick = { onProgram(p.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (p.degreeType.isNotBlank()) Text(p.degreeType, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth()) {
                            Text("${pc.size} مقرر • ${graded.size} مُقيّم", modifier = Modifier.weight(1f))
                            Text(graded.takeIf { it.isNotEmpty() }?.average()?.let { "${formatGrade(it)}%" } ?: "—", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("$passed مادة ناجحة من ${pc.size}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgramScreen(
    program: ProgramEntity?,
    courses: List<CourseEntity>,
    onBack: () -> Unit,
    onSemester: (Int, Int) -> Unit,
    onCourse: (Long) -> Unit
) {
    if (program == null) return
    val years = courses.map { it.academicYear }.distinct().sorted()
    val allGrades = courses.mapNotNull { GradeCalculator.calculate(it, program).finalGrade }
    val passed = courses.count { GradeCalculator.calculate(it, program).isPassed == true }
    val failed = courses.count { GradeCalculator.calculate(it, program).isPassed == false }
    val progress = if (courses.isEmpty()) 0f else (passed.toFloat() / courses.size.toFloat()).coerceIn(0f, 1f)
    var programFilter by remember(program.id) { mutableStateOf(CourseFilter.UNGRADED) }
    val statusCourses = courses.filter { c ->
        val result = GradeCalculator.calculate(c, program)
        when (programFilter) {
            CourseFilter.ALL -> true
            CourseFilter.PASSED -> result.isPassed == true
            CourseFilter.FAILED -> result.isPassed == false
            CourseFilter.UNGRADED -> result.finalGrade == null
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(program.name, fontWeight = FontWeight.Bold) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("الملخص الأكاديمي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricBox("المعدل", allGrades.takeIf { it.isNotEmpty() }?.average()?.let(::formatGrade) ?: "—", Modifier.weight(1f))
                            MetricBox("ناجح", passed.toString(), Modifier.weight(1f))
                            MetricBox("راسب", failed.toString(), Modifier.weight(1f))
                            MetricBox("متبقي", (courses.size - passed).toString(), Modifier.weight(1f))
                        }
                        AcademicProgressBar(passed = passed, total = courses.size)
                        HorizontalDivider()
                        Text("حد النجاح: ${program.passingGrade.toInt()}/100", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (program.gradingScheme == GradeCalculator.SVU_WEIGHTED)
                                "طريقة الحساب: ${program.assignmentWeight.toInt()}% وظيفة + ${program.examWeight.toInt()}% امتحان."
                            else "طريقة الحساب: العملي + النظري، والمجموع النهائي لا يتجاوز 100.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                SectionHeader("المواد حسب الحالة", "اعرض المواد على مستوى البرنامج بالكامل دون الحاجة لفتح كل فصل")
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = programFilter == CourseFilter.UNGRADED, onClick = { programFilter = CourseFilter.UNGRADED }, label = { Text("غير مُقيّمة") })
                        FilterChip(selected = programFilter == CourseFilter.FAILED, onClick = { programFilter = CourseFilter.FAILED }, label = { Text("راسبة") })
                        FilterChip(selected = programFilter == CourseFilter.PASSED, onClick = { programFilter = CourseFilter.PASSED }, label = { Text("ناجحة") })
                    }
                    FilterChip(selected = programFilter == CourseFilter.ALL, onClick = { programFilter = CourseFilter.ALL }, label = { Text("كل المواد") })
                }
            }
            if (statusCourses.isEmpty()) {
                item { EmptyState("لا توجد مواد ضمن الحالة المحددة.") }
            } else {
                items(statusCourses, key = { "program-status-${it.id}" }) { c ->
                    val result = GradeCalculator.calculate(c, program)
                    ElevatedCard(onClick = { onCourse(c.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(c.name, fontWeight = FontWeight.Bold)
                                Text("السنة ${arabicOrdinal(c.academicYear)} • الفصل ${if (c.semester == 1) "الأول" else "الثاني"}${if (c.code.isNotBlank()) " • ${c.code}" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                GradeStatus(result.isPassed)
                                Text(result.finalGrade?.let { formatGrade(it) } ?: "—", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            years.forEach { year ->
                val yearCourses = courses.filter { it.academicYear == year }
                val yearGrades = yearCourses.mapNotNull { GradeCalculator.calculate(it, program).finalGrade }
                val yearPassed = yearCourses.count { GradeCalculator.calculate(it, program).isPassed == true }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("السنة ${arabicOrdinal(year)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "${yearCourses.size} مقرر • $yearPassed ناجح • معدل السنة: ${yearGrades.takeIf { it.isNotEmpty() }?.average()?.let(::formatGrade) ?: "—"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AcademicProgressBar(passed = yearPassed, total = yearCourses.size)
                    }
                }
                for (semester in 1..2) {
                    val sc = yearCourses.filter { it.semester == semester }
                    if (sc.isNotEmpty()) item {
                        val avg = GradeCalculator.semesterAverage(sc, program)
                        val graded = sc.count { GradeCalculator.calculate(it, program).finalGrade != null }
                        val semesterPassed = sc.count { GradeCalculator.calculate(it, program).isPassed == true }
                        ElevatedCard(onClick = { onSemester(year, semester) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("الفصل ${if (semester == 1) "الأول" else "الثاني"}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text("${sc.size} مقرر • $graded مُقيّم • $semesterPassed ناجح", style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { if (sc.isEmpty()) 0f else semesterPassed.toFloat() / sc.size.toFloat() },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(avg?.let(::formatGrade) ?: "—", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("المعدل", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class CourseFilter { ALL, PASSED, FAILED, UNGRADED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SemesterScreen(program: ProgramEntity?, year: Int, semester: Int, courses: List<CourseEntity>, onBack: () -> Unit, onCourse: (Long) -> Unit) {
    if (program == null) return
    val avg = GradeCalculator.semesterAverage(courses, program)
    val passed = courses.count { GradeCalculator.calculate(it, program).isPassed == true }
    val failed = courses.count { GradeCalculator.calculate(it, program).isPassed == false }
    var search by remember { mutableStateOf(TextFieldValue("")) }
    var filter by remember { mutableStateOf(CourseFilter.ALL) }

    val visibleCourses = courses.filter { c ->
        val result = GradeCalculator.calculate(c, program)
        val matchesSearch = search.text.isBlank() || c.name.contains(search.text, ignoreCase = true) || c.code.contains(search.text, ignoreCase = true)
        val matchesFilter = when (filter) {
            CourseFilter.ALL -> true
            CourseFilter.PASSED -> result.isPassed == true
            CourseFilter.FAILED -> result.isPassed == false
            CourseFilter.UNGRADED -> result.finalGrade == null
        }
        matchesSearch && matchesFilter
    }

    Scaffold(topBar = { TopAppBar(title = { Text("السنة ${arabicOrdinal(year)} • الفصل ${if (semester == 1) "الأول" else "الثاني"}", fontWeight = FontWeight.Bold) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("معدل الفصل", fontWeight = FontWeight.Bold)
                                Text("يُحسب من المواد التي أُدخلت درجاتها", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(avg?.let(::formatGrade) ?: "—", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("ناجح: $passed • راسب: $failed • غير مُقيّم: ${courses.size - passed - failed}", style = MaterialTheme.typography.bodySmall)
                        AcademicProgressBar(passed = passed, total = courses.size)
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("البحث باسم المادة أو الرمز") },
                    singleLine = true
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = filter == CourseFilter.ALL, onClick = { filter = CourseFilter.ALL }, label = { Text("الكل") })
                        FilterChip(selected = filter == CourseFilter.PASSED, onClick = { filter = CourseFilter.PASSED }, label = { Text("ناجح") })
                        FilterChip(selected = filter == CourseFilter.FAILED, onClick = { filter = CourseFilter.FAILED }, label = { Text("راسب") })
                    }
                    FilterChip(selected = filter == CourseFilter.UNGRADED, onClick = { filter = CourseFilter.UNGRADED }, label = { Text("غير مُقيّم") })
                }
            }
            if (visibleCourses.isEmpty()) item { EmptyState("لا توجد مواد مطابقة للبحث أو الفلتر الحالي.") }
            items(visibleCourses, key = { it.id }) { c ->
                val result = GradeCalculator.calculate(c, program)
                ElevatedCard(onClick = { onCourse(c.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                if (c.code.isNotBlank()) Text(c.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            GradeStatus(result.isPassed)
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth()) {
                            Text("الدرجة", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(result.finalGrade?.let { "${formatGrade(it)}/100" } ?: "لم تُدخل بعد", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseScreen(course: CourseEntity?, program: ProgramEntity?, onBack: () -> Unit, onSave: (CourseEntity) -> Unit) {
    if (course == null || program == null) return
    val result = GradeCalculator.calculate(course, program)
    var notes by remember(course.id, course.notes) { mutableStateOf(course.notes) }
    var noteSaved by remember(course.id, course.notes) { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("تفاصيل المادة", fontWeight = FontWeight.Bold) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(course.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (course.code.isNotBlank()) Text("رمز المقرر: ${course.code}")
                        if (course.language.isNotBlank()) Text("اللغة: ${course.language}")
                        Text("السنة ${arabicOrdinal(course.academicYear)} • الفصل ${if (course.semester == 1) "الأول" else "الثاني"}")
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("النتيجة النهائية", style = MaterialTheme.typography.labelMedium)
                                Text(result.finalGrade?.let { "${formatGrade(it)}/100" } ?: "—", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            GradeStatus(result.isPassed)
                        }
                        Text("حد النجاح: ${program.passingGrade.toInt()}/100", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { GradeEntryCard(course, program, onSave) }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("ملاحظاتي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it; noteSaved = false },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                            placeholder = { Text("أضف أي ملاحظة عن المادة، الامتحان أو الدراسة...") }
                        )
                        Button(
                            onClick = { onSave(course.copy(notes = notes)); noteSaved = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("حفظ الملاحظة") }
                        if (noteSaved) Text("تم حفظ الملاحظة.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatisticsScreen(
    universities: List<UniversityEntity>,
    programs: List<ProgramEntity>,
    courses: List<CourseEntity>,
    onHome: () -> Unit,
    onProgram: (Long) -> Unit
) {
    val totalGraded = courses.count { c -> programs.firstOrNull { it.id == c.programId }?.let { GradeCalculator.calculate(c, it).finalGrade != null } == true }
    val totalPassed = courses.count { c -> programs.firstOrNull { it.id == c.programId }?.let { GradeCalculator.calculate(c, it).isPassed == true } == true }
    val totalFailed = courses.count { c -> programs.firstOrNull { it.id == c.programId }?.let { GradeCalculator.calculate(c, it).isPassed == false } == true }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("الإحصائيات", fontWeight = FontWeight.Bold) }) },
        bottomBar = { RootNavigationBar(false, onHome = onHome, onStatistics = {}) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("الملخص العام", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${universities.size} جامعات • ${programs.size} برامج • ${courses.size} مقرر")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricBox("مُقيّمة", totalGraded.toString(), Modifier.weight(1f))
                            MetricBox("ناجحة", totalPassed.toString(), Modifier.weight(1f))
                            MetricBox("راسبة", totalFailed.toString(), Modifier.weight(1f))
                            MetricBox("متبقية", (courses.size - totalPassed).toString(), Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            AcademicProgressRing(totalPassed, courses.size)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("التقدم الأكاديمي العام", fontWeight = FontWeight.Bold)
                                AcademicProgressBar(totalPassed, courses.size)
                            }
                        }
                    }
                }
            }
            item { SectionHeader("حسب البرنامج", "اضغط على أي برنامج للانتقال إلى تفاصيله") }
            items(programs, key = { it.id }) { p ->
                val pc = courses.filter { it.programId == p.id }
                val grades = pc.mapNotNull { GradeCalculator.calculate(it, p).finalGrade }
                val passed = pc.count { GradeCalculator.calculate(it, p).isPassed == true }
                val failed = pc.count { GradeCalculator.calculate(it, p).isPassed == false }
                val progress = if (pc.isEmpty()) 0f else passed.toFloat() / pc.size.toFloat()
                ElevatedCard(onClick = { onProgram(p.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(p.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth()) {
                            Text("${pc.size} مقرر • ${grades.size} مُقيّم", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(grades.takeIf { it.isNotEmpty() }?.average()?.let { "${formatGrade(it)}%" } ?: "—", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        AcademicProgressBar(passed = passed, total = pc.size)
                        Text("$passed ناجح • $failed راسب • ${pc.size - passed - failed} غير مُقيّم • ${pc.size - passed} متبقي للإنجاز", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    TextButton(onClick = onBack) { Text("‹ رجوع", fontWeight = FontWeight.Bold) }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MetricBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun GradeStatus(status: Boolean?) {
    val text = when (status) { true -> "ناجح"; false -> "راسب"; null -> "غير مُقيّم" }
    val container = when (status) {
        true -> MaterialTheme.colorScheme.primaryContainer
        false -> MaterialTheme.colorScheme.errorContainer
        null -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = container, shape = RoundedCornerShape(50)) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyState(message: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(18.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatGrade(value: Double): String = String.format(Locale.US, "%.2f", value)
private fun arabicOrdinal(year: Int): String = when (year) { 1 -> "الأولى"; 2 -> "الثانية"; 3 -> "الثالثة"; 4 -> "الرابعة"; 5 -> "الخامسة"; else -> year.toString() }


@Composable
private fun AcademicProgressRing(
    passed: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val progress = if (total > 0) passed.toFloat() / total.toFloat() else 0f
    val percent = (progress * 100f).roundToInt()
    Box(modifier = modifier.size(88.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = 9.dp.toPx()
            drawArc(
                color = MaterialTheme.colorScheme.surfaceVariant,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = MaterialTheme.colorScheme.primary,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Text("$percent%", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun AcademicProgressBar(
    passed: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val progress = if (total > 0) passed.toFloat() / total.toFloat() else 0f
    val percent = progress * 100f
    Column(modifier = modifier) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "$passed / $total مادة ناجحة — ${"%.1f".format(percent)}%",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
