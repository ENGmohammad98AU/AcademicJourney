package com.academicjourney.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.academicjourney.app.data.AcademicDatabase
import com.academicjourney.app.data.CourseEntity
import com.academicjourney.app.data.HighSchoolGradeEntity
import com.academicjourney.app.data.HighSchoolSeedData
import com.academicjourney.app.data.SeedData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AcademicViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AcademicDatabase.get(app).academicDao()

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
}
