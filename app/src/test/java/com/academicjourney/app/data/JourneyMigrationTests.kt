package com.academicjourney.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JourneyMigrationTests {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun migrationFrom8PreservesMarksNotesTransfersAndValidatesRoomSchema() = runBlocking {
        val name = "migration-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE UniversityEntity (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
            db.execSQL("CREATE TABLE ProgramEntity (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, universityId INTEGER NOT NULL, name TEXT NOT NULL, degreeType TEXT NOT NULL, gradingScheme TEXT NOT NULL, assignmentWeight REAL NOT NULL, examWeight REAL NOT NULL, passingGrade REAL NOT NULL, FOREIGN KEY(universityId) REFERENCES UniversityEntity(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX index_ProgramEntity_universityId ON ProgramEntity(universityId)")
            db.execSQL("CREATE TABLE CourseEntity (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, programId INTEGER NOT NULL, name TEXT NOT NULL, code TEXT NOT NULL, language TEXT NOT NULL, academicYear INTEGER NOT NULL, semester INTEGER NOT NULL, practicalGrade REAL, theoryGrade REAL, assignmentGrade REAL, examGrade REAL, notes TEXT NOT NULL, studentWorkGrade REAL, practicalExamGrade REAL, creditHours INTEGER, passedWithoutGrade INTEGER NOT NULL DEFAULT 0, directGrade REAL, FOREIGN KEY(programId) REFERENCES ProgramEntity(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX index_CourseEntity_programId ON CourseEntity(programId)")
            db.execSQL("CREATE TABLE HighSchoolGradeEntity (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, branch TEXT NOT NULL, subject TEXT NOT NULL, maxGrade INTEGER NOT NULL, includedInPercentage INTEGER NOT NULL, displayOrder INTEGER NOT NULL, grade INTEGER)")
            db.execSQL("CREATE UNIQUE INDEX index_HighSchoolGradeEntity_branch_subject ON HighSchoolGradeEntity(branch,subject)")
            db.execSQL("INSERT INTO UniversityEntity VALUES (1,'جامعتي')")
            db.execSQL("INSERT INTO ProgramEntity VALUES (2,1,'البرنامج','','PRACTICAL_THEORY',0,0,50)")
            db.execSQL("INSERT INTO CourseEntity VALUES (3,2,'مادتي','101','',1,1,23.5,NULL,NULL,NULL,'ملاحظة محفوظة',NULL,NULL,5,0,NULL)")
            db.execSQL("INSERT INTO CourseEntity VALUES (4,2,'ترفيع','102','',1,1,NULL,NULL,NULL,NULL,'سبب الترفيع',NULL,NULL,3,1,NULL)")
            db.execSQL("INSERT INTO HighSchoolGradeEntity VALUES (5,'علمي','رياضيات',600,1,1,590)")
            db.version = 8
        }
        val db = Room.databaseBuilder(context, AcademicDatabase::class.java, name)
            .addMigrations(AcademicDatabase.MIGRATION_8_9, AcademicDatabase.MIGRATION_9_10).allowMainThreadQueries().build()
        try {
            val dao = db.academicDao()
            val c = dao.getCourse(3)!!
            assertEquals(23.5, c.practicalGrade!!, 0.001)
            assertNull(c.theoryGrade)
            assertEquals("ملاحظة محفوظة", c.notes)
            assertFalse(c.isCurrentSemester)
            assertTrue(dao.getCourse(4)!!.passedWithoutGrade)
            assertEquals(590, dao.getHighSchoolGrade(5)!!.grade)
            dao.setCurrentSemester(3, true)
            val event = AcademicEventEntity(UUID.randomUUID().toString(), 3, "EXAM", "نهائي", "", 1_900_000_000_000, 60)
            dao.saveEvent(event)
            assertEquals(event, dao.getEvents().single())
            assertTrue(dao.getCourse(3)!!.isCurrentSemester)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun versionOneBackupPreservesNewSemesterSelectionAndCurriculum() {
        val u = UniversityEntity(1, "الجامعة")
        val p = ProgramEntity(2, 1, "البرنامج", gradingScheme = "PRACTICAL_THEORY", passingGrade = 50.0)
        val c = CourseEntity(3, 2, "مادة", "100", academicYear = 1, semester = 1,
            practicalGrade = 10.0, theoryGrade = 50.0, creditHours = 4, isCurrentSemester = true)
        val file = File.createTempFile("backup", ".json", context.cacheDir)
        try {
            AcademicBackupManager.write(context, Uri.fromFile(file), listOf(u), listOf(p), listOf(c), emptyList())
            val json = JSONObject(file.readText())
            json.put("schemaVersion", 1)
            json.remove("journey")
            json.getJSONArray("courses").getJSONObject(0).apply {
                remove("isCurrentSemester"); remove("sourceId"); remove("sourceProgramId"); put("theoryGrade", JSONObject.NULL)
            }
            file.writeText(json.toString())
            val plan = AcademicBackupManager.readAndPlan(context, Uri.fromFile(file), listOf(u), listOf(p), listOf(c), emptyList())
            assertTrue(plan.courses.single().isCurrentSemester)
            assertEquals(4, plan.courses.single().creditHours)
            assertNull(plan.courses.single().theoryGrade)
            assertEquals(1, plan.differences.size)
            assertEquals(listOf(c), plan.originalCourses)
            assertTrue(plan.events.isEmpty())
        } finally { file.delete() }
    }

    @Test fun diplomacyMigrationPreservesHistoryDatesArchivesAndOtherProgrammes() = runBlocking {
        val name = "diplomacy-${UUID.randomUUID()}.db"
        val oldProgramme = ProgramEntity(2, 1, "الدراسات الدولية والدبلوماسية – التعليم المفتوح",
            gradingScheme = "PRACTICAL_THEORY", passingGrade = 50.0)
        val complete = CourseEntity(3, 2, "مقرر كامل", "510", academicYear = 1, semester = 1,
            practicalGrade = 20.0, theoryGrade = 56.1, notes = "ملاحظة محفوظة", isCurrentSemester = true)
        val partial = complete.copy(id = 4, name = "مقرر جزئي", code = "511", practicalGrade = null, theoryGrade = 60.0)
        val other = complete.copy(id = 6, programId = 5, directGrade = null)
        val before = complete.copy(theoryGrade = null)
        val history = GradeChangeEntity(UUID.randomUUID().toString(), "COURSE", 3, "حفظ", GradeSnapshot.encode(before), GradeSnapshot.encode(complete), 1000)
        val event = AcademicEventEntity(UUID.randomUUID().toString(), 3, "EXAM", "امتحان", "قاعة", 1_900_000_000_000, 60)
        val archive = SemesterSnapshots.create(oldProgramme, listOf(complete), 1, 1, "أرشيف ثابت")
        // v9 and v10 have identical tables; only curriculum data and its undo snapshots change.
        val initialDb = Room.databaseBuilder(context, AcademicDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            val dao = initialDb.academicDao()
            dao.insertUniversity(UniversityEntity(1, "جامعة دمشق"))
            dao.insertProgram(oldProgramme)
            dao.insertProgram(oldProgramme.copy(id = 5, name = "التاريخ"))
            listOf(complete, partial, other).forEach { dao.insertCourse(it) }
            dao.insertHistory(listOf(history)); dao.saveEvent(event); dao.insertArchives(listOf(archive))
        } finally { initialDb.close() }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = 9 }
        val db = Room.databaseBuilder(context, AcademicDatabase::class.java, name)
            .addMigrations(AcademicDatabase.MIGRATION_9_10).allowMainThreadQueries().build()
        try {
            val dao = db.academicDao()
            val migrated = dao.getCourse(3)!!
            assertEquals(76.1, migrated.directGrade!!, 0.0001)
            assertEquals(complete, migrated.copy(directGrade = null))
            assertEquals(partial, dao.getCourse(4))
            assertEquals(other, dao.getCourse(6))
            assertEquals("SINGLE_FINAL_GRADE", dao.getPrograms().first { it.id == 2L }.gradingScheme)
            assertEquals("PRACTICAL_THEORY", dao.getPrograms().first { it.id == 5L }.gradingScheme)
            val change = dao.getHistory().single()
            assertTrue(GradeSnapshot.same(change.afterJson, GradeSnapshot.encode(migrated)))
            assertEquals(before, GradeSnapshot.apply(migrated, change.beforeJson))
            assertEquals(listOf(event), dao.getEvents())
            assertEquals(listOf(archive), dao.getArchives())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun oldDiplomacyBackupUpgradesMarksAndHistoryButNewBackupKeepsEmptyFinalGrade() {
        val u = UniversityEntity(1, "جامعة دمشق")
        val p = ProgramEntity(2, 1, "الدراسات الدولية والدبلوماسية – التعليم المفتوح",
            gradingScheme = DiplomacyCurriculum.GRADING_SCHEME, passingGrade = 50.0)
        val c = CourseEntity(3, 2, "مقرر", "510", academicYear = 1, semester = 1,
            practicalGrade = 20.0, theoryGrade = 56.1, notes = "ملاحظة")
        val partial = c.copy(id = 4, code = "511", name = "جزئي", theoryGrade = null)
        val h = GradeChangeEntity(UUID.randomUUID().toString(), "COURSE", 3, "حفظ",
            GradeSnapshot.encode(c.copy(theoryGrade = null)), GradeSnapshot.encode(c), 1000)
        val file = File.createTempFile("diplomacy-backup", ".json", context.cacheDir)
        fun plan() = AcademicBackupManager.readAndPlan(context, Uri.fromFile(file), listOf(u), listOf(p), listOf(c, partial), emptyList())
        try {
            AcademicBackupManager.write(context, Uri.fromFile(file), listOf(u), listOf(p), listOf(c, partial), emptyList(), history = listOf(h))
            val current = file.readText()
            assertEquals(3, JSONObject(current).getInt("schemaVersion"))
            assertNull(plan().courses.first { it.id == 3L }.directGrade)
            file.writeText(JSONObject(current).put("schemaVersion", 2).toString())
            val old = plan()
            val restored = old.courses.first { it.id == 3L }
            assertEquals(76.1, restored.directGrade!!, 0.0001)
            assertTrue(GradeSnapshot.same(old.history.single().afterJson, GradeSnapshot.encode(restored)))
            assertEquals(partial, old.courses.first { it.id == 4L })
            assertEquals("ملاحظة", restored.notes)
        } finally { file.delete() }
    }
}
