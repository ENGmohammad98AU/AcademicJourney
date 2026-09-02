package com.academicjourney.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UniversityEntity::class,
        ProgramEntity::class,
        CourseEntity::class,
        HighSchoolGradeEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AcademicDatabase : RoomDatabase() {
    abstract fun academicDao(): AcademicDao

    companion object {
        @Volatile
        private var INSTANCE: AcademicDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `HighSchoolGradeEntity` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `branch` TEXT NOT NULL,
                        `subject` TEXT NOT NULL,
                        `maxGrade` INTEGER NOT NULL,
                        `includedInPercentage` INTEGER NOT NULL,
                        `displayOrder` INTEGER NOT NULL,
                        `grade` INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_HighSchoolGradeEntity_branch_subject` " +
                        "ON `HighSchoolGradeEntity` (`branch`, `subject`)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `CourseEntity` ADD COLUMN `studentWorkGrade` REAL")
                db.execSQL("ALTER TABLE `CourseEntity` ADD COLUMN `practicalExamGrade` REAL")

                // Existing Al-Andalus grades remain numerically identical after splitting
                // the old practical field into student work + practical exam.
                db.execSQL(
                    """
                    UPDATE `CourseEntity`
                    SET `studentWorkGrade` = `practicalGrade`, `practicalExamGrade` = 0
                    WHERE `practicalGrade` IS NOT NULL
                      AND `programId` IN (
                          SELECT p.`id` FROM `ProgramEntity` p
                          INNER JOIN `UniversityEntity` u ON u.`id` = p.`universityId`
                          WHERE u.`name` LIKE '%الأندلس%'
                      )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE `ProgramEntity`
                    SET `gradingScheme` = 'ANDALUS_SPLIT_PRACTICAL_THEORY'
                    WHERE `universityId` IN (
                        SELECT `id` FROM `UniversityEntity` WHERE `name` LIKE '%الأندلس%'
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE `CourseEntity`
                    SET `name` = 'اللغة الانكليزية التكميلية (2)'
                    WHERE `name` = 'اللغة الانكليزية التكميلية (1)'
                      AND `academicYear` = 4
                      AND `semester` = 2
                      AND `programId` IN (
                          SELECT p.`id` FROM `ProgramEntity` p
                          INNER JOIN `UniversityEntity` u ON u.`id` = p.`universityId`
                          WHERE u.`name` LIKE '%الأندلس%'
                      )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Keep repeated courses as entered, but make the complementary-English
                // number consistent with its academic year for Al-Andalus University.
                db.execSQL(
                    """
                    UPDATE `CourseEntity`
                    SET `name` = 'اللغة الانكليزية التكميلية (1)'
                    WHERE `academicYear` = 4
                      AND `name` IN (
                          'اللغة الانكليزية التكميلية (1)',
                          'اللغة الانكليزية التكميلية (2)'
                      )
                      AND `programId` IN (
                          SELECT p.`id` FROM `ProgramEntity` p
                          INNER JOIN `UniversityEntity` u ON u.`id` = p.`universityId`
                          WHERE u.`name` LIKE '%الأندلس%'
                      )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE `CourseEntity`
                    SET `name` = 'اللغة الانكليزية التكميلية (2)'
                    WHERE `academicYear` = 5
                      AND `name` IN (
                          'اللغة الانكليزية التكميلية (1)',
                          'اللغة الانكليزية التكميلية (2)'
                      )
                      AND `programId` IN (
                          SELECT p.`id` FROM `ProgramEntity` p
                          INNER JOIN `UniversityEntity` u ON u.`id` = p.`universityId`
                          WHERE u.`name` LIKE '%الأندلس%'
                      )
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): AcademicDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AcademicDatabase::class.java,
                "academic_journey.db"
            )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                .also { INSTANCE = it }
        }
    }
}
