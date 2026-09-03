package com.academicjourney.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.academicjourney.app.data.AcademicBackupManager
import com.academicjourney.app.data.AcademicDatabase
import com.academicjourney.app.data.CourseEntity
import com.academicjourney.app.data.HighSchoolGradeEntity
import com.academicjourney.app.data.HighSchoolSeedData
import com.academicjourney.app.data.SeedData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AcademicViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = app.applicationContext
    private val database = AcademicDatabase.get(app)
    private val dao = database.academicDao()

    val universities = dao.observeUniversities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val programs = dao.observePrograms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val courses = dao.observeCourses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val highSchoolGrades = dao.observeHighSchoolGrades()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun ensureSeeded() = viewModelScope.launch {
        SeedData.seed(dao)
        HighSchoolSeedData.seed(dao)
    }

    fun saveCourse(course: CourseEntity) = viewModelScope.launch {
        dao.updateCourse(course)
    }

    fun saveHighSchoolGrade(item: HighSchoolGradeEntity) = viewModelScope.launch {
        dao.updateHighSchoolGrade(item)
    }

    fun exportBackup(uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        val result = runCatching {
            withContext(Dispatchers.IO) {
                SeedData.seed(dao)
                HighSchoolSeedData.seed(dao)
                val universities = dao.getUniversities()
                val programs = dao.getPrograms()
                val courses = dao.getCourses()
                val highSchool = dao.getHighSchoolGrades()
                AcademicBackupManager.write(
                    context = appContext,
                    uri = uri,
                    universities = universities,
                    programs = programs,
                    courses = courses,
                    highSchoolGrades = highSchool
                )
                courses.size to highSchool.size
            }
        }
        onResult(result.fold(
            onSuccess = { (courseCount, highSchoolCount) ->
                "تم حفظ نسخة احتياطية قابلة للاستعادة تضم $courseCount مقررًا و$highSchoolCount مادة ثانوية."
            },
            onFailure = { "تعذر تصدير النسخة الاحتياطية: ${it.message ?: "خطأ غير معروف"}" }
        ))
    }

    fun importBackup(uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        val result = runCatching {
            withContext(Dispatchers.IO) {
                SeedData.seed(dao)
                HighSchoolSeedData.seed(dao)
                val plan = AcademicBackupManager.readAndPlan(
                    context = appContext,
                    uri = uri,
                    universities = dao.getUniversities(),
                    programs = dao.getPrograms(),
                    currentCourses = dao.getCourses(),
                    currentHighSchoolGrades = dao.getHighSchoolGrades()
                )
                database.withTransaction {
                    if (plan.courses.isNotEmpty()) dao.updateCourses(plan.courses)
                    if (plan.highSchoolGrades.isNotEmpty()) dao.updateHighSchoolGrades(plan.highSchoolGrades)
                }
                plan
            }
        }
        onResult(result.fold(
            onSuccess = { plan ->
                buildString {
                    append("تمت استعادة ${plan.restoredCourseCount} مقررًا و${plan.restoredHighSchoolCount} مادة ثانوية بنجاح.")
                    if (plan.skippedCount > 0) append(" تم تجاهل ${plan.skippedCount} سجلًا غير مطابق للإصدار الحالي.")
                }
            },
            onFailure = { "تعذر استعادة النسخة الاحتياطية: ${it.message ?: "خطأ غير معروف"}" }
        ))
    }
}
