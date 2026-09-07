package com.academicjourney.app.domain

import com.academicjourney.app.data.CourseEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseSearchTests {
    private val course = CourseEntity(
        id = 1,
        programId = 1,
        name = "مبادئ علم السياسة",
        code = "512",
        language = "العربية",
        academicYear = 1,
        semester = 1
    )

    @Test
    fun searchesByArabicNameWithoutBeingSensitiveToAlefVariants() {
        assertTrue(CourseSearch.matches(course, "مبادئ"))
        assertTrue(CourseSearch.matches(course, "السياسه"))
    }

    @Test
    fun searchesCourseNumberUsingWesternOrArabicDigits() {
        assertTrue(CourseSearch.matches(course, "512"))
        assertTrue(CourseSearch.matches(course, "٥١٢"))
    }

    @Test
    fun rejectsUnrelatedQuery() {
        assertFalse(CourseSearch.matches(course, "فيزياء"))
    }
}
