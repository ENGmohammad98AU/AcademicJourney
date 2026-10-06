package com.academicjourney.app.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.academicjourney.app.MainActivity
import com.academicjourney.app.R
import com.academicjourney.app.data.AcademicDatabase
import com.academicjourney.app.domain.GradeCalculator
import com.academicjourney.app.domain.GradeExplanation
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date

class JourneyWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { runCatching { refresh(context) } } finally { pending.finish() }
        }
    }
    companion object {
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, JourneyWidget::class.java))
            if (ids.isEmpty()) return
            val dao = AcademicDatabase.get(context).academicDao()
            val selected = context.getSharedPreferences("journey_preferences", 0).getLong("pinned_program", 0)
            val programs = dao.getPrograms()
            val program = programs.firstOrNull { it.id == selected } ?: programs.firstOrNull()
            val courses = dao.getCourses()
            val pc = courses.filter { it.programId == program?.id }
            val event = dao.getEvents().firstOrNull { it.kind == "EXAM" && it.startsAt > System.currentTimeMillis() }
            val views = RemoteViews(context.packageName, R.layout.journey_widget)
            views.setTextViewText(R.id.widget_program, program?.name ?: "مسيرتي الأكاديمية")
            val passed = program?.let { p -> pc.count { GradeCalculator.calculate(it, p).isPassed == true } } ?: 0
            val average = program?.let { GradeCalculator.average(pc, it) }
            views.setTextViewText(R.id.widget_progress, "$passed / ${pc.size} ناجح • المعدل ${GradeExplanation.number(average)}")
            views.setProgressBar(R.id.widget_bar, maxOf(pc.size, 1), passed, false)
            views.setTextViewText(R.id.widget_exam, event?.let {
                "${courses.firstOrNull { c -> c.id == it.courseId }?.name ?: it.title}\n" +
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.startsAt))
            } ?: "لا توجد امتحانات قادمة")
            val openProgram = Intent(context, MainActivity::class.java).putExtra("open_program", program?.id ?: 0)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val openExam = Intent(context, MainActivity::class.java).putExtra("open_course", event?.courseId ?: 0)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 20, openProgram, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            views.setOnClickPendingIntent(R.id.widget_exam, PendingIntent.getActivity(context, 21, openExam, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(ids, views)
        }
    }
}
