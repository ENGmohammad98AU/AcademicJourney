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
    fun subjectPercentageIsCalculatedIndependentlyForEachHighSchoolSubject() {
        val entered = highSchool("الفيزياء", 400, true, 300)
        val missing = entered.copy(subject = "الكيمياء", maxGrade = 200, grade = null)

        assertEquals(75.0, HighSchoolCalculator.subjectPercentage(entered) ?: 0.0, 0.001)
        assertNull(HighSchoolCalculator.subjectPercentage(missing))
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

    @Test
    fun everyUniversityFractionIsRoundedUp() {
        val weighted = program(GradeCalculator.SVU_WEIGHTED, 20.0, 80.0, 50.0)
        val result = GradeCalculator.calculate(
            course(assignmentGrade = 76.5, examGrade = 76.0),
            weighted
        )

        assertEquals(76.1, result.rawGrade ?: 0.0, 0.001)
        assertEquals(77.0, result.roundedGrade ?: 0.0, 0.001)
        assertEquals(77.0, result.finalGrade ?: 0.0, 0.001)
    }

    @Test
    fun mediaAndHumanResourcesReceiveOnlyNeededAssistanceUpToTwoPoints() {
        val media = program(
            GradeCalculator.SVU_WEIGHTED,
            assignment = 20.0,
            exam = 80.0,
            passing = 50.0,
            name = "الإعلام والاتصال"
        )
        val onePoint = GradeCalculator.calculate(course(assignmentGrade = 49.0, examGrade = 49.0), media)
        val twoPoints = GradeCalculator.calculate(course(assignmentGrade = 48.0, examGrade = 48.0), media)
        val outsideLimit = GradeCalculator.calculate(course(assignmentGrade = 47.0, examGrade = 47.0), media)

        assertEquals(1, onePoint.assistancePoints)
        assertEquals(50.0, onePoint.finalGrade ?: 0.0, 0.001)
        assertTrue(onePoint.isPassed == true)
        assertEquals(2, twoPoints.assistancePoints)
        assertEquals(50.0, twoPoints.finalGrade ?: 0.0, 0.001)
        assertTrue(twoPoints.isPassed == true)
        assertEquals(0, outsideLimit.assistancePoints)
        assertEquals(47.0, outsideLimit.finalGrade ?: 0.0, 0.001)
        assertFalse(outsideLimit.isPassed == true)

        val humanResources = program(
            GradeCalculator.SVU_WEIGHTED,
            assignment = 25.0,
            exam = 75.0,
            passing = 60.0,
            name = "علوم الإدارة – إدارة الموارد البشرية"
        )
        val hrResult = GradeCalculator.calculate(course(assignmentGrade = 59.0, examGrade = 59.0), humanResources)
        assertEquals(1, hrResult.assistancePoints)
        assertEquals(60.0, hrResult.finalGrade ?: 0.0, 0.001)
    }

    @Test
    fun assistanceDoesNotApplyToSvuMasters() {
        val master = program(
            GradeCalculator.SVU_WEIGHTED,
            assignment = 30.0,
            exam = 70.0,
            passing = 60.0,
            name = "ماجستير التأهيل والتخصص في إدارة الأعمال"
        )
        val result = GradeCalculator.calculate(course(assignmentGrade = 59.0, examGrade = 59.0), master)

        assertEquals(0, result.assistancePoints)
        assertEquals(59.0, result.finalGrade ?: 0.0, 0.001)
        assertFalse(result.isPassed == true)
    }

    @Test
    fun weightedAverageUsesCreditHours() {
        val weighted = program(GradeCalculator.SVU_WEIGHTED, 20.0, 80.0, 50.0)
        val courses = listOf(
            course(id = 1, assignmentGrade = 100.0, examGrade = 100.0, creditHours = 6),
            course(id = 2, assignmentGrade = 50.0, examGrade = 50.0, creditHours = 3)
        )

        assertEquals(83.333, GradeCalculator.average(courses, weighted) ?: 0.0, 0.01)
    }

    @Test
    fun recognizedPassCountsAsSuccessWithoutEnteringTheAverage() {
        val weighted = program(GradeCalculator.SVU_WEIGHTED, 20.0, 80.0, 60.0)
        val recognized = course(id = 1, creditHours = 5, passedWithoutGrade = true)
        val graded = course(id = 2, assignmentGrade = 80.0, examGrade = 80.0, creditHours = 5)

        val recognizedResult = GradeCalculator.calculate(recognized, weighted)

        assertTrue(recognizedResult.isPassed == true)
        assertTrue(recognizedResult.passedWithoutGrade)
        assertNull(recognizedResult.finalGrade)
        assertEquals(80.0, GradeCalculator.average(listOf(recognized, graded), weighted) ?: 0.0, 0.001)
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

    private fun program(
        scheme: String,
        assignment: Double,
        exam: Double,
        passing: Double,
        name: String = "اختبار"
    ) =
        ProgramEntity(
            id = 1,
            universityId = 1,
            name = name,
            gradingScheme = scheme,
            assignmentWeight = assignment,
            examWeight = exam,
            passingGrade = passing
        )

    private fun course(
        id: Long = 1,
        practicalGrade: Double? = null,
        theoryGrade: Double? = null,
        assignmentGrade: Double? = null,
        examGrade: Double? = null,
        studentWorkGrade: Double? = null,
        practicalExamGrade: Double? = null,
        creditHours: Int? = null,
        academicYear: Int = 1,
        semester: Int = 1,
        passedWithoutGrade: Boolean = false
    ) = CourseEntity(
        id = id,
        programId = 1,
        name = "اختبار",
        academicYear = academicYear,
        semester = semester,
        practicalGrade = practicalGrade,
        theoryGrade = theoryGrade,
        assignmentGrade = assignmentGrade,
        examGrade = examGrade,
        studentWorkGrade = studentWorkGrade,
        practicalExamGrade = practicalExamGrade,
        creditHours = creditHours,
        passedWithoutGrade = passedWithoutGrade
    )
}
