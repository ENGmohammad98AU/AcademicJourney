package com.academicjourney.app.domain

import com.academicjourney.app.data.CourseEntity
import com.academicjourney.app.data.ProgramEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicCelebrationTests {
    private val program = ProgramEntity(
        id = 1,
        universityId = 1,
        name = "ماجستير التأهيل والتخصص في علوم الحاسوب",
        degreeType = "ماجستير",
        gradingScheme = GradeCalculator.SVU_WEIGHTED,
        assignmentWeight = 40.0,
        examWeight = 60.0,
        passingGrade = 60.0
    )

    @Test
    fun newlyPassedCourseGetsCourseSuccessCelebration() {
        val courses = (1L..8L).map { course(it, passed = false) }
        val original = courses.first()
        val updated = original.copy(assignmentGrade = 80.0, examGrade = 80.0)

        val event = AcademicCelebrationDetector.detect(
            universityName = "الجامعة الافتراضية السورية",
            program = program,
            courses = courses,
            originalCourse = original,
            updatedCourse = updated
        )

        assertTrue(event is AcademicCelebration.CourseSuccess)
        assertEquals(original.name, (event as AcademicCelebration.CourseSuccess).courseName)
    }

    @Test
    fun crossingYearThresholdGetsYearCelebrationInsteadOfCourseCelebration() {
        val courses = (1L..8L).map { id -> course(id, passed = id <= 5L) }
        val original = courses.first { it.id == 6L }
        val updated = original.copy(assignmentGrade = 100.0, examGrade = 100.0)

        val event = AcademicCelebrationDetector.detect(
            universityName = "الجامعة الافتراضية السورية",
            program = program,
            courses = courses,
            originalCourse = original,
            updatedCourse = updated
        )

        assertEquals(AcademicCelebration.YearPromotion(2), event)
    }

    @Test
    fun passingLastCourseGetsGraduationCelebrationWithHighestPriority() {
        val courses = (1L..3L).map { id -> course(id, passed = id <= 2L) }
        val original = courses.last()
        val updated = original.copy(assignmentGrade = 100.0, examGrade = 100.0)

        val event = AcademicCelebrationDetector.detect(
            universityName = "الجامعة الافتراضية السورية",
            program = program,
            courses = courses,
            originalCourse = original,
            updatedCourse = updated
        )

        assertEquals(AcademicCelebration.Graduation(program.name), event)
    }

    @Test
    fun editingAnAlreadyPassedCourseDoesNotRepeatCelebration() {
        val original = course(1, passed = true)
        val event = AcademicCelebrationDetector.detect(
            universityName = "الجامعة الافتراضية السورية",
            program = program,
            courses = listOf(original, course(2, passed = false)),
            originalCourse = original,
            updatedCourse = original.copy(assignmentGrade = 90.0, examGrade = 90.0)
        )

        assertNull(event)
    }

    @Test
    fun latakiaTranslationPromotionUsesTheDistinctYearCelebration() {
        val translation = program.copy(
            name = "الترجمة في اللغة الإنكليزية – التعليم المفتوح",
            degreeType = "إجازة",
            gradingScheme = GradeCalculator.SINGLE_FINAL_GRADE,
            assignmentWeight = 0.0,
            examWeight = 0.0,
            passingGrade = 50.0
        )
        val courses = (1L..24L).map { id ->
            CourseEntity(
                id = id,
                programId = translation.id,
                name = "مقرر $id",
                academicYear = if (id <= 12) 1 else 2,
                semester = 1,
                directGrade = 50.0.takeIf { id <= 7 }
            )
        }
        val original = courses.first { it.id == 8L }

        val event = AcademicCelebrationDetector.detect(
            universityName = "جامعة اللاذقية",
            program = translation,
            courses = courses,
            originalCourse = original,
            updatedCourse = original.copy(directGrade = 50.0)
        )

        assertEquals(AcademicCelebration.YearPromotion(2), event)
    }

    private fun course(id: Long, passed: Boolean) = CourseEntity(
        id = id,
        programId = program.id,
        name = "مقرر $id",
        academicYear = if (id <= 6) 1 else 2,
        semester = 1,
        assignmentGrade = if (passed) 100.0 else null,
        examGrade = if (passed) 100.0 else null
    )
}
