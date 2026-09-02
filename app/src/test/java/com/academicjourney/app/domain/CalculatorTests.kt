package com.academicjourney.app.domain

import com.academicjourney.app.data.CourseEntity
import com.academicjourney.app.data.HighSchoolGradeEntity
import com.academicjourney.app.data.ProgramEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorTests {
    @Test
    fun scientificPercentageExcludesFrenchAndReligion() {
        val grades = listOf(
            highSchool("العربية", 400, true, 400),
            highSchool("الإنكليزية", 300, true, 300),
            highSchool("الفرنسية", 300, false, 0),
            highSchool("الوطنية", 200, true, 200),
            highSchool("الرياضيات", 600, true, 600),
            highSchool("الفيزياء", 400, true, 400),
            highSchool("الكيمياء", 200, true, 200),
            highSchool("الأحياء", 300, true, 300),
            highSchool("الديانة", 200, false, 0)
        )

        val result = HighSchoolCalculator.calculate(grades)

        assertEquals(2400, result.maximumGrade)
        assertEquals(2400, result.totalGrade)
        assertEquals(100.0, result.percentage, 0.001)
    }

    @Test
    fun literaryPercentageExcludesFrenchOnly() {
        val grades = listOf(
            highSchool("العربية", 600, true, 300),
            highSchool("الفلسفة", 400, true, 200),
            highSchool("التاريخ", 300, true, 150),
            highSchool("الجغرافيا", 300, true, 150),
            highSchool("الديانة", 200, true, 100),
            highSchool("الإنكليزية", 400, true, 200),
            highSchool("الفرنسية", 400, false, 400)
        )

        val result = HighSchoolCalculator.calculate(grades)

        assertEquals(2200, result.maximumGrade)
        assertEquals(1100, result.totalGrade)
        assertEquals(50.0, result.percentage, 0.001)
    }

    @Test
    fun universityGradeRulesHandleWeightedAndPracticalTheoryPrograms() {
        val weighted = program(GradeCalculator.SVU_WEIGHTED, 20.0, 80.0, 50.0)
        val weightedResult = GradeCalculator.calculate(
            course(assignmentGrade = 100.0, examGrade = 50.0),
            weighted
        )
        assertEquals(60.0, weightedResult.finalGrade ?: 0.0, 0.001)
        assertTrue(weightedResult.isPassed == true)

        val practicalTheory = program(GradeCalculator.PRACTICAL_THEORY, 0.0, 0.0, 60.0)
        val invalid = GradeCalculator.calculate(
            course(practicalGrade = 60.0, theoryGrade = 50.0),
            practicalTheory
        )
        assertNull(invalid.finalGrade)
        assertNull(invalid.isPassed)

        val failed = GradeCalculator.calculate(
            course(practicalGrade = 25.0, theoryGrade = 30.0),
            practicalTheory
        )
        assertFalse(failed.isPassed == true)
    }

    @Test
    fun andalusGradeAddsStudentWorkPracticalExamAndTheoryWithoutExceeding100() {
        val andalus = program(
            GradeCalculator.ANDALUS_SPLIT_PRACTICAL_THEORY,
            assignment = 0.0,
            exam = 0.0,
            passing = 60.0
        )
        val passed = GradeCalculator.calculate(
            course(studentWorkGrade = 20.0, practicalExamGrade = 20.0, theoryGrade = 30.0),
            andalus
        )
        assertEquals(70.0, passed.finalGrade ?: 0.0, 0.001)
        assertTrue(passed.isPassed == true)

        val invalid = GradeCalculator.calculate(
            course(studentWorkGrade = 40.0, practicalExamGrade = 30.0, theoryGrade = 31.0),
            andalus
        )
        assertNull(invalid.finalGrade)
        assertEquals(
            "يجب أن يكون مجموع أعمال الطالب والامتحان العملي والنظري بين 0 و100.",
            GradeCalculator.validateAndalus(40.0, 30.0, 31.0)
        )
    }

    private fun highSchool(name: String, maximum: Int, included: Boolean, grade: Int) =
        HighSchoolGradeEntity(
            branch = "test",
            subject = name,
            maxGrade = maximum,
            includedInPercentage = included,
            displayOrder = 0,
            grade = grade
        )

    private fun program(scheme: String, assignment: Double, exam: Double, passing: Double) =
        ProgramEntity(
            id = 1,
            universityId = 1,
            name = "اختبار",
            gradingScheme = scheme,
            assignmentWeight = assignment,
            examWeight = exam,
            passingGrade = passing
        )

    private fun course(
        practicalGrade: Double? = null,
        theoryGrade: Double? = null,
        assignmentGrade: Double? = null,
        examGrade: Double? = null,
        studentWorkGrade: Double? = null,
        practicalExamGrade: Double? = null
    ) = CourseEntity(
        id = 1,
        programId = 1,
        name = "اختبار",
        academicYear = 1,
        semester = 1,
        practicalGrade = practicalGrade,
        theoryGrade = theoryGrade,
        assignmentGrade = assignmentGrade,
        examGrade = examGrade,
        studentWorkGrade = studentWorkGrade,
        practicalExamGrade = practicalExamGrade
    )
}
