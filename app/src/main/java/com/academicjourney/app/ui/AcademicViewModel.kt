package com.academicjourney.app.ui
import android.app.Application
import androidx.lifecycle.*
import com.academicjourney.app.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
class AcademicViewModel(app:Application):AndroidViewModel(app){private val dao=AcademicDatabase.get(app).academicDao();val universities=dao.observeUniversities().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val programs=dao.observePrograms().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val courses=dao.observeCourses().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());fun ensureSeeded()=viewModelScope.launch{SeedData.seed(dao)};fun saveCourse(c:CourseEntity)=viewModelScope.launch{dao.updateCourse(c)}}
