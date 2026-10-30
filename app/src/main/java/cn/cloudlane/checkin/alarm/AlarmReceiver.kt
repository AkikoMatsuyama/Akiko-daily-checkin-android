package cn.cloudlane.checkin.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import cn.cloudlane.checkin.MainActivity
import cn.cloudlane.checkin.R
import cn.cloudlane.checkin.data.CheckinStore
import cn.cloudlane.checkin.ui.ReminderActivity

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_ID) ?: return
        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_TITLE) ?: "打卡提醒"
        val time = intent.getStringExtra(AlarmScheduler.EXTRA_TIME) ?: ""
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "打卡提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                )
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
        val fullScreen = PendingIntent.getActivity(
            context, taskId.hashCode(),
            Intent(context, ReminderActivity::class.java).apply {
                putExtra(AlarmScheduler.EXTRA_TASK_ID, taskId)
                putExtra(AlarmScheduler.EXTRA_TASK_TITLE, title)
                putExtra(AlarmScheduler.EXTRA_TIME, time)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("每日打卡时间")
            .setContentText("现在完成：$title")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(taskId.hashCode(), notification)

        val tasks = CheckinStore(context).load()
        AlarmScheduler.scheduleAll(context, tasks)
    }

    companion object { const val CHANNEL_ID = "daily_checkin_alarm" }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.scheduleAll(context, CheckinStore(context).load())
    }
}
