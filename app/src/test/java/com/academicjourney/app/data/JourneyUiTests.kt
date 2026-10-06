package com.academicjourney.app.data

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.academicjourney.app.domain.GradeCalculator
import com.academicjourney.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JourneyUiTests {
    @get:Rule val compose = createComposeRule()
    private val p = ProgramEntity(2, 1, "برنامج الاختبار", gradingScheme = GradeCalculator.PRACTICAL_THEORY, passingGrade = 50.0)
    private val c = CourseEntity(3, 2, "مادة الاختبار", "101", academicYear = 1, semester = 1, practicalGrade = 20.0)

    @Test fun partialGradeStaysVisibleAndDraftSurvivesCollapse() {
        val vm = AcademicViewModel(ApplicationProvider.getApplicationContext<Application>())
        var saved: CourseEntity? = null
        compose.setContent {
            AcademicJourneyTheme {
                var expanded by remember { mutableStateOf(false) }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CourseAccordion(c, p, expanded, { expanded = !expanded }, { saved = it }, vm, {})
                }
            }
        }
        compose.onNodeWithText("لم يتم إدخال درجة النظري بعد.").assertExists()
        compose.onNodeWithText(c.name).performClick()
        compose.onNode(hasSetTextAction() and hasText("درجة النظري")).performScrollTo().performTextInput("60")
        compose.onNodeWithText(c.name).performScrollTo().performClick()
        compose.onNodeWithText(c.name).performClick()
        compose.onNode(hasSetTextAction() and hasText("درجة النظري")).assertTextContains("60")
        compose.onNodeWithText("تحديث الدرجات المتاحة").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(60.0, saved?.theoryGrade); assertEquals(20.0, saved?.practicalGrade) }
    }

    @Test fun dashboardRendersFourNavigationDestinations() {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl
            ) {
                AcademicJourneyTheme {
                    JourneyDashboard(listOf(UniversityEntity(1, "جامعة الاختبار")), listOf(p), listOf(c), emptyList(),
                        p.id, 0, {}, {}, {}, {}, {})
                }
            }
        }
        listOf("الرئيسية", "فصلي الحالي", "الإحصائيات", "المزيد").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText(p.name).assertExists()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File("build/reports/journey-ui/dashboard.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
