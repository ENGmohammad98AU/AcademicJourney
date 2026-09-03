package com.academicjourney.app.domain

import com.academicjourney.app.data.CourseEntity
import com.academicjourney.app.data.HumanResourcesCurriculum
import com.academicjourney.app.data.ProgramEntity

data class StudentStanding(
    val title: String,
    val details: String,
    val currentYear: Int?,
    val isGraduated: Boolean,
    val passedCourses: Int,
    val totalCourses: Int,
    val earnedCreditHours: Int = 0,
    val requiredCreditHours: Int? = null
)

object StudentStandingCalculator {
    fun calculate(
        universityName: String,
        program: ProgramEntity,
        courses: List<CourseEntity>
    ): StudentStanding {
        val passedCourses = courses.filter { GradeCalculator.calculate(it, program).isPassed == true }
        val passedCount = passedCourses.size
        val graduated = courses.isNotEmpty() && passedCount == courses.size
        val isHumanResources = program.name.contains("إدارة الموارد البشرية")
        val earnedHours = passedCourses.sumOf { it.creditHours ?: 0 }

        if (graduated) {
            return StudentStanding(
                title = "متخرج",
                details = if (isHumanResources) {
                    "اجتاز جميع المقررات وأكمل $earnedHours من ${HumanResourcesCurriculum.GRADUATION_HOURS} ساعة معتمدة."
                } else {
                    "اجتاز جميع مقررات البرنامج بنجاح."
                },
                currentYear = null,
                isGraduated = true,
                passedCourses = passedCount,
                totalCourses = courses.size,
                earnedCreditHours = earnedHours,
                requiredCreditHours = HumanResourcesCurriculum.GRADUATION_HOURS.takeIf { isHumanResources }
            )
        }

        if (isHumanResources) {
            val year = when {
                earnedHours >= HumanResourcesCurriculum.FOURTH_YEAR_HOURS -> 4
                earnedHours >= HumanResourcesCurriculum.THIRD_YEAR_HOURS -> 3
                earnedHours >= HumanResourcesCurriculum.SECOND_YEAR_HOURS -> 2
                else -> 1
            }
            val nextTarget = when (year) {
                1 -> HumanResourcesCurriculum.SECOND_YEAR_HOURS
                2 -> HumanResourcesCurriculum.THIRD_YEAR_HOURS
                3 -> HumanResourcesCurriculum.FOURTH_YEAR_HOURS
                else -> HumanResourcesCurriculum.GRADUATION_HOURS
            }
            return standing(
                year = year,
                details = "أنجز $earnedHours ساعة معتمدة؛ الحد التالي $nextTarget ساعة، والتخرج بعد اجتياز جميع المقررات (269 ساعة).",
                passedCount = passedCount,
                totalCount = courses.size,
                earnedHours = earnedHours,
                requiredHours = HumanResourcesCurriculum.GRADUATION_HOURS
            )
        }

        if (program.name.contains("الإعلام والاتصال")) {
            val year = when {
                passedCount >= 33 -> 4
                passedCount >= 20 -> 3
                passedCount >= 8 -> 2
                else -> 1
            }
            val nextTarget = when (year) {
                1 -> 8
                2 -> 20
                3 -> 33
                else -> courses.size
            }
            return standing(
                year = year,
                details = "اجتاز $passedCount مقررًا؛ الحد التالي $nextTarget مقررًا، والتخرج بعد اجتياز جميع المقررات.",
                passedCount = passedCount,
                totalCount = courses.size
            )
        }

        if (program.degreeType.contains("ماجستير")) {
            val year = if (passedCount >= 6) 2 else 1
            val details = if (year == 1) {
                "اجتاز $passedCount مقررات؛ الترفع إلى السنة الثانية بعد اجتياز 6 مقررات."
            } else {
                "اجتاز $passedCount مقررات وترفع إلى السنة الثانية؛ يصبح متخرجًا بعد اجتياز جميع المقررات."
            }
            return standing(year, details, passedCount, courses.size)
        }

        if (isFourRemainingUniversity(universityName)) {
            return calculateFourRemainingStanding(program, courses, passedCount)
        }

        val firstIncompleteYear = courses.groupBy { it.academicYear }
            .toSortedMap()
            .entries
            .firstOrNull { (_, yearCourses) ->
                yearCourses.any { GradeCalculator.calculate(it, program).isPassed != true }
            }
            ?.key ?: courses.maxOfOrNull { it.academicYear } ?: 1
        return standing(
            year = firstIncompleteYear,
            details = "اجتاز $passedCount من أصل ${courses.size} مقررًا.",
            passedCount = passedCount,
            totalCount = courses.size
        )
    }

    private fun calculateFourRemainingStanding(
        program: ProgramEntity,
        courses: List<CourseEntity>,
        passedCount: Int
    ): StudentStanding {
        val years = courses.map { it.academicYear }.distinct().sorted()
        if (years.isEmpty()) return standing(1, "لا توجد مقررات ضمن البرنامج.", 0, 0)

        var currentYear = years.first()
        for (index in 0 until years.lastIndex) {
            val year = years[index]
            val yearCourses = courses.filter { it.academicYear == year }
            val yearPassed = yearCourses.count { GradeCalculator.calculate(it, program).isPassed == true }
            val remaining = yearCourses.size - yearPassed
            if (yearCourses.isNotEmpty() && remaining <= 4) {
                currentYear = years[index + 1]
            } else {
                currentYear = year
                break
            }
        }

        val currentCourses = courses.filter { it.academicYear == currentYear }
        val currentPassed = currentCourses.count { GradeCalculator.calculate(it, program).isPassed == true }
        val currentRemaining = currentCourses.size - currentPassed
        return standing(
            year = currentYear,
            details = "المتبقي من مقررات السنة الحالية: $currentRemaining؛ يتم الترفع عند بقاء 4 مقررات أو أقل، والتخرج بعد اجتياز الجميع.",
            passedCount = passedCount,
            totalCount = courses.size
        )
    }

    private fun isFourRemainingUniversity(name: String): Boolean =
        name.contains("الأندلس") || name.contains("دمشق") || name.contains("اللاذقية")

    private fun standing(
        year: Int,
        details: String,
        passedCount: Int,
        totalCount: Int,
        earnedHours: Int = 0,
        requiredHours: Int? = null
    ) = StudentStanding(
        title = "السنة ${yearName(year)}",
        details = details,
        currentYear = year,
        isGraduated = false,
        passedCourses = passedCount,
        totalCourses = totalCount,
        earnedCreditHours = earnedHours,
        requiredCreditHours = requiredHours
    )

    private fun yearName(year: Int): String = when (year) {
        1 -> "الأولى"
        2 -> "الثانية"
        3 -> "الثالثة"
        4 -> "الرابعة"
        5 -> "الخامسة"
        else -> year.toString()
    }
}
