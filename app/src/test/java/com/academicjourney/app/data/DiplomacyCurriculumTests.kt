package com.academicjourney.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import com.academicjourney.app.domain.GradeCalculator
import com.academicjourney.app.domain.PartialGradePreviewBuilder

class DiplomacyCurriculumTests {
    private val programme = ProgramEntity(2, 1, "الدراسات الدولية والدبلوماسية – التعليم المفتوح",
        gradingScheme = DiplomacyCurriculum.GRADING_SCHEME, passingGrade = DiplomacyCurriculum.PASSING_GRADE)

    @Test fun legacyCompleteGradesRetainTheirResultAndPartialGradesStayIncomplete() {
        val old = CourseEntity(3, 2, "مقرر", "510", academicYear = 1, semester = 1,
            practicalGrade = 20.0, theoryGrade = 56.1, notes = "ملاحظة")
        val upgraded = DiplomacyCurriculum.upgradeLegacyCourse(old)
        assertEquals(76.1, upgraded.directGrade!!, 0.0001)
        assertEquals(77.0, GradeCalculator.calculate(upgraded, programme).finalGrade!!, 0.0)
        assertEquals(old.practicalGrade, upgraded.practicalGrade)
        assertEquals(old.notes, upgraded.notes)
        val partial = DiplomacyCurriculum.upgradeLegacyCourse(old.copy(practicalGrade = null, theoryGrade = 60.0))
        assertNull(partial.directGrade)
        assertNull(GradeCalculator.calculate(partial, programme).isPassed)
        val preview = PartialGradePreviewBuilder.forCourse(partial, programme)!!
        assertEquals(60.0, preview.entered.single().value, 0.0)
        assertEquals(listOf("الدرجة النهائية"), preview.missingLabels)
        assertNull(DiplomacyCurriculum.legacyTotal(80.0, 30.0))
    }

    @Test fun directGradeControlsResultAndNeverFallsBackAfterItIsCleared() {
        val c = CourseEntity(3, 2, "مقرر", "510", academicYear = 1, semester = 1,
            practicalGrade = 20.0, theoryGrade = 60.0, directGrade = 49.0)
        assertFalse(GradeCalculator.calculate(c, programme).isPassed!!)
        assertTrue(GradeCalculator.calculate(c.copy(directGrade = 49.1), programme).isPassed!!)
        assertEquals(0, GradeCalculator.calculate(c, programme).assistancePoints)
        assertNull(GradeCalculator.calculate(c.copy(directGrade = null), programme).finalGrade)
        assertEquals(49.0, DiplomacyCurriculum.upgradeLegacyCourse(c).directGrade!!, 0.0)
    }

    @Test
    fun officialWorkbookNumbersCoverEveryCourseExactlyOnce() {
        val numbers = DiplomacyCurriculum.courseNumberByName.values.map(String::toInt)

        assertEquals(48, DiplomacyCurriculum.courseNumberByName.size)
        assertEquals(48, numbers.distinct().size)
        assertEquals((510..557).toList(), numbers.sorted())
    }

    @Test
    fun representativeNumbersMatchTheSuppliedWorkbook() {
        assertEquals("510", DiplomacyCurriculum.numberFor("مدخل إلى علم القانون"))
        assertEquals("534", DiplomacyCurriculum.numberFor("نظرية العلاقات الدولية"))
        assertEquals("557", DiplomacyCurriculum.numberFor("الجغرافيا السياسية"))
        assertTrue(DiplomacyCurriculum.isProgramme("الدراسات الدولية والدبلوماسية – التعليم المفتوح"))
    }
}
