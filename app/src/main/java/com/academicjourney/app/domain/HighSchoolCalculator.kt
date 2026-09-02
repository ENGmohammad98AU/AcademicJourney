package com.academicjourney.app.domain

import com.academicjourney.app.data.HighSchoolGradeEntity

data class HighSchoolSummary(
    val totalGrade: Int,
    val maximumGrade: Int,
    val percentage: Double
)

object HighSchoolCalculator {
    fun calculate(grades: List<HighSchoolGradeEntity>): HighSchoolSummary {
        val included = grades.filter { it.includedInPercentage }
        val maximum = included.sumOf { it.maxGrade }
        val total = included.sumOf { (it.grade ?: 0).coerceIn(0, it.maxGrade) }
        val percentage = if (maximum == 0) 0.0 else total.toDouble() * 100.0 / maximum.toDouble()
        return HighSchoolSummary(total, maximum, percentage)
    }

    fun subjectPercentage(item: HighSchoolGradeEntity): Double? {
        val grade = item.grade ?: return null
        if (item.maxGrade <= 0) return null
        return grade.coerceIn(0, item.maxGrade).toDouble() * 100.0 / item.maxGrade.toDouble()
    }
}
