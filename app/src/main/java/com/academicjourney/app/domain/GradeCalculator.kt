package com.academicjourney.app.domain

import com.academicjourney.app.data.CourseEntity
import com.academicjourney.app.data.ProgramEntity

data class GradeResult(val finalGrade: Double?, val isPassed: Boolean?)

object GradeCalculator {
    const val SVU_WEIGHTED = "SVU_WEIGHTED"
    const val PRACTICAL_THEORY = "PRACTICAL_THEORY"
    const val ANDALUS_SPLIT_PRACTICAL_THEORY = "ANDALUS_SPLIT_PRACTICAL_THEORY"

    fun calculate(course: CourseEntity, program: ProgramEntity): GradeResult {
        val finalGrade = when (program.gradingScheme) {
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
        return GradeResult(finalGrade, finalGrade >= program.passingGrade)
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

    fun semesterAverage(courses: List<CourseEntity>, program: ProgramEntity): Double? =
        courses.mapNotNull { calculate(it, program).finalGrade }
            .takeIf { it.isNotEmpty() }
            ?.average()

    private fun valid(value: Double): Boolean = value in 0.0..100.0
}
