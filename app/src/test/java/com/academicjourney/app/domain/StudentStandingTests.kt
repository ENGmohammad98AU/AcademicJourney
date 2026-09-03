package com.academicjourney.app.domain

import com.academicjourney.app.data.CourseEntity
import com.academicjourney.app.data.ProgramEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentStandingTests {
    @Test
    fun fourRemainingRulePromotesStudentToNextYear() {
        val program = program(name = "الهندسة الطبية", scheme = GradeCalculator.PRACTICAL_THEORY, passing = 60.0)
        val firstYear = (1..12).map { course(it.toLong(), 1, passed = it <= 8) }
        val secondYear = (13..22).map { course(it.toLong(), 2, passed = false) }

        val standing = StudentStandingCalculator.calculate(
            universityName = "جامعة الأندلس الخاصة للعلوم الطبية",
            program = program,
            courses = firstYear + secondYear
        )

        assertEquals(2, standing.currentYear)
        assertFalse(standing.isGraduated)
    }

    @Test
    fun mediaUsesSpecifiedCumulativeCourseThresholds() {
        val program = program(name = "الإعلام والاتصال")
        val courses = (1..40).map { index ->
            course(index.toLong(), ((index - 1) / 10) + 1, passed = index <= 33, svu = true)
        }

        val standing = StudentStandingCalculator.calculate("الجامعة الافتراضية السورية", program, courses)

        assertEquals(4, standing.currentYear)
        assertEquals(33, standing.passedCourses)
    }

    @Test
    fun humanResourcesUsesEarnedCreditHours() {
        val program = program(name = "علوم الإدارة – إدارة الموارد البشرية", passing = 60.0)
        val courses = (1..12).map { index ->
            course(index.toLong(), if (index <= 9) 1 else 2, passed = index <= 9, svu = true, hours = 5)
        }

        val standing = StudentStandingCalculator.calculate("الجامعة الافتراضية السورية", program, courses)

        assertEquals(45, standing.earnedCreditHours)
        assertEquals(2, standing.currentYear)
    }

    @Test
    fun mastersAdvanceAfterSixPassedCourses() {
        val program = program(name = "ماجستير التأهيل والتخصص", degreeType = "ماجستير", passing = 60.0)
        val courses = (1..10).map { index ->
            course(index.toLong(), if (index <= 6) 1 else 2, passed = index <= 6, svu = true)
        }

        val standing = StudentStandingCalculator.calculate("الجامعة الافتراضية السورية", program, courses)

        assertEquals(2, standing.currentYear)
    }

    @Test
    fun passingEveryCourseAlwaysProducesGraduatedStatus() {
        val program = program(name = "الإعلام والاتصال")
        val courses = (1..5).map { index -> course(index.toLong(), 1, passed = true, svu = true) }

        val standing = StudentStandingCalculator.calculate("الجامعة الافتراضية السورية", program, courses)

        assertTrue(standing.isGraduated)
        assertEquals("متخرج", standing.title)
    }

    private fun program(
        name: String,
        scheme: String = GradeCalculator.SVU_WEIGHTED,
        passing: Double = 50.0,
        degreeType: String = "إجازة"
    ) = ProgramEntity(
        id = 1,
        universityId = 1,
        name = name,
        degreeType = degreeType,
        gradingScheme = scheme,
        assignmentWeight = if (scheme == GradeCalculator.SVU_WEIGHTED) 20.0 else 0.0,
        examWeight = if (scheme == GradeCalculator.SVU_WEIGHTED) 80.0 else 0.0,
        passingGrade = passing
    )

    private fun course(
        id: Long,
        year: Int,
        passed: Boolean,
        svu: Boolean = false,
        hours: Int? = null
    ) = CourseEntity(
        id = id,
        programId = 1,
        name = "مقرر $id",
        academicYear = year,
        semester = 1,
        practicalGrade = if (!svu && passed) 30.0 else null,
        theoryGrade = if (!svu && passed) 30.0 else null,
        assignmentGrade = if (svu && passed) 100.0 else null,
        examGrade = if (svu && passed) 100.0 else null,
        creditHours = hours
    )
}
