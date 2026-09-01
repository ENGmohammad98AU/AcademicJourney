package com.academicjourney.app.data
import android.content.Context
import androidx.room.*
@Database(entities=[UniversityEntity::class,ProgramEntity::class,CourseEntity::class],version=2,exportSchema=false)
abstract class AcademicDatabase:RoomDatabase(){abstract fun academicDao():AcademicDao
companion object{@Volatile private var INSTANCE:AcademicDatabase?=null
fun get(context:Context):AcademicDatabase=INSTANCE?:synchronized(this){INSTANCE?:Room.databaseBuilder(context.applicationContext,AcademicDatabase::class.java,"academic_journey.db").fallbackToDestructiveMigration().build().also{INSTANCE=it}}}}
