package com.academicjourney.app.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.academicjourney.app.MainActivity
import com.academicjourney.app.R
import com.academicjourney.app.data.*
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date

object JourneyReminders {
    private const val CHANNEL = "academic_dates"
    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    private fun pending(context: Context, id: String): PendingIntent {
        val intent = Intent(context, AcademicReminderReceiver::class.java)
            .setData(Uri.parse("academicjourney://reminder/$id")).putExtra("event_id", id)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun cancel(context: Context, id: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, id))
        context.getSystemService(NotificationManager::class.java).cancel(id, 1)
    }
    fun schedule(context: Context, event: AcademicEventEntity) {
        cancel(context, event.id)
        val minutes = event.reminderMinutes ?: return
        val now = System.currentTimeMillis()
        if (event.notifiedAt != null || event.startsAt <= now || !allowed(context)) return
        val time = maxOf(event.startsAt - minutes * 60_000L, now + 1000L)
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending(context, event.id))
    }
    fun reschedule(context: Context, events: List<AcademicEventEntity>) {
        events.forEach { schedule(context, it) }
    }

    @SuppressLint("MissingPermission") // allowed() checks the runtime permission immediately before notifying.
    suspend fun notify(context: Context, id: String) {
        val dao = AcademicDatabase.get(context).academicDao()
        val event = dao.getEvent(id) ?: return
        // Inexact alarms can be delivered shortly after the event time (especially a 0-minute reminder).
        if (event.notifiedAt != null || event.reminderMinutes == null || event.startsAt + 3_600_000L < System.currentTimeMillis() || !allowed(context)) return
        // Ignore an obsolete alarm after its event was postponed.
        if (event.startsAt - event.reminderMinutes * 60_000L > System.currentTimeMillis() + 1000) {
            schedule(context, event)
            return
        }
        val course = dao.getCourse(event.courseId) ?: return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "مواعيد الامتحانات والوظائف", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val intent = Intent(context, MainActivity::class.java).putExtra("open_course", course.id)
            .setData(Uri.parse("academicjourney://course/${course.id}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val tap = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else Notification.Builder(context)
        val time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(event.startsAt))
        val text = "${course.name} • $time" + if (event.place.isBlank()) "" else " • ${event.place}"
        manager.notify(id, 1, builder.setSmallIcon(R.drawable.ic_journey_notification)
            .setContentTitle(event.title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(tap).setAutoCancel(true).build())
        dao.saveEvent(event.copy(notifiedAt = System.currentTimeMillis()))
        JourneyWidget.refresh(context)
    }
}

class AcademicReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { runCatching { intent.getStringExtra("event_id")?.let { JourneyReminders.notify(context.applicationContext, it) } } }
            finally { pending.finish() }
        }
    }
}

class JourneyBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    val dao = AcademicDatabase.get(context).academicDao()
                    JourneyReminders.reschedule(context, dao.getEvents())
                    JourneyWidget.refresh(context)
                }
            } finally { pending.finish() }
        }
    }
}
