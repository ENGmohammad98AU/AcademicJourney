package com.academicjourney.app.data
import androidx.room.*
@Entity data class UniversityEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val name:String)
@Entity(foreignKeys=[ForeignKey(entity=UniversityEntity::class,parentColumns=["id"],childColumns=["universityId"],onDelete=ForeignKey.CASCADE)],indices=[Index("universityId")])
data class ProgramEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val universityId:Long,val name:String,val degreeType:String="",val gradingScheme:String,val assignmentWeight:Double=0.0,val examWeight:Double=0.0,val passingGrade:Double)
@Entity(foreignKeys=[ForeignKey(entity=ProgramEntity::class,parentColumns=["id"],childColumns=["programId"],onDelete=ForeignKey.CASCADE)],indices=[Index("programId")])
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long,
    val name: String,
    val code: String = "",
    val language: String = "",
    val academicYear: Int,
    val semester: Int,
    val practicalGrade: Double? = null,
    val theoryGrade: Double? = null,
    val assignmentGrade: Double? = null,
    val examGrade: Double? = null,
    val notes: String = "",
    val studentWorkGrade: Double? = null,
    val practicalExamGrade: Double? = null,
    val creditHours: Int? = null,
    @ColumnInfo(defaultValue = "0") val passedWithoutGrade: Boolean = false,
    val directGrade: Double? = null,
    @ColumnInfo(defaultValue = "0") val isCurrentSemester: Boolean = false
)

@Entity(indices = [Index("targetId")])
data class GradeChangeEntity(
    @PrimaryKey val id: String,
    val targetKind: String,
    val targetId: Long,
    val title: String,
    val beforeJson: String,
    val afterJson: String,
    val createdAt: Long,
    val undone: Boolean = false
)

@Entity(foreignKeys = [ForeignKey(entity = CourseEntity::class, parentColumns = ["id"], childColumns = ["courseId"], onDelete = ForeignKey.CASCADE)], indices = [Index("courseId")])
data class AcademicEventEntity(
    @PrimaryKey val id: String,
    val courseId: Long,
    val kind: String,
    val title: String,
    val place: String,
    val startsAt: Long,
    val reminderMinutes: Int? = null,
    val notifiedAt: Long? = null
)

@Entity(indices = [Index("programId")])
data class SemesterArchiveEntity(
    @PrimaryKey val id: String,
    val programId: Long,
    val academicYear: Int,
    val semester: Int,
    val label: String,
    val createdAt: Long,
    val snapshotJson: String
)

@Entity(indices = [Index(value = ["branch", "subject"], unique = true)])
data class HighSchoolGradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val branch: String,
    val subject: String,
    val maxGrade: Int,
    val includedInPercentage: Boolean,
    val displayOrder: Int,
    val grade: Int? = null
)
