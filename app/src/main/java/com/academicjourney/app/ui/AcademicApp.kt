package com.academicjourney.app.ui

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import kotlin.math.roundToInt
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.academicjourney.app.BuildConfig
import com.academicjourney.app.R
import com.academicjourney.app.data.CourseEntity
import com.academicjourney.app.data.DiplomacyCurriculum
import com.academicjourney.app.data.HighSchoolGradeEntity
import com.academicjourney.app.data.HighSchoolSeedData
import com.academicjourney.app.data.LatakiaTranslationCurriculum
import com.academicjourney.app.data.ProgramEntity
import com.academicjourney.app.data.UniversityEntity
import com.academicjourney.app.domain.AcademicCelebration
import com.academicjourney.app.domain.AcademicCelebrationDetector
import com.academicjourney.app.domain.GradeCalculator
import com.academicjourney.app.domain.CourseSearch
import com.academicjourney.app.domain.HighSchoolCalculator
import com.academicjourney.app.domain.PartialGradePreview
import com.academicjourney.app.domain.PartialGradePreviewBuilder
import com.academicjourney.app.domain.StudentStandingCalculator
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface Screen : java.io.Serializable {
    data object Current : Screen
    data object More : Screen
    data object History : Screen
    data object Archives : Screen
    data class Dates(val courseId: Long? = null) : Screen
    data object Home : Screen
    data object Universities : Screen
    data object HighSchool : Screen
    data class HighSchoolBranch(val branch: String) : Screen
    data object Statistics : Screen
    data class University(val id: Long) : Screen
    data class Program(val id: Long) : Screen
    data class Year(val programId: Long, val year: Int) : Screen
    data class Semester(val programId: Long, val year: Int, val semester: Int) : Screen
    data class Course(val id: Long) : Screen
}

@Composable
fun AcademicApp(vm: AcademicViewModel, openCourse: Long = 0, openProgram: Long = 0, launchToken: Long = 0) {
    val universities by vm.universities.collectAsState()
    val programs by vm.programs.collectAsState()
    val courses by vm.courses.collectAsState()
    val highSchoolGrades by vm.highSchoolGrades.collectAsState()
    val events by vm.events.collectAsState()
    val history by vm.history.collectAsState()
    val archives by vm.archives.collectAsState()
    val pinned by vm.pinnedProgram.collectAsState()
    val lastCourse by vm.lastCourse.collectAsState()
    val lastBackup by vm.lastBackup.collectAsState()
    val preview by vm.restorePreview.collectAsState()
    val busy by vm.restoreBusy.collectAsState()
    val message by vm.message.collectAsState()
    var screen by rememberSaveable { mutableStateOf<Screen>(Screen.Home) }
    var eventBack by rememberSaveable { mutableStateOf<Screen>(Screen.More) }
    val pageState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var showIntro by rememberSaveable { mutableStateOf(true) }
    var backupMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var celebration by remember { mutableStateOf<AcademicCelebration?>(null) }
    val snackbar = remember { SnackbarHostState() }

    val navigate: (String) -> Unit = { screen = when (it) {
        "current" -> Screen.Current
        "stats" -> Screen.Statistics
        "more" -> Screen.More
        else -> Screen.Home
    } }
    val selectProgram: (Long) -> Unit = {
        if (pinned == 0L) vm.pinProgram(it)
        screen = Screen.Program(it)
    }
    val selectCourse: (Long) -> Unit = { vm.rememberCourse(it); screen = Screen.Course(it) }
    val schedule: (Long) -> Unit = { eventBack = screen; screen = Screen.Dates(it) }
    val save: (CourseEntity) -> Unit = { updated ->
        vm.saveCourse(updated) { before, after ->
            programs.firstOrNull { it.id == after.programId }?.let { program ->
                val event = AcademicCelebrationDetector.detect(
                    universityName = universities.firstOrNull { it.id == program.universityId }?.name.orEmpty(),
                    program = program,
                    courses = courses.filter { it.programId == program.id }.map { if (it.id == before.id) before else it },
                    originalCourse = before, updatedCourse = after)
                if (event != null) celebration = event
            }
        }
    }
    val createBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { backupMessage = "جارٍ تجهيز النسخة الاحتياطية…"; vm.exportBackup(it) { result -> backupMessage = result; vm.message.value = result } }
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { backupMessage = "جارٍ قراءة النسخة ومعاينة الفروق…"; vm.importBackup(it) { result -> backupMessage = result; vm.message.value = result } }
    }
    val exportBackup = {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        createBackupLauncher.launch("AcademicJourney-backup-$stamp.json")
    }
    val importBackup = { restoreBackupLauncher.launch(arrayOf("application/pdf", "application/json", "text/plain", "application/octet-stream")) }

    LaunchedEffect(Unit) { vm.ensureSeeded() }
    LaunchedEffect(launchToken) {
        if (openCourse > 0) selectCourse(openCourse)
        else if (openProgram > 0) selectProgram(openProgram)
    }
    LaunchedEffect(message) { message?.let { text ->
        snackbar.showSnackbar(text, withDismissAction = true)
        if (vm.message.value == text) vm.message.value = null
    } }

    fun back() {
        screen = when (val s = screen) {
            Screen.Home, Screen.Current, Screen.More, Screen.Statistics, Screen.Universities, Screen.HighSchool -> Screen.Home
            Screen.History, Screen.Archives -> Screen.More
            is Screen.Dates -> eventBack
            is Screen.HighSchoolBranch -> Screen.HighSchool
            is Screen.University -> Screen.Home
            is Screen.Program -> programs.firstOrNull { it.id == s.id }?.let { Screen.University(it.universityId) } ?: Screen.Home
            is Screen.Year -> Screen.Program(s.programId)
            is Screen.Semester -> Screen.Year(s.programId, s.year)
            is Screen.Course -> courses.firstOrNull { it.id == s.id }?.let { Screen.Year(it.programId, it.academicYear) } ?: Screen.Home
        }
    }
    BackHandler(enabled = screen !is Screen.Home && !showIntro) { back() }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalJourneyNavigation provides navigate) {
        AcademicJourneyTheme {
            Box(Modifier.fillMaxSize()) {
                if (showIntro) IntroVideoScreen(onFinished = { showIntro = false })
                else pageState.SaveableStateProvider(screen.toString()) {
                    when (val s = screen) {
                        Screen.Home -> JourneyDashboard(universities, programs, courses, events, pinned, lastCourse,
                            { screen = Screen.University(it) }, selectProgram, selectCourse,
                            { eventBack = Screen.Home; screen = Screen.Dates() }, { screen = Screen.HighSchool })
                        Screen.Current -> CurrentSemesterScreen(programs, universities, courses, vm, save, schedule)
                        Screen.More -> JourneyMoreScreen(vm, programs, universities, pinned, lastBackup, backupMessage,
                            exportBackup, importBackup, { eventBack = Screen.More; screen = Screen.Dates() },
                            { screen = Screen.History }, { screen = Screen.Archives }, { screen = Screen.HighSchool })
                        Screen.History -> HistoryScreen(history, courses, highSchoolGrades, vm, ::back)
                        Screen.Archives -> ArchiveScreen(archives, ::back)
                        is Screen.Dates -> EventsScreen(events, courses, programs, vm, s.courseId, ::back)
                        Screen.Universities -> UniversitiesScreen(universities, programs, courses,
                            onBack = ::back, onHome = { screen = Screen.Home },
                            onUniversity = { screen = Screen.University(it) }, onStatistics = { screen = Screen.Statistics })
                        Screen.HighSchool -> HighSchoolScreen(highSchoolGrades, ::back, { screen = Screen.HighSchoolBranch(it) })
                        is Screen.HighSchoolBranch -> HighSchoolBranchScreen(s.branch,
                            highSchoolGrades.filter { it.branch == s.branch }, ::back, vm::saveHighSchoolGrade)
                        Screen.Statistics -> StatisticsScreen(universities, programs, courses, highSchoolGrades,
                            { screen = Screen.Home }, selectProgram, { screen = Screen.HighSchool })
                        is Screen.University -> UniversityScreen(universities.firstOrNull { it.id == s.id },
                            programs.filter { it.universityId == s.id }, courses, ::back, selectProgram)
                        is Screen.Program -> ProgramScreen(programs.firstOrNull { it.id == s.id },
                            programs.firstOrNull { it.id == s.id }?.let { p -> universities.firstOrNull { it.id == p.universityId }?.name }.orEmpty(),
                            courses.filter { it.programId == s.id }, ::back, { screen = Screen.Year(s.id, it) }, selectCourse)
                        is Screen.Year -> ExpandableYearScreen(programs.firstOrNull { it.id == s.programId }, s.year,
                            courses.filter { it.programId == s.programId && it.academicYear == s.year }, vm, ::back, save, schedule)
                        is Screen.Semester -> ExpandableYearScreen(programs.firstOrNull { it.id == s.programId }, s.year,
                            courses.filter { it.programId == s.programId && it.academicYear == s.year }, vm, ::back, save, schedule)
                        is Screen.Course -> {
                            val c = courses.firstOrNull { it.id == s.id }
                            val p = programs.firstOrNull { it.id == c?.programId }
                            JourneyPage(c?.name ?: "المقرر", ::back) { pad ->
                                LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp)) {
                                    if (c != null && p != null) item { CourseAccordion(c, p, true, {}, save, vm, schedule, canCollapse = false) }
                                }
                            }
                        }
                    }
                }
                if (!showIntro) SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 96.dp))
                preview?.let { RestorePreviewDialog(it, busy, vm::confirmRestore, vm::cancelRestore) }
                celebration?.let { AcademicCelebrationDialog(it) { celebration = null } }
            }
        }
    }
}

@Composable
private fun AcademicCelebrationDialog(
    event: AcademicCelebration,
    onDismiss: () -> Unit
) {
    val durationMillis = if (event is AcademicCelebration.Graduation) 5_200L else 4_200L
    LaunchedEffect(event) {
        delay(durationMillis)
        onDismiss()
    }

    val transition = rememberInfiniteTransition(label = "academic celebration")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "celebration pulse"
    )
    val sway by transition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "celebration sway"
    )
    val rise by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "year promotion rise"
    )
    val confettiFall by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1_800)),
        label = "graduation confetti"
    )

    val emoji: String
    val title: String
    val message: String
    val accent: Color
    when (event) {
        is AcademicCelebration.CourseSuccess -> {
            emoji = "✅✨"
            title = "مبروك النجاح بالمادة!"
            message = "نجحت في مادة ${event.courseName}. خطوة جديدة أُنجزت بنجاح."
            accent = Color(0xFF146C38)
        }
        is AcademicCelebration.YearPromotion -> {
            emoji = "🚀⬆️"
            title = "مبروك الانتقال للسنة ${celebrationYearName(event.year)}!"
            message = "أنجزت متطلبات الترفع وبدأت مرحلة أكاديمية جديدة."
            accent = Color(0xFF0B6A88)
        }
        is AcademicCelebration.Graduation -> {
            emoji = "🎓🎉🏆"
            title = "ألف مبروك التخرج!"
            message = "أتممت جميع مقررات ${event.programName} بنجاح. تستحق هذا الإنجاز."
            accent = Color(0xFF8A5A00)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f)),
            contentAlignment = Alignment.Center
        ) {
            if (event is AcademicCelebration.Graduation) {
                GraduationConfetti(
                    fall = confettiFall,
                    modifier = Modifier.fillMaxSize()
                )
            }
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 480.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = emoji,
                        style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier.graphicsLayer {
                            when (event) {
                                is AcademicCelebration.CourseSuccess -> {
                                    scaleX = pulse
                                    scaleY = pulse
                                    rotationZ = sway
                                }
                                is AcademicCelebration.YearPromotion -> {
                                    translationY = -34f * rise
                                    scaleX = 1f + rise * 0.05f
                                    scaleY = 1f + rise * 0.05f
                                }
                                is AcademicCelebration.Graduation -> {
                                    scaleX = pulse
                                    scaleY = pulse
                                    rotationZ = sway
                                }
                            }
                        }
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    InteractiveButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                    ) {
                        Text("رائع!", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun GraduationConfetti(fall: Float, modifier: Modifier = Modifier) {
    val colors = listOf(
        Color(0xFFFFC107),
        Color(0xFF00A6A6),
        Color(0xFFE64A73),
        Color(0xFF6C63FF),
        Color(0xFF4CAF50)
    )
    Canvas(modifier = modifier) {
        repeat(44) { index ->
            val x = (((index * 37) % 101) / 100f) * size.width
            val start = (((index * 53) % 103) / 102f) * size.height
            val y = (start + fall * size.height * 0.55f) % size.height
            val color = colors[index % colors.size]
            if (index % 2 == 0) {
                drawCircle(color = color, radius = 4f + (index % 4), center = Offset(x, y))
            } else {
                drawRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(width = 7f + (index % 5), height = 12f + (index % 7))
                )
            }
        }
    }
}

private fun celebrationYearName(year: Int): String = when (year) {
    1 -> "الأولى"
    2 -> "الثانية"
    3 -> "الثالثة"
    4 -> "الرابعة"
    5 -> "الخامسة"
    else -> year.toString()
}

@Composable
private fun IntroVideoScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    var finished by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    var musicMuted by rememberSaveable { mutableStateOf(false) }
    val finishOnce: () -> Unit = {
        if (!finished) {
            finished = true
            onFinished()
        }
    }

    LaunchedEffect(Unit) {
        // Completion normally closes the intro. This is only a safety timeout for damaged media.
        delay(15_000)
        finishOnce()
    }

    DisposableEffect(Unit) {
        onDispose {
            videoView?.stopPlayback()
            videoView = null
            mediaPlayer = null
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF101B2B))) {
        AndroidView(
            factory = { viewContext ->
                VideoView(viewContext).apply {
                    videoView = this
                    setVideoURI(Uri.parse("android.resource://${context.packageName}/${R.raw.app_intro}"))
                    setOnPreparedListener { player ->
                        mediaPlayer = player
                        player.isLooping = false
                        player.setVolume(if (musicMuted) 0f else 0.32f, if (musicMuted) 0f else 0.32f)
                        start()
                    }
                    setOnCompletionListener { finishOnce() }
                    setOnErrorListener { _, _, _ ->
                        finishOnce()
                        true
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Transparent, Color(0xD8101B2B)),
                    startY = 180f
                )
            )
        )
        InteractiveTextButton(
            onClick = finishOnce,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Text("تخطي", color = Color.White, fontWeight = FontWeight.Bold)
        }
        InteractiveTextButton(
            onClick = {
                musicMuted = !musicMuted
                val volume = if (musicMuted) 0f else 0.32f
                mediaPlayer?.setVolume(volume, volume)
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Text(if (musicMuted) "تشغيل الموسيقا" else "كتم الموسيقا", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text("مسيرتي الأكاديمية", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color.White)
            Text("جامعاتك ودرجاتك وتقدمك في مكان واحد", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                IntroFeaturePill("الجامعات")
                IntroFeaturePill("الدرجات")
                IntroFeaturePill("التقارير")
            }
            Text("الإصدار ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun IntroFeaturePill(label: String) {
    Surface(
        color = Color.White.copy(alpha = 0.16f),
        contentColor = Color.White,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RootNavigationBar(homeSelected: Boolean, onHome: () -> Unit, onStatistics: () -> Unit) {
    JourneyNavigation(if (homeSelected) "home" else "stats")
}

@Composable
private fun LegacyRootNavigationBar(homeSelected: Boolean, onHome: () -> Unit, onStatistics: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
        Text(
            "الإصدار ${BuildConfig.VERSION_NAME}",
            modifier = Modifier.padding(bottom = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InteractiveElevatedCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "cardPressScale"
    )
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        interactionSource = interactionSource,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    onUniversities: () -> Unit,
    onHighSchool: () -> Unit,
    onStatistics: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    backupMessage: String?
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("مسيرتي الأكاديمية", fontWeight = FontWeight.Bold)
                        Text("اختر القسم الذي تريد متابعته", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        },
        bottomBar = { RootNavigationBar(true, onHome = {}, onStatistics = onStatistics) }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("الأقسام الرئيسية", "كل قسم منظم ببطاقات ثنائية الأعمدة لسهولة الوصول")
            }
            item {
                PortalCard(
                    title = "الجامعات",
                    subtitle = "البرامج والمقررات والتقدم الأكاديمي",
                    image = R.drawable.home_universities,
                    onClick = onUniversities
                )
            }
            item {
                PortalCard(
                    title = "الثانوية العامة",
                    subtitle = "درجات الفرعين العلمي والأدبي وحساب النسبة",
                    image = R.drawable.home_high_school,
                    onClick = onHighSchool
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                BackupCard(
                    onExportBackup = onExportBackup,
                    onImportBackup = onImportBackup,
                    message = backupMessage
                )
            }
        }
    }
}

@Composable
private fun BackupCard(
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    message: String?
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("حماية البيانات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "يمكن استيراد تقارير PDF الجديدة لاستعادة درجات الفرع وملاحظاته. نسخة JSON شاملة لكل البرامج والمواعيد والسجل والأرشيف. تظهر معاينة قبل تطبيق الاستعادة، وتبقى التقارير القديمة متاحة للعرض والطباعة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InteractiveButton(onClick = onExportBackup, modifier = Modifier.weight(1f)) {
                    Text("تصدير نسخة احتياطية", fontWeight = FontWeight.Bold)
                }
                InteractiveOutlinedButton(onClick = onImportBackup, modifier = Modifier.weight(1f)) {
                    Text("استيراد PDF أو JSON", fontWeight = FontWeight.Bold)
                }
            }
            if (!message.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        message,
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun PortalCard(
    title: String,
    subtitle: String,
    @DrawableRes image: Int,
    onClick: () -> Unit
) {
    InteractiveElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().height(242.dp)) {
        Box(Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(image),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xED172235)),
                        startY = 75f
                    )
                )
            )
            Column(
                Modifier.align(Alignment.BottomStart).padding(13.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HighSchoolScreen(
    grades: List<HighSchoolGradeEntity>,
    onBack: () -> Unit,
    onBranch: (String) -> Unit
) {
    val scientific = grades.filter { it.branch == HighSchoolSeedData.SCIENTIFIC_2016 }
    val literary = grades.filter { it.branch == HighSchoolSeedData.LITERARY_2026 }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الثانوية العامة", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackButton(onBack) }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("اختر الفرع", "أدخل الدرجات ضمن حدود كل مادة وسيُحسب المجموع والنسبة تلقائيًا")
            }
            item {
                HighSchoolBranchCard(
                    title = "الفرع العلمي",
                    year = "2016",
                    image = R.drawable.high_school_scientific,
                    percentage = highSchoolPercentage(scientific),
                    onClick = { onBranch(HighSchoolSeedData.SCIENTIFIC_2016) }
                )
            }
            item {
                HighSchoolBranchCard(
                    title = "الفرع الأدبي",
                    year = "2026",
                    image = R.drawable.high_school_literary,
                    percentage = highSchoolPercentage(literary),
                    onClick = { onBranch(HighSchoolSeedData.LITERARY_2026) }
                )
            }
        }
    }
}

@Composable
private fun HighSchoolBranchCard(
    title: String,
    year: String,
    @DrawableRes image: Int,
    percentage: Double,
    onClick: () -> Unit
) {
    InteractiveElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().height(274.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Image(
                    painter = painterResource(image),
                    contentDescription = "$title $year",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xE1172235)), startY = 50f)
                    )
                )
                Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
                    Text(year, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("النسبة الحالية", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "${formatGrade(percentage)}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                PercentageRing(percentage = percentage, modifier = Modifier.size(58.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HighSchoolBranchScreen(
    branch: String,
    grades: List<HighSchoolGradeEntity>,
    onBack: () -> Unit,
    onSave: (HighSchoolGradeEntity) -> Unit
) {
    val context = LocalContext.current
    val isScientific = branch == HighSchoolSeedData.SCIENTIFIC_2016
    val branchTitle = if (isScientific) "الفرع العلمي 2016" else "الفرع الأدبي 2026"
    val summary = HighSchoolCalculator.calculate(grades)
    val total = summary.totalGrade
    val maximum = summary.maximumGrade
    val excludedNames = grades.filterNot { it.includedInPercentage }.joinToString(" و") { it.subject }
    var reportMessage by rememberSaveable(branch) { mutableStateOf<String?>(null) }
    val exportReportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            reportMessage = "جارٍ إنشاء ملف PDF..."
            ReportPrinter.exportHighSchoolReport(context, it, branchTitle, grades) { message ->
                reportMessage = message
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(branchTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = { BackButton(onBack) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            PercentageRing(summary.percentage, Modifier.size(96.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("النتيجة الحالية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MetricBox("المجموع", total.toString(), Modifier.weight(1f))
                                    MetricBox("العظمى", maximum.toString(), Modifier.weight(1f))
                                }
                                Text(
                                    "النسبة العامة: ${formatGrade(summary.percentage)}%",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            "لا يدخل في حساب النسبة: $excludedNames.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        InteractiveButton(
                            onClick = {
                                exportReportLauncher.launch(reportPdfFileName(branchTitle))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تصدير التقرير PDF", fontWeight = FontWeight.Bold)
                        }
                        InteractiveOutlinedButton(
                            onClick = {
                                reportMessage = "جارٍ تجهيز نافذة الطباعة..."
                                ReportPrinter.printHighSchoolReport(context, branchTitle, grades) { message ->
                                    reportMessage = message
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("طباعة التقرير", fontWeight = FontWeight.Bold)
                        }
                        if (!reportMessage.isNullOrBlank()) {
                            Text(
                                reportMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            item { SectionHeader("المواد والدرجات", "الدرجة المقبولة من 0 حتى الدرجة العظمى الموضحة لكل مادة") }
            if (grades.isEmpty()) {
                item { EmptyState("يتم الآن تجهيز جدول المواد...") }
            }
            items(grades, key = { it.id }) { item ->
                SubjectGradeCard(item = item, onSave = onSave)
            }
        }
    }
}

@Composable
private fun SubjectGradeCard(
    item: HighSchoolGradeEntity,
    onSave: (HighSchoolGradeEntity) -> Unit
) {
    var value by remember(item.id, item.grade) { mutableStateOf(item.grade?.toString().orEmpty()) }
    var inputError by remember(item.id) { mutableStateOf<String?>(null) }
    val enteredGrade = value.toIntOrNull()?.takeIf { it in 0..item.maxGrade }
    val subjectPercentage = HighSchoolCalculator.subjectPercentage(item.copy(grade = enteredGrade))
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("الدرجة العظمى: ${item.maxGrade}", style = MaterialTheme.typography.bodySmall)
                }
                if (!item.includedInPercentage) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("مستثناة من النسبة", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            OutlinedTextField(
                value = value,
                onValueChange = { input ->
                    value = input
                    when {
                        input.isEmpty() -> {
                            inputError = null
                            onSave(item.copy(grade = null))
                        }
                        else -> {
                            val number = input.takeIf { text -> text.all { it.isDigit() } }?.toIntOrNull()
                            if (number == null || number !in 0..item.maxGrade) {
                                inputError = "يجب أن تكون الدرجة بين 0 و${item.maxGrade}."
                            } else {
                                inputError = null
                                onSave(item.copy(grade = number))
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("الدرجة من ${item.maxGrade}") },
                supportingText = {
                    Text(
                        inputError ?: if (item.includedInPercentage) "تدخل هذه المادة في حساب النسبة."
                        else "تُحفظ الدرجة للمراجعة ولا تدخل في حساب النسبة."
                    )
                },
                isError = inputError != null,
                suffix = { Text("/ ${item.maxGrade}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
            )
            if (subjectPercentage != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PercentageRing(subjectPercentage, Modifier.size(58.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            "نسبة المادة: ${formatGrade(subjectPercentage)}%",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LinearProgressIndicator(
                            progress = { (subjectPercentage / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private fun highSchoolPercentage(grades: List<HighSchoolGradeEntity>): Double {
    return HighSchoolCalculator.calculate(grades).percentage
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UniversitiesScreen(
    universities: List<UniversityEntity>,
    programs: List<ProgramEntity>,
    courses: List<CourseEntity>,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onUniversity: (Long) -> Unit,
    onStatistics: () -> Unit
) {
    val gradedCount = courses.count { c -> programs.firstOrNull { it.id == c.programId }?.let { GradeCalculator.calculate(c, it).finalGrade != null } == true }
    val passedCount = courses.count { c -> programs.firstOrNull { it.id == c.programId }?.let { GradeCalculator.calculate(c, it).isPassed == true } == true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الجامعات", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackButton(onBack) }
            )
        },
        bottomBar = { RootNavigationBar(true, onHome = onHome, onStatistics = onStatistics) }
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
            NumericMetricValue(value)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun UniversityCard(university: UniversityEntity, programCount: Int, courseCount: Int, gradedCount: Int, passedCount: Int, onClick: () -> Unit) {
    InteractiveElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 232.dp)) {
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
internal fun universityLogo(name: String): Int = when {
    "الافتراضية" in name -> R.drawable.logo_svu
    "الأندلس" in name -> R.drawable.logo_andalus
    "دمشق" in name -> R.drawable.logo_damascus
    "اللاذقية" in name -> R.drawable.logo_latakia
    else -> R.drawable.logo_svu
}

@DrawableRes
private fun programImage(name: String): Int = when {
    "الإعلام والاتصال" in name -> R.drawable.program_media
    "الموارد البشرية" in name -> R.drawable.program_hr
    "إدارة الأعمال" in name -> R.drawable.program_mba
    "علوم الحاسوب" in name -> R.drawable.program_cs
    name == "التاريخ" -> R.drawable.program_history
    LatakiaTranslationCurriculum.isProgramme(name) -> R.drawable.course_language
    "الدولية والدبلوماسية" in name -> R.drawable.program_diplomacy
    "الهندسة الطبية" in name -> R.drawable.program_biomedical
    else -> R.drawable.program_mba
}

@DrawableRes
private fun courseImage(course: CourseEntity, program: ProgramEntity): Int {
    val name = course.name
    return when {
        "إدارة المؤسسات الإعلامية" in name -> R.drawable.course_media_management
        listOf("رياضيات", "الرياضيات", "إحصاء", "الاحصاء", "احتمالات").any { it in name } ->
            R.drawable.course_math
        listOf("فيزياء", "الفيزياء", "ميكانيك", "ترموديناميك", "كهرومغناطيس").any { it in name } ->
            R.drawable.course_physics
        listOf(
            "اللغة", "إنكليز", "انكليز", "فرنسي", "مصطلحات", "ترجمة", "القواعد",
            "الكتابة", "القراءة", "المعاجم", "الصوتيات", "الاستماع", "لغويات"
        ).any { it in name } ->
            R.drawable.course_language
        listOf("إعلام", "صحافة", "اتصال", "إذاعة", "تلفزيون", "سينما", "تحرير").any { it in name } ->
            R.drawable.program_media
        listOf("حاسوب", "حواسب", "برمج", "خوارزم", "شبكات", "ويب", "معلومات", "ذكاء صنعي").any { it in name } ->
            R.drawable.program_cs
        listOf("طب", "طبية", "حيوي", "تشريح", "فيزيولوج", "أجهزة", "شعاع", "إشارة").any { it in name } ->
            R.drawable.program_biomedical
        listOf("دبلوماس", "سياس", "قانون", "علاقات دولية", "بروتوكول").any { it in name } ->
            R.drawable.program_diplomacy
        listOf("تاريخ", "آثار", "حضارة").any { it in name } ->
            R.drawable.program_history
        listOf("موارد بشرية", "سلوك تنظيمي", "قيادة").any { it in name } ->
            R.drawable.program_hr
        listOf("إدارة", "محاسب", "اقتصاد", "تسويق", "مالية", "أعمال").any { it in name } ->
            R.drawable.program_mba
        program.gradingScheme == GradeCalculator.SVU_WEIGHTED -> programImage(program.name)
        else -> R.drawable.course_study
    }
}

@DrawableRes
private fun yearImage(year: Int): Int = when (year) {
    1 -> R.drawable.year_1
    2 -> R.drawable.year_2
    3 -> R.drawable.year_3
    4 -> R.drawable.year_4
    else -> R.drawable.year_5
}

@DrawableRes
private fun semesterImage(semester: Int): Int = if (semester == 1) R.drawable.semester_1 else R.drawable.semester_2

private fun englishYear(year: Int): String = when (year) {
    1 -> "First year"
    2 -> "Second year"
    3 -> "Third year"
    4 -> "Fourth year"
    5 -> "Fifth year"
    else -> "Year $year"
}

private fun englishSemester(semester: Int): String = if (semester == 1) "First semester" else "Second semester"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UniversityScreen(
    university: UniversityEntity?,
    programs: List<ProgramEntity>,
    courses: List<CourseEntity>,
    onBack: () -> Unit,
    onProgram: (Long) -> Unit
) {
    val visiblePrograms = programs.distinctBy { it.name.trim() }
    Scaffold(topBar = { TopAppBar(title = { Text(university?.name ?: "الجامعة", fontWeight = FontWeight.Bold) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            university?.let { u ->
                item(span = { GridItemSpan(maxLineSpan) }) {
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
            item(span = { GridItemSpan(maxLineSpan) }) {
                val programIds = visiblePrograms.map { it.id }.toSet()
                val universityCourses = courses.filter { it.programId in programIds }
                val passed = universityCourses.count { c -> visiblePrograms.firstOrNull { it.id == c.programId }?.let { GradeCalculator.calculate(c, it).isPassed == true } == true }
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("التقدم في الجامعة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        AcademicProgressBar(passed = passed, total = universityCourses.size)
                        Text("متبقي للإنجاز: ${universityCourses.size - passed} مادة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("البرامج", "اختر البرنامج لعرض سنواته وفصوله ومقرراته")
            }
            items(visiblePrograms, key = { it.name }) { p ->
                val pc = courses.filter { it.programId == p.id }
                val graded = pc.mapNotNull { GradeCalculator.calculate(it, p).finalGrade }
                val passed = pc.count { GradeCalculator.calculate(it, p).isPassed == true }
                ProgramImageCard(
                    program = p,
                    courseCount = pc.size,
                    gradedCount = graded.size,
                    passedCount = passed,
                    average = GradeCalculator.average(pc, p),
                    onClick = { onProgram(p.id) }
                )
            }
        }
    }
}

@Composable
private fun ProgramImageCard(
    program: ProgramEntity,
    courseCount: Int,
    gradedCount: Int,
    passedCount: Int,
    average: Double?,
    onClick: () -> Unit
) {
    val progress = if (courseCount == 0) 0f else passedCount.toFloat() / courseCount.toFloat()
    InteractiveElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.fillMaxWidth().height(154.dp)) {
                Image(
                    painter = painterResource(programImage(program.name)),
                    contentDescription = "صورة برنامج ${program.name}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xE61B2433)),
                            startY = 45f
                        )
                    )
                )
                Column(
                    Modifier.align(Alignment.BottomStart).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        program.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (program.degreeType.isNotBlank()) {
                        Text(program.degreeType, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.88f))
                    }
                }
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$courseCount مقرر • $gradedCount مُقيّم", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Row(Modifier.fillMaxWidth()) {
                    Text("$passedCount ناجحة", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    Text(average?.let { "${formatGrade(it)}%" } ?: "—", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgramScreen(
    program: ProgramEntity?,
    universityName: String,
    courses: List<CourseEntity>,
    onBack: () -> Unit,
    onYear: (Int) -> Unit,
    onCourse: (Long) -> Unit
) {
    if (program == null) return
    val context = LocalContext.current
    val years = courses.map { it.academicYear }.distinct().sorted()
    val overallAverage = GradeCalculator.average(courses, program)
    val passed = courses.count { GradeCalculator.calculate(it, program).isPassed == true }
    val failed = courses.count { GradeCalculator.calculate(it, program).isPassed == false }
    val standing = StudentStandingCalculator.calculate(universityName, program, courses)
    var programFilter by remember(program.id) { mutableStateOf(CourseFilter.ALL) }
    var programSearch by remember(program.id) { mutableStateOf(TextFieldValue("")) }
    var reportMessage by rememberSaveable(program.id) { mutableStateOf<String?>(null) }
    val exportReportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            reportMessage = "جارٍ إنشاء ملف PDF..."
            ReportPrinter.exportProgramReport(
                context = context,
                uri = it,
                universityName = universityName,
                program = program,
                courses = courses
            ) { message -> reportMessage = message }
        }
    }
    val statusCourses = courses.filter { c ->
        val result = GradeCalculator.calculate(c, program)
        val matchesSearch = CourseSearch.matches(c, programSearch.text)
        val matchesFilter = when (programFilter) {
            CourseFilter.ALL -> true
            CourseFilter.PASSED -> result.isPassed == true
            CourseFilter.FAILED -> result.isPassed == false
            CourseFilter.UNGRADED -> result.isPassed == null
        }
        matchesSearch && matchesFilter
    }

    Scaffold(topBar = { TopAppBar(title = { Text(program.name, fontWeight = FontWeight.Bold) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("الملخص الأكاديمي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricBox("المعدل", overallAverage?.let(::formatGrade) ?: "—", Modifier.weight(1f))
                            MetricBox("ناجح", passed.toString(), Modifier.weight(1f))
                            MetricBox("راسب", failed.toString(), Modifier.weight(1f))
                            MetricBox("متبقي", (courses.size - passed).toString(), Modifier.weight(1f))
                        }
                        AcademicProgressBar(passed = passed, total = courses.size)
                        Surface(
                            color = if (standing.isGraduated) Color(0xFFDDF7E6) else MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    "حالة الطالب: ${standing.title}",
                                    fontWeight = FontWeight.Bold,
                                    color = if (standing.isGraduated) Color(0xFF146C38) else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(standing.details, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HorizontalDivider()
                        Text("حد النجاح: ${program.passingGrade.toInt()}/100", fontWeight = FontWeight.SemiBold)
                        Text(
                            com.academicjourney.app.domain.GradeExplanation.average(courses, program),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            when (program.gradingScheme) {
                                GradeCalculator.SVU_WEIGHTED ->
                                    "طريقة الحساب: ${program.assignmentWeight.toInt()}% وظيفة + ${program.examWeight.toInt()}% امتحان. كل كسر يُجبر إلى العدد الصحيح الأعلى."
                                GradeCalculator.ANDALUS_SPLIT_PRACTICAL_THEORY ->
                                    "طريقة الحساب: أعمال الطالب + الامتحان العملي + النظري، والمجموع النهائي بين 0 و100، وكل كسر يُجبر للأعلى."
                                GradeCalculator.SINGLE_FINAL_GRADE ->
                                    "طريقة الحساب: درجة نهائية واحدة من 100، والنجاح من ${program.passingGrade.toInt()}، وكل كسر يُجبر للأعلى."
                                else -> "طريقة الحساب: العملي + النظري، والمجموع النهائي بين 0 و100، وكل كسر يُجبر للأعلى."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        InteractiveButton(
                            onClick = {
                                exportReportLauncher.launch(reportPdfFileName(program.name))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تصدير التقرير PDF", fontWeight = FontWeight.Bold)
                        }
                        InteractiveOutlinedButton(
                            onClick = {
                                reportMessage = "جارٍ تجهيز نافذة الطباعة..."
                                ReportPrinter.printProgramReport(
                                    context = context,
                                    universityName = universityName,
                                    program = program,
                                    courses = courses
                                ) { message -> reportMessage = message }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("طباعة التقرير", fontWeight = FontWeight.Bold)
                        }
                        if (!reportMessage.isNullOrBlank()) {
                            Text(
                                reportMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            if (universityName.contains("الأندلس")) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AcademicNoticeCard(
                        "تنويه جامعة الأندلس: مقرر اللغة الإنكليزية التكميلية (1) تابع للسنة الرابعة، " +
                            "ومقرر اللغة الإنكليزية التكميلية (2) تابع للسنة الخامسة."
                    )
                }
            }
            if (program.gradingScheme == GradeCalculator.SVU_WEIGHTED) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AcademicNoticeCard(
                        "تنويه الجامعة الافتراضية: الأحرف والرموز الإنجليزية المعروضة تحت اسم المادة هي رمز المقرر."
                    )
                }
            }
            if (GradeCalculator.maximumAssistance(program) > 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AcademicNoticeCard(
                        "درجات المساعدة: يمنح هذا البرنامج تلقائيًا درجة أو درجتين كحد أقصى فقط إذا أوصلتا النتيجة إلى حد النجاح. تظهر المساعدة في التطبيق وتقرير PDF."
                    )
                }
            }
            if (DiplomacyCurriculum.isProgramme(program.name)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AcademicNoticeCard(
                        "أرقام مقررات الدراسات الدولية والدبلوماسية (510–557) مثبتة تلقائيًا وفق ملف التسجيل 2025–2026، ولا تحتاج إلى إدخال يدوي."
                    )
                }
            }
            if (LatakiaTranslationCurriculum.isProgramme(program.name)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AcademicNoticeCard(
                        "برنامج الترجمة في اللغة الإنكليزية: لكل مقرر حقل درجة نهائية واحد، " +
                            "وحد النجاح 50/100. رُموز المقررات وتقسيم ف1 وف2 مثبتان وفق الجدول المرفق."
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("السنوات الدراسية", "اختر السنة لعرض الفصل الأول والفصل الثاني")
            }
            items(years, key = { it }) { year ->
                val yearCourses = courses.filter { it.academicYear == year }
                val yearPassed = yearCourses.count { GradeCalculator.calculate(it, program).isPassed == true }
                AcademicYearCard(
                    year = year,
                    courseCount = yearCourses.size,
                    passedCount = yearPassed,
                    average = GradeCalculator.average(yearCourses, program),
                    onClick = { onYear(year) }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("المواد حسب الحالة", "اعرض المواد على مستوى البرنامج بالكامل دون الحاجة لفتح كل فصل")
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = programSearch,
                    onValueChange = { programSearch = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("استعلام عن المادة بالاسم أو الرقم أو الرمز") },
                    supportingText = { Text("يشمل البحث جميع سنوات البرنامج وفصوله.") },
                    singleLine = true
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
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
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState("لا توجد مواد مطابقة لعبارة البحث والحالة المحددة.")
                }
            } else {
                items(
                    items = statusCourses,
                    key = { "program-status-${it.id}" },
                    span = { GridItemSpan(maxLineSpan) }
                ) { c ->
                    val result = GradeCalculator.calculate(c, program)
                    val partial = PartialGradePreviewBuilder.forCourse(c, program)
                    InteractiveElevatedCard(onClick = { onCourse(c.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(c.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "السنة ${arabicOrdinal(c.academicYear)} • الفصل ${if (c.semester == 1) "الأول" else "الثاني"}" +
                                        if (c.code.isNotBlank()) " • ${courseIdentifierLabel(program)}: ${c.code}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                GradeStatus(result.isPassed, result.assistancePoints, result.passedWithoutGrade)
                                Text(
                                    when {
                                        result.passedWithoutGrade -> "دون علامة"
                                        result.finalGrade != null -> formatGrade(result.finalGrade)
                                        partial != null -> partialEnteredText(partial)
                                        else -> "—"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (partial != null) {
                                    Text(
                                        partial.missingNotice,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (c.creditHours != null) {
                                    Text("${c.creditHours} ساعات", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun AcademicYearCard(
    year: Int,
    courseCount: Int,
    passedCount: Int,
    average: Double?,
    onClick: () -> Unit
) {
    InteractiveElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.fillMaxWidth().height(150.dp)) {
                Image(
                    painter = painterResource(yearImage(year)),
                    contentDescription = "${englishYear(year)} - السنة ${arabicOrdinal(year)}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xDC172235)), startY = 35f)
                    )
                )
                Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                    Text(englishYear(year), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
                    Text("السنة ${arabicOrdinal(year)}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                }
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$courseCount مقرر • $passedCount ناجح", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Row(Modifier.fillMaxWidth()) {
                    Text("المعدل", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    Text(average?.let(::formatGrade) ?: "—", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                LinearProgressIndicator(
                    progress = { if (courseCount == 0) 0f else (passedCount.toFloat() / courseCount).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearScreen(
    program: ProgramEntity?,
    year: Int,
    courses: List<CourseEntity>,
    onBack: () -> Unit,
    onSemester: (Int) -> Unit
) {
    if (program == null) return
    val passed = courses.count { GradeCalculator.calculate(it, program).isPassed == true }
    val failed = courses.count { GradeCalculator.calculate(it, program).isPassed == false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("السنة ${arabicOrdinal(year)}", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackButton(onBack) }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(program.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(englishYear(year), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricBox("المعدل", GradeCalculator.average(courses, program)?.let(::formatGrade) ?: "—", Modifier.weight(1f))
                            MetricBox("ناجح", passed.toString(), Modifier.weight(1f))
                            MetricBox("راسب", failed.toString(), Modifier.weight(1f))
                            MetricBox("متبقي", (courses.size - passed).toString(), Modifier.weight(1f))
                        }
                        AcademicProgressBar(passed = passed, total = courses.size)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("الفصول الدراسية", "اختر الفصل لعرض مقرراته ودرجاته") }
            items(listOf(1, 2), key = { it }) { semester ->
                val semesterCourses = courses.filter { it.semester == semester }
                SemesterImageCard(
                    program = program,
                    semester = semester,
                    courses = semesterCourses,
                    onClick = { onSemester(semester) }
                )
            }
        }
    }
}

@Composable
private fun SemesterImageCard(
    program: ProgramEntity,
    semester: Int,
    courses: List<CourseEntity>,
    onClick: () -> Unit
) {
    val average = GradeCalculator.semesterAverage(courses, program)
    val graded = courses.count { GradeCalculator.calculate(it, program).finalGrade != null }
    val passed = courses.count { GradeCalculator.calculate(it, program).isPassed == true }
    InteractiveElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.fillMaxWidth().height(150.dp)) {
                Image(
                    painter = painterResource(semesterImage(semester)),
                    contentDescription = englishSemester(semester),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xDF172235)), startY = 35f)
                    )
                )
                Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                    Text(englishSemester(semester), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1)
                    Text(
                        "ف$semester • الفصل ${if (semester == 1) "الأول" else "الثاني"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${courses.size} مقرر • $graded مُقيّم", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Row(Modifier.fillMaxWidth()) {
                    Text("$passed ناجح", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    Text(average?.let(::formatGrade) ?: "—", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                LinearProgressIndicator(
                    progress = { if (courses.isEmpty()) 0f else passed.toFloat() / courses.size.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
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
        val matchesSearch = CourseSearch.matches(c, search.text)
        val matchesFilter = when (filter) {
            CourseFilter.ALL -> true
            CourseFilter.PASSED -> result.isPassed == true
            CourseFilter.FAILED -> result.isPassed == false
            CourseFilter.UNGRADED -> result.isPassed == null
        }
        matchesSearch && matchesFilter
    }

    Scaffold(topBar = { TopAppBar(title = { Text("السنة ${arabicOrdinal(year)} • الفصل ${if (semester == 1) "الأول" else "الثاني"}", fontWeight = FontWeight.Bold) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
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
            if (program.gradingScheme == GradeCalculator.SVU_WEIGHTED) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "تنويه: الأحرف والرموز الإنجليزية الظاهرة تحت اسم المادة هي رمز المقرر.",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("البحث باسم المادة أو رمز/رقم المقرر") },
                    singleLine = true
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = filter == CourseFilter.ALL, onClick = { filter = CourseFilter.ALL }, label = { Text("الكل") })
                        FilterChip(selected = filter == CourseFilter.PASSED, onClick = { filter = CourseFilter.PASSED }, label = { Text("ناجح") })
                        FilterChip(selected = filter == CourseFilter.FAILED, onClick = { filter = CourseFilter.FAILED }, label = { Text("راسب") })
                    }
                    FilterChip(selected = filter == CourseFilter.UNGRADED, onClick = { filter = CourseFilter.UNGRADED }, label = { Text("غير مُقيّم") })
                }
            }
            if (visibleCourses.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("لا توجد مواد مطابقة للبحث أو الفلتر الحالي.") }
            }
            items(visibleCourses, key = { it.id }) { c ->
                val result = GradeCalculator.calculate(c, program)
                val partial = PartialGradePreviewBuilder.forCourse(c, program)
                InteractiveElevatedCard(onClick = { onCourse(c.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 264.dp)) {
                    Column {
                        Box(Modifier.fillMaxWidth().height(116.dp)) {
                            Image(
                                painter = painterResource(courseImage(c, program)),
                                contentDescription = "صورة مقرر ${c.name}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xC9172235)), startY = 35f)
                                )
                            )
                            Box(Modifier.align(Alignment.TopEnd).padding(7.dp)) {
                                GradeStatus(result.isPassed, result.assistancePoints, result.passedWithoutGrade)
                            }
                        }
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                c.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (c.code.isNotBlank()) {
                                Text(
                                    "${courseIdentifierLabel(program)}: ${c.code}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (c.creditHours != null) {
                                Text(
                                    "عدد الساعات: ${c.creditHours}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            HorizontalDivider()
                            Text(
                                when {
                                    result.passedWithoutGrade -> "ناجح بالترفيع دون علامة"
                                    result.finalGrade != null -> "الدرجة: ${formatGrade(result.finalGrade)}/100"
                                    partial != null -> partialEnteredText(partial)
                                    else -> "لم تُدخل الدرجة بعد"
                                },
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelMedium
                            )
                            if (partial != null) {
                                Text(
                                    partial.missingNotice,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (result.assistancePoints > 0) {
                                Text(
                                    "تتضمن مساعدة +${result.assistancePoints}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF146C38)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseScreen(
    course: CourseEntity?,
    program: ProgramEntity?,
    onBack: () -> Unit,
    onSave: (CourseEntity) -> Unit
) {
    if (course == null || program == null) return
    val result = GradeCalculator.calculate(course, program)
    val partial = PartialGradePreviewBuilder.forCourse(course, program)
    val isDiplomacy = DiplomacyCurriculum.isProgramme(program.name)
    var notes by remember(course.id, course.notes) { mutableStateOf(course.notes) }
    var noteSaved by remember(course.id, course.notes) { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("تفاصيل المادة", fontWeight = FontWeight.Bold) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 112.dp)
        ) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(course.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (course.code.isNotBlank()) Text("${courseIdentifierLabel(program)}: ${course.code}")
                        if (course.language.isNotBlank()) Text("اللغة: ${course.language}")
                        if (course.creditHours != null) Text("عدد الساعات: ${course.creditHours} ساعات", fontWeight = FontWeight.SemiBold)
                        Text("السنة ${arabicOrdinal(course.academicYear)} • الفصل ${if (course.semester == 1) "الأول" else "الثاني"}")
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (partial != null) "النتيجة الحالية (غير مكتملة)" else "النتيجة النهائية",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                if (partial != null) {
                                    partial.entered.forEach { component ->
                                        Text(
                                            "${component.label}: ${formatGrade(component.value)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else {
                                    Text(
                                        when {
                                            result.passedWithoutGrade -> "ناجح دون علامة"
                                            result.finalGrade != null -> "${formatGrade(result.finalGrade)}/100"
                                            else -> "—"
                                        },
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (result.passedWithoutGrade) Color(0xFF146C38) else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            GradeStatus(result.isPassed, result.assistancePoints, result.passedWithoutGrade)
                        }
                        if (partial != null) {
                            Text(
                                partial.missingNotice,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "لا تظهر حالة النجاح أو الرسوب حتى تكتمل جميع الدرجات المطلوبة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (result.rawGrade != null && result.roundedGrade != null && result.rawGrade != result.roundedGrade) {
                            Text(
                                "المحصلة قبل الجبر: ${formatGrade(result.rawGrade)} • بعد جبر الكسر: ${formatGrade(result.roundedGrade)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (result.assistancePoints > 0) {
                            Text(
                                "تم منح مساعدة +${result.assistancePoints} للوصول إلى حد النجاح.",
                                color = Color(0xFF146C38),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text("حد النجاح: ${program.passingGrade.toInt()}/100", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (isDiplomacy && course.code.isNotBlank()) {
                item {
                    AcademicNoticeCard(
                        "رقم المقرر ${course.code} مثبت تلقائيًا وفق منهاج الدراسات الدولية والدبلوماسية، ولا يحتاج إلى إدخال أو تعديل يدوي."
                    )
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
                        InteractiveButton(
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
    highSchoolGrades: List<HighSchoolGradeEntity>,
    onHome: () -> Unit,
    onProgram: (Long) -> Unit,
    onHighSchool: () -> Unit
) {
    val totalGraded = courses.count { c -> programs.firstOrNull { it.id == c.programId }?.let { GradeCalculator.calculate(c, it).finalGrade != null } == true }
    val totalPassed = courses.count { c -> programs.firstOrNull { it.id == c.programId }?.let { GradeCalculator.calculate(c, it).isPassed == true } == true }
    val totalFailed = courses.count { c -> programs.firstOrNull { it.id == c.programId }?.let { GradeCalculator.calculate(c, it).isPassed == false } == true }
    val scientificGrades = highSchoolGrades.filter { it.branch == HighSchoolSeedData.SCIENTIFIC_2016 }
    val literaryGrades = highSchoolGrades.filter { it.branch == HighSchoolSeedData.LITERARY_2026 }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("الإحصائيات", fontWeight = FontWeight.Bold) }) },
        bottomBar = { RootNavigationBar(false, onHome = onHome, onStatistics = {}) }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("الملخص العام", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${universities.size} جامعات • ${programs.size} برامج • ${courses.size} مقرر • الثانوية العامة")
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
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("بطاقات الإحصائيات", "البرامج والشهادة الثانوية ضمن شبكة مصوّرة من عمودين")
            }
            item {
                HighSchoolStatisticsCard(
                    scientificPercentage = highSchoolPercentage(scientificGrades),
                    literaryPercentage = highSchoolPercentage(literaryGrades),
                    enteredGrades = highSchoolGrades.count { it.grade != null },
                    totalSubjects = highSchoolGrades.size,
                    onClick = onHighSchool
                )
            }
            items(programs, key = { it.id }) { p ->
                val pc = courses.filter { it.programId == p.id }
                val grades = pc.mapNotNull { GradeCalculator.calculate(it, p).finalGrade }
                val passed = pc.count { GradeCalculator.calculate(it, p).isPassed == true }
                val failed = pc.count { GradeCalculator.calculate(it, p).isPassed == false }
                StatisticsProgramCard(
                    program = p,
                    courseCount = pc.size,
                    gradedCount = grades.size,
                    passedCount = passed,
                    failedCount = failed,
                    average = GradeCalculator.average(pc, p),
                    onClick = { onProgram(p.id) }
                )
            }
        }
    }
}

@Composable
private fun StatisticsProgramCard(
    program: ProgramEntity,
    courseCount: Int,
    gradedCount: Int,
    passedCount: Int,
    failedCount: Int,
    average: Double?,
    onClick: () -> Unit
) {
    val progress = if (courseCount == 0) 0f else passedCount.toFloat() / courseCount.toFloat()
    InteractiveElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().height(140.dp)) {
                Image(
                    painter = painterResource(programImage(program.name)),
                    contentDescription = "إحصائيات ${program.name}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xEB172235)), startY = 45f)
                    )
                )
                Text(
                    program.name,
                    modifier = Modifier.align(Alignment.BottomStart).padding(11.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("$gradedCount/$courseCount مُقيّم", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    Text(
                        average?.let { "${formatGrade(it)}%" } ?: "—",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text(
                    "$passedCount ناجح • $failedCount راسب • ${courseCount - passedCount - failedCount} غير مُقيّم",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun HighSchoolStatisticsCard(
    scientificPercentage: Double,
    literaryPercentage: Double,
    enteredGrades: Int,
    totalSubjects: Int,
    onClick: () -> Unit
) {
    InteractiveElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 292.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().height(128.dp)) {
                Image(
                    painter = painterResource(R.drawable.home_high_school),
                    contentDescription = "إحصائيات الشهادة الثانوية",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xEB172235)), startY = 45f)
                    )
                )
                Text(
                    "الشهادة الثانوية",
                    modifier = Modifier.align(Alignment.BottomStart).padding(11.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$enteredGrades/$totalSubjects درجة مدخلة", style = MaterialTheme.typography.labelSmall)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        PercentageRing(scientificPercentage, Modifier.size(58.dp))
                        Text("العلمي 2016", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        PercentageRing(literaryPercentage, Modifier.size(58.dp))
                        Text("الأدبي 2026", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
                Text("اضغط لعرض تفاصيل الفرعين", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    InteractiveTextButton(onClick = onBack) { Text("‹ رجوع", fontWeight = FontWeight.Bold) }
}

@Composable
private fun AcademicNoticeCard(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
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
        Column(Modifier.padding(vertical = 10.dp, horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            NumericMetricValue(value)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun NumericMetricValue(value: String) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            text = value,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

private fun partialEnteredText(partial: PartialGradePreview): String =
    partial.entered.joinToString(" • ") { component ->
        "${component.label}: ${formatGrade(component.value)}"
    }

@Composable
private fun GradeStatus(status: Boolean?, assistancePoints: Int = 0, passedWithoutGrade: Boolean = false) {
    val text = when {
        status == true && passedWithoutGrade -> "ناجح دون علامة"
        status == true && assistancePoints > 0 -> "ناجح • مساعدة +$assistancePoints"
        status == true -> "ناجح"
        status == false -> "راسب"
        else -> "غير مُقيّم"
    }
    val container = when (status) {
        true -> Color(0xFFDDF7E6)
        false -> Color(0xFFFFE3E1)
        null -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (status) {
        true -> Color(0xFF146C38)
        false -> Color(0xFFB3261E)
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = container, shape = RoundedCornerShape(50)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = content
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(18.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatGrade(value: Double): String = String.format(Locale.US, "%.2f", value)
private fun reportPdfFileName(label: String): String {
    val safeLabel = label
        .replace(Regex("[\\\\/:*?\"<>|]+"), "-")
        .replace(Regex("\\s+"), "-")
        .trim('-')
        .take(72)
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    return "AcademicJourney-$safeLabel-$stamp.pdf"
}
private fun courseIdentifierLabel(program: ProgramEntity): String =
    if (!DiplomacyCurriculum.isProgramme(program.name) && (
        program.gradingScheme == GradeCalculator.SVU_WEIGHTED ||
        program.gradingScheme == GradeCalculator.SINGLE_FINAL_GRADE
    )) "رمز المقرر" else "رقم المقرر"
private fun arabicOrdinal(year: Int): String = when (year) { 1 -> "الأولى"; 2 -> "الثانية"; 3 -> "الثالثة"; 4 -> "الرابعة"; 5 -> "الخامسة"; else -> year.toString() }


@Composable
private fun AcademicProgressRing(
    passed: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val percentage = if (total > 0) passed.toDouble() * 100.0 / total.toDouble() else 0.0
    PercentageRing(percentage = percentage, modifier = modifier)
}

@Composable
private fun PercentageRing(
    percentage: Double,
    modifier: Modifier = Modifier
) {
    val progress = (percentage / 100.0).toFloat().coerceIn(0f, 1f)
    val percent = percentage.coerceIn(0.0, 100.0).roundToInt()
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary
    Box(modifier = modifier.size(88.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = 9.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
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
