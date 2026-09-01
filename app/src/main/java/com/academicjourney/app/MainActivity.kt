package com.academicjourney.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.academicjourney.app.ui.AcademicApp
import com.academicjourney.app.ui.AcademicViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AcademicViewModel = viewModel()
            AcademicApp(vm)
        }
    }
}
