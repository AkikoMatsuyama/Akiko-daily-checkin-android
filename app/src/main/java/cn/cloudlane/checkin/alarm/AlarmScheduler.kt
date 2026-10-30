package cn.cloudlane.checkin.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import cn.cloudlane.checkin.data.CheckinTask
import java.util.Calendar

object AlarmScheduler {
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_TASK_TITLE = "task_title"
    const val EXTRA_TIME = "time"

    fun scheduleAll(context: Context, tasks: List<CheckinTask>) {
        val manager = context.getSystemService(AlarmManager::class.java)
        tasks.forEach { task ->
            task.times.forEach { time ->
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    putExtra(EXTRA_TASK_ID, task.id)
                    putExtra(EXTRA_TASK_TITLE, task.title)
                    putExtra(EXTRA_TIME, time)
                }
                val requestCode = (task.id + time).hashCode()
                val pending = PendingIntent.getBroadcast(
                    context, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                if (!task.enabled) {
                    manager.cancel(pending)
                    return@forEach
                }
                val parts = time.split(":")
                if (parts.size != 2) return@forEach
                val target = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, parts[0].toIntOrNull() ?: 0)
                    set(Calendar.MINUTE, parts[1].toIntOrNull() ?: 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (target.timeInMillis <= System.currentTimeMillis()) target.add(Calendar.DAY_OF_YEAR, 1)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pending)
                } else {
                    manager.setExact(AlarmManager.RTC_WAKEUP, target.timeInMillis, pending)
                }
            }
        }
    }
}
