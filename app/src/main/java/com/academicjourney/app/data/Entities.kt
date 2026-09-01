package com.academicjourney.app.data
import androidx.room.*
@Entity data class UniversityEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val name:String)
@Entity(foreignKeys=[ForeignKey(entity=UniversityEntity::class,parentColumns=["id"],childColumns=["universityId"],onDelete=ForeignKey.CASCADE)],indices=[Index("universityId")])
data class ProgramEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val universityId:Long,val name:String,val degreeType:String="",val gradingScheme:String,val assignmentWeight:Double=0.0,val examWeight:Double=0.0,val passingGrade:Double)
@Entity(foreignKeys=[ForeignKey(entity=ProgramEntity::class,parentColumns=["id"],childColumns=["programId"],onDelete=ForeignKey.CASCADE)],indices=[Index("programId")])
data class CourseEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val programId:Long,val name:String,val code:String="",val language:String="",val academicYear:Int,val semester:Int,val practicalGrade:Double?=null,val theoryGrade:Double?=null,val assignmentGrade:Double?=null,val examGrade:Double?=null,val notes:String="")
