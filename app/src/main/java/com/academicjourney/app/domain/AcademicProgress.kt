package com.academicjourney.app.domain

import com.academicjourney.app.data.CourseEntity
import com.academicjourney.app.data.ProgramEntity

data class AcademicProgress(
    val passedCourses: Int,
    val totalCourses: Int
) {
    val ratio: Float
        get() = if (totalCourses == 0) 0f else passedCourses.toFloat() / totalCourses.toFloat()

    val percentage: Float
        get() = ratio * 100f
}

/**
 * Success is derived only from grades entered by the user and each program's passing grade.
 * No imported checkbox/"ناجح" field is used.
 */
fun calculateAcademicProgress(
    courses: List<CourseEntity>,
    programs: Map<Long, ProgramEntity>
): AcademicProgress {
    var passed = 0
    courses.forEach { course ->
        val program = programs[course.programId] ?: return@forEach
        val result = GradeCalculator.calculate(course, program)
        if (result.isPassed == true) passed++
    }
    return AcademicProgress(passedCourses = passed, totalCourses = courses.size)
}
