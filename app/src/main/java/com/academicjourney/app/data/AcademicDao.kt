package com.academicjourney.app.data
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Dao interface AcademicDao {
@Query("SELECT * FROM UniversityEntity ORDER BY name") fun observeUniversities():Flow<List<UniversityEntity>>
@Query("SELECT * FROM ProgramEntity ORDER BY name") fun observePrograms():Flow<List<ProgramEntity>>
@Query("SELECT * FROM CourseEntity ORDER BY programId, academicYear, semester, name") fun observeCourses():Flow<List<CourseEntity>>
@Query("SELECT * FROM HighSchoolGradeEntity ORDER BY branch, displayOrder") fun observeHighSchoolGrades():Flow<List<HighSchoolGradeEntity>>
@Query("SELECT COUNT(*) FROM UniversityEntity") suspend fun universityCount():Int
@Query("SELECT COUNT(*) FROM HighSchoolGradeEntity") suspend fun highSchoolGradeCount():Int
@Insert suspend fun insertUniversity(item:UniversityEntity):Long
@Insert suspend fun insertProgram(item:ProgramEntity):Long
@Insert suspend fun insertCourse(item:CourseEntity):Long
@Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertHighSchoolGrade(item:HighSchoolGradeEntity):Long
@Update suspend fun updateCourse(item:CourseEntity)
@Update suspend fun updateHighSchoolGrade(item:HighSchoolGradeEntity)
}
