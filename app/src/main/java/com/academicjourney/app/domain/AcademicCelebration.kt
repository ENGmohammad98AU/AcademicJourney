package com.academicjourney.app.domain

import com.academicjourney.app.data.CourseEntity
import com.academicjourney.app.data.ProgramEntity

sealed interface AcademicCelebration {
    data class CoursePromotion(val courseName: String) : AcademicCelebration
    data class YearPromotion(val year: Int) : AcademicCelebration
    data class Graduation(val programName: String) : AcademicCelebration
}

/** Detects only a new academic achievement caused by the grade being saved. */
object AcademicCelebrationDetector {
    fun detect(
        universityName: String,
        program: ProgramEntity,
        courses: List<CourseEntity>,
        originalCourse: CourseEntity,
        updatedCourse: CourseEntity
    ): AcademicCelebration? {
        if (originalCourse.id != updatedCourse.id || originalCourse.programId != updatedCourse.programId) {
            return null
        }

        val wasPassed = GradeCalculator.calculate(originalCourse, program).isPassed == true
        val isNowPassed = GradeCalculator.calculate(updatedCourse, program).isPassed == true
        if (wasPassed || !isNowPassed) return null

        val before = StudentStandingCalculator.calculate(universityName, program, courses)
        val afterCourses = courses.map { course ->
            if (course.id == updatedCourse.id) updatedCourse else course
        }
        val after = StudentStandingCalculator.calculate(universityName, program, afterCourses)

        return when {
            !before.isGraduated && after.isGraduated ->
                AcademicCelebration.Graduation(program.name)
            before.currentYear != null &&
                after.currentYear != null &&
                after.currentYear > before.currentYear ->
                AcademicCelebration.YearPromotion(after.currentYear)
            else -> AcademicCelebration.CoursePromotion(updatedCourse.name)
        }
    }
}
