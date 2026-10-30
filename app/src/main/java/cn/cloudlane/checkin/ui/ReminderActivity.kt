package cn.cloudlane.checkin.ui

import android.app.Activity
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.cloudlane.checkin.alarm.AlarmScheduler
import cn.cloudlane.checkin.data.CheckinStore
import cn.cloudlane.checkin.data.todayKey

class ReminderActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null
    private var taskId = ""
    private var taskTitle = "打卡提醒"
    private var time = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        if (Build.VERSION.SDK_INT >= 27) setTurnScreenOn(true)
        taskId = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_ID) ?: ""
        taskTitle = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_TITLE) ?: "打卡提醒"
        time = intent.getStringExtra(AlarmScheduler.EXTRA_TIME) ?: ""
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(this, alarmUri)?.also { it.play() }
        vibrate()
        setContent {
            var checked by remember { mutableStateOf(false) }
            ReminderScreen(taskTitle, time, checked) {
                val store = CheckinStore(this)
                val tasks = store.load().map { task ->
                    if (task.id == taskId) task.copy(completed = task.completed + "${todayKey()} $time") else task
                }
                store.save(tasks)
                AlarmScheduler.scheduleAll(this, tasks)
                checked = true
                ringtone?.stop()
                finish()
            }
        }
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 300, 600), 0))
        else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 600, 300, 600), 0)
        }
    }

    override fun onDestroy() {
        ringtone?.stop()
        val vibrator = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator else @Suppress("DEPRECATION") (getSystemService(VIBRATOR_SERVICE) as Vibrator)
        vibrator.cancel()
        super.onDestroy()
    }
}

@Composable
private fun ReminderScreen(title: String, time: String, checked: Boolean, onCheckin: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Color(0xFF17231F)) {
        Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("打卡时间到了", color = Color(0xFFB8D8C7), fontSize = 14.sp)
            Spacer(Modifier.height(18.dp))
            Text(time, color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(20.dp))
            Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(44.dp))
            Button(onClick = onCheckin, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF77C7A4))) {
                Text(if (checked) "已完成" else "完成打卡", color = Color(0xFF12241C), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text("完成打卡后提醒将关闭", color = Color(0xFFB7C3BE), fontSize = 12.sp)
        }
    }
}
