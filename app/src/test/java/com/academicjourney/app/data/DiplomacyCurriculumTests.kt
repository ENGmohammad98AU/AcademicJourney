package com.academicjourney.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiplomacyCurriculumTests {
    @Test
    fun officialWorkbookNumbersCoverEveryCourseExactlyOnce() {
        val numbers = DiplomacyCurriculum.courseNumberByName.values.map(String::toInt)

        assertEquals(48, DiplomacyCurriculum.courseNumberByName.size)
        assertEquals(48, numbers.distinct().size)
        assertEquals((510..557).toList(), numbers.sorted())
    }

    @Test
    fun representativeNumbersMatchTheSuppliedWorkbook() {
        assertEquals("510", DiplomacyCurriculum.numberFor("مدخل إلى علم القانون"))
        assertEquals("534", DiplomacyCurriculum.numberFor("نظرية العلاقات الدولية"))
        assertEquals("557", DiplomacyCurriculum.numberFor("الجغرافيا السياسية"))
        assertTrue(DiplomacyCurriculum.isProgramme("الدراسات الدولية والدبلوماسية – التعليم المفتوح"))
    }
}
