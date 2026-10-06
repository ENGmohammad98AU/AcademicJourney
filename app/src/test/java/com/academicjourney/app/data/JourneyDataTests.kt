package com.academicjourney.app.data

import com.academicjourney.app.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class JourneyDataTests {
    private val p = ProgramEntity(4, 2, "علوم", gradingScheme = GradeCalculator.PRACTICAL_THEORY, passingGrade = 50.0)
    private val c = CourseEntity(7, p.id, "لغة", "101", academicYear = 1, semester = 1, practicalGrade = 20.0, theoryGrade = 45.0, creditHours = 3)

    @Test fun undoRestoresEditableValuesWithoutChangingCurriculumOrSelection() {
        val newer = c.copy(theoryGrade = 60.0, notes = "تعديل")
        val curriculum = newer.copy(code = "102", creditHours = 6, passedWithoutGrade = true, isCurrentSemester = true)
        val restored = GradeSnapshot.apply(curriculum, GradeSnapshot.encode(c))
        assertEquals(45.0, restored.theoryGrade)
        assertEquals("102", restored.code)
        assertEquals(6, restored.creditHours)
        assertTrue(restored.passedWithoutGrade)
        assertTrue(restored.isCurrentSemester)
        assertFalse(GradeSnapshot.same(GradeSnapshot.encode(newer), GradeSnapshot.encode(c)))
    }

    @Test fun changingNotesAlsoPreventsStaleUndo() {
        assertFalse(GradeSnapshot.same(GradeSnapshot.encode(c), GradeSnapshot.encode(c.copy(notes = "أحدث"))))
        assertTrue(GradeSnapshot.same(GradeSnapshot.encode(c), GradeSnapshot.encode(c.copy(isCurrentSemester = true))))
    }

    @Test fun archiveRemainsUnchangedAfterMarksAreEdited() {
        val archive = SemesterSnapshots.create(p, listOf(c, c.copy(id = 8, passedWithoutGrade = true)), 1, 1, "خريف 2026")
        val snapshot = JSONObject(archive.snapshotJson)
        assertEquals(65.0, snapshot.getDouble("average"), 0.001)
        assertEquals(2, snapshot.getInt("passed"))
        val changed = c.copy(theoryGrade = 70.0)
        assertEquals(90.0, GradeCalculator.calculate(changed, p).finalGrade!!, 0.001)
        assertEquals(65.0, JSONObject(archive.snapshotJson).getDouble("average"), 0.001)
    }

    @Test fun journeyBackupRemapsIdentifiersAndPreservesReminders() {
        val event = AcademicEventEntity(UUID.randomUUID().toString(), c.id, "EXAM", "امتحان", "القاعة", 1_900_000_000_000, 1440)
        val change = GradeChangeEntity(UUID.randomUUID().toString(), "COURSE", c.id, c.name,
            GradeSnapshot.encode(c), GradeSnapshot.encode(c.copy(theoryGrade = 60.0)), 10)
        val archive = SemesterSnapshots.create(p, listOf(c), 1, 1, "الفصل")
        val target = c.copy(id = 800, programId = 900)
        val result = JourneyBackup.decode(JourneyBackup.encode(listOf(event), listOf(change), listOf(archive)),
            mapOf(c.id to 800), mapOf(p.id to 900), emptyMap(), listOf(target), emptyList(), listOf(p.copy(id = 900)))
        assertEquals(800L, result.events.single().courseId)
        assertEquals(1440, result.events.single().reminderMinutes)
        assertEquals(800L, result.history.single().targetId)
        assertEquals(900L, result.archives.single().programId)
        assertEquals(0, result.skipped)
    }

    @Test(expected = IllegalArgumentException::class)
    fun backupRejectsComponentsWhoseSumExceeds100() {
        JourneyBackup.validateCourse(c.copy(theoryGrade = 90.0), p)
    }
    @Test fun partialExplanationNeverClaimsFinalResultOrFailure() {
        val text = GradeExplanation.course(c.copy(theoryGrade = null), p).joinToString(" ")
        assertTrue(text.contains("لم يتم إدخال درجة النظري بعد"))
        assertTrue(text.contains("20"))
        assertFalse(text.contains("راسب"))
    }
    @Test fun weightedAverageExplanationMatchesExistingCalculation() {
        val values = listOf(c, c.copy(id = 8, practicalGrade = 30.0, theoryGrade = 60.0, creditHours = 6),
            c.copy(id = 9, theoryGrade = null), c.copy(id = 10, passedWithoutGrade = true))
        assertEquals((65.0 * 3 + 90.0 * 6) / 9, GradeCalculator.average(values, p)!!, 0.001)
        assertTrue(GradeExplanation.average(values, p).contains("(9)"))
    }
}
