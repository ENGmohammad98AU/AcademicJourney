package com.academicjourney.app.domain

import com.academicjourney.app.data.CourseEntity
import com.academicjourney.app.data.ProgramEntity
import java.util.Locale

/**
 * The MBA and MCS projects are assessed with one direct project grade rather
 * than the regular SVU assignment/exam pair.
 *
 * Existing installations may still contain both legacy components. Until the
 * user saves the course again, expose their old weighted result as the single
 * project grade so updating the app never changes a stored academic result.
 */
object ProjectGradePolicy {
    private val singleGradeCodes = setOf("MPR", "PRJ.40")

    fun usesSingleProjectGrade(course: CourseEntity, program: ProgramEntity): Boolean =
        program.gradingScheme == GradeCalculator.SVU_WEIGHTED &&
            normalizedCode(course.code) in singleGradeCodes

    fun displayedGrade(course: CourseEntity, program: ProgramEntity): Double? {
        if (!usesSingleProjectGrade(course, program)) return null
        val assignment = course.assignmentGrade
        val exam = course.examGrade
        return when {
            assignment != null && exam != null ->
                assignment * program.assignmentWeight / 100.0 +
                    exam * program.examWeight / 100.0
            exam != null -> exam
            else -> assignment
        }
    }

    fun withProjectGrade(course: CourseEntity, grade: Double?): CourseEntity =
        course.copy(assignmentGrade = null, examGrade = grade)

    private fun normalizedCode(code: String): String =
        code.trim().uppercase(Locale.ROOT).replace(" ", "")
}
