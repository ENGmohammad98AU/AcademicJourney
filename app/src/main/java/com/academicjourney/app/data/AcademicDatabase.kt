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
    version = 3,
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

        fun get(context: Context): AcademicDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AcademicDatabase::class.java,
                "academic_journey.db"
            )
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }
    }
}
