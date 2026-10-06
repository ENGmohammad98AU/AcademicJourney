package com.academicjourney.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatakiaTranslationCurriculumTests {
    @Test
    fun suppliedScheduleCodesAreUniqueAndCoverFourYears() {
        val courses = LatakiaTranslationCurriculum.courses

        assertEquals(56, courses.size)
        assertEquals(56, courses.map { it.code }.distinct().size)
        assertEquals(setOf(1, 2, 3, 4), courses.map { it.academicYear }.toSet())
        assertEquals(setOf(1, 2), courses.map { it.semester }.toSet())
        assertTrue(courses.all { it.code.matches(Regex("\\d{3,4}")) })
        assertTrue(courses.all { it.name.isNotBlank() })
    }

    @Test
    fun everyYearMatchesTheAgreedSemesterCardCounts() {
        val expected = mapOf(
            1 to (6 to 6),
            2 to (6 to 6),
            3 to (8 to 9),
            4 to (7 to 8)
        )

        expected.forEach { (year, counts) ->
            val yearCourses = LatakiaTranslationCurriculum.courses.filter { it.academicYear == year }
            assertEquals(counts.first, yearCourses.count { it.semester == 1 })
            assertEquals(counts.second, yearCourses.count { it.semester == 2 })
        }
    }

    @Test
    fun representativeThreeAndFourDigitCodesMatchTheImage() {
        val byCode = LatakiaTranslationCurriculum.courses.associateBy { it.code }

        assertEquals("القواعد (1)", byCode.getValue("111").name)
        assertEquals(2, byCode.getValue("321").semester)
        assertEquals("نصوص في الأدب العربي المعاصر", byCode.getValue("3321").name)
        assertEquals(1, byCode.getValue("4412").semester)
        assertEquals("مقدمة في تحليل النصوص باللغة الإنكليزية", byCode.getValue("4423").name)
        assertEquals(2, byCode.getValue("4423").semester)
    }

    @Test
    fun programmeUsesOneFinalGradeWithPassMarkFifty() {
        assertEquals("SINGLE_FINAL_GRADE", LatakiaTranslationCurriculum.GRADING_SCHEME)
        assertEquals(50.0, LatakiaTranslationCurriculum.PASSING_GRADE, 0.0)
        assertTrue(
            LatakiaTranslationCurriculum.isProgramme(
                "الترجمة في اللغة الإنكليزية – التعليم المفتوح"
            )
        )
    }
}
