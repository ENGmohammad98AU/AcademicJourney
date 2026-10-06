package com.academicjourney.app

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.academicjourney.app.ui.AcademicApp
import com.academicjourney.app.ui.AcademicViewModel

class MainActivity : ComponentActivity() {
    private val launchRequest = mutableStateOf(Triple(0L, 0L, 0L))
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readRequest(intent)
        setContent {
            val vm: AcademicViewModel = viewModel()
            val request = launchRequest.value
            AcademicApp(vm, request.first, request.second, request.third)
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readRequest(intent)
    }
    private fun readRequest(intent: Intent) {
        launchRequest.value = Triple(intent.getLongExtra("open_course", 0), intent.getLongExtra("open_program", 0), System.nanoTime())
    }
}
