package com.academicjourney.app.domain

import com.academicjourney.app.data.CourseEntity
import com.academicjourney.app.data.ProgramEntity
import kotlin.math.ceil

data class GradeResult(
    val finalGrade: Double?,
    val isPassed: Boolean?,
    val rawGrade: Double? = finalGrade,
    val roundedGrade: Double? = finalGrade,
    val assistancePoints: Int = 0,
    val passedWithoutGrade: Boolean = false
) {
    val receivedAssistance: Boolean get() = assistancePoints > 0
}

object GradeCalculator {
    const val SVU_WEIGHTED = "SVU_WEIGHTED"
    const val PRACTICAL_THEORY = "PRACTICAL_THEORY"
    const val ANDALUS_SPLIT_PRACTICAL_THEORY = "ANDALUS_SPLIT_PRACTICAL_THEORY"

    fun calculate(course: CourseEntity, program: ProgramEntity): GradeResult {
        if (course.passedWithoutGrade) {
            return GradeResult(
                finalGrade = null,
                isPassed = true,
                rawGrade = null,
                roundedGrade = null,
                passedWithoutGrade = true
            )
        }
        val rawGrade = when (program.gradingScheme) {
            SVU_WEIGHTED -> {
                val assignment = course.assignmentGrade ?: return GradeResult(null, null)
                val exam = course.examGrade ?: return GradeResult(null, null)
                if (!valid(assignment) || !valid(exam)) return GradeResult(null, null)
                assignment * program.assignmentWeight / 100.0 +
                    exam * program.examWeight / 100.0
            }

            PRACTICAL_THEORY -> {
                val practical = course.practicalGrade ?: return GradeResult(null, null)
                val theory = course.theoryGrade ?: return GradeResult(null, null)
                if (validatePracticalTheory(practical, theory) != null) return GradeResult(null, null)
                practical + theory
            }

            ANDALUS_SPLIT_PRACTICAL_THEORY -> {
                // Old installations stored the complete practical result in practicalGrade.
                val studentWork = course.studentWorkGrade
                    ?: course.practicalGrade
                    ?: return GradeResult(null, null)
                val practicalExam = course.practicalExamGrade
                    ?: if (course.practicalGrade != null) 0.0 else return GradeResult(null, null)
                val theory = course.theoryGrade ?: return GradeResult(null, null)
                if (validateAndalus(studentWork, practicalExam, theory) != null) {
                    return GradeResult(null, null)
                }
                studentWork + practicalExam + theory
            }

            else -> return GradeResult(null, null)
        }
        val roundedGrade = roundUniversityGrade(rawGrade)
        val assistancePoints = assistancePointsFor(roundedGrade, program)
        val finalGrade = (roundedGrade + assistancePoints).coerceAtMost(100.0)
        return GradeResult(
            finalGrade = finalGrade,
            isPassed = finalGrade >= program.passingGrade,
            rawGrade = rawGrade,
            roundedGrade = roundedGrade,
            assistancePoints = assistancePoints
        )
    }

    /** University fractions are always promoted to the next whole grade. */
    fun roundUniversityGrade(value: Double): Double = ceil(value - 1e-9).coerceIn(0.0, 100.0)

    /**
     * SVU assistance is limited to the Media and Human Resources programs.
     * It is granted only when one or two points are enough to reach the pass mark.
     */
    fun maximumAssistance(program: ProgramEntity): Int {
        if (program.gradingScheme != SVU_WEIGHTED) return 0
        return if (
            program.name.contains("الإعلام والاتصال") ||
            program.name.contains("إدارة الموارد البشرية")
        ) 2 else 0
    }

    private fun assistancePointsFor(roundedGrade: Double, program: ProgramEntity): Int {
        val limit = maximumAssistance(program)
        if (limit == 0 || roundedGrade >= program.passingGrade) return 0
        val needed = ceil(program.passingGrade - roundedGrade).toInt()
        return needed.takeIf { it in 1..limit } ?: 0
    }

    fun validatePracticalTheory(practical: Double, theory: Double): String? = when {
        !valid(practical) || !valid(theory) -> "يجب أن تكون كل درجة بين 0 و100."
        practical + theory > 100 -> "يجب أن يكون المجموع النهائي بين 0 و100."
        else -> null
    }

    fun validateAndalus(studentWork: Double, practicalExam: Double, theory: Double): String? = when {
        !valid(studentWork) || !valid(practicalExam) || !valid(theory) ->
            "يجب أن تكون كل درجة بين 0 و100."
        studentWork + practicalExam + theory > 100 ->
            "يجب أن يكون مجموع أعمال الطالب والامتحان العملي والنظري بين 0 و100."
        else -> null
    }

    fun validateSvu(assignment: Double, exam: Double): String? =
        if (!valid(assignment) || !valid(exam)) "يجب أن تكون كل درجة بين 0 و100." else null

    /** Uses credit-hour weighting when every graded course has supplied credit hours. */
    fun average(courses: List<CourseEntity>, program: ProgramEntity): Double? {
        val graded = courses.mapNotNull { course ->
            calculate(course, program).finalGrade?.let { grade -> course to grade }
        }
        if (graded.isEmpty()) return null

        val canWeight = graded.all { (course, _) -> (course.creditHours ?: 0) > 0 }
        if (!canWeight) return graded.map { it.second }.average()

        val totalHours = graded.sumOf { (course, _) -> requireNotNull(course.creditHours) }
        return graded.sumOf { (course, grade) -> grade * requireNotNull(course.creditHours) } / totalHours
    }

    fun semesterAverage(courses: List<CourseEntity>, program: ProgramEntity): Double? =
        average(courses, program)

    private fun valid(value: Double): Boolean = value in 0.0..100.0
}
