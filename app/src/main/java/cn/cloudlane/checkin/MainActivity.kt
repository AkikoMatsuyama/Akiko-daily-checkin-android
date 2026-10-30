package cn.cloudlane.checkin

import android.Manifest
import android.app.TimePickerDialog
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.AlarmClock
import android.widget.Toast
import android.content.ActivityNotFoundException
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.cloudlane.checkin.alarm.AlarmScheduler
import cn.cloudlane.checkin.data.CheckinStore
import cn.cloudlane.checkin.data.CheckinTask
import cn.cloudlane.checkin.data.todayKey
import java.util.Calendar

private val Ink = Color(0xFF22312D)
private val Muted = Color(0xFF75827C)
private val Accent = Color(0xFF287D65)
private val Paper = Color(0xFFF4F6F2)

class MainActivity : ComponentActivity() {
    private lateinit var store: CheckinStore
    private var tasks by mutableStateOf<List<CheckinTask>>(emptyList())
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = CheckinStore(this)
        tasks = store.load()
        requestReminderPermissions()
        setContent { CheckinScreen(tasks, ::persist) }
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) {
            tasks = store.load()
            AlarmScheduler.scheduleAll(this, tasks)
        }
    }

    private fun requestReminderPermissions() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val manager = getSystemService(android.app.AlarmManager::class.java)
            if (!manager.canScheduleExactAlarms()) {
                runCatching { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) }
            }
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val manager = getSystemService(android.app.NotificationManager::class.java)
            if (!manager.canUseFullScreenIntent()) {
                runCatching { startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))) }
            }
        }
    }

    private fun persist(updated: List<CheckinTask>) {
        tasks = updated
        store.save(updated)
        AlarmScheduler.scheduleAll(this, updated)
    }
}

@Composable
private fun CheckinScreen(tasks: List<CheckinTask>, onTasksChange: (List<CheckinTask>) -> Unit) {
    var draft by remember { mutableStateOf("") }
    val context = LocalContext.current
    val day = todayKey()
    val total = tasks.sumOf { it.times.size }
    val done = tasks.sumOf { task -> task.completed.count { it.startsWith(day) } }

    Surface(modifier = Modifier.fillMaxSize(), color = Paper) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(22.dp))
            Text("DAILY ROUTINE", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(7.dp))
            Text("每日打卡", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().background(Color(0xFFE6EEE8), RoundedCornerShape(12.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("今天的进度", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("$done / $total 次完成", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(if (total > 0 && done >= total) "已完成" else "继续保持", color = Accent, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("我的任务", Modifier.weight(1f), color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("${tasks.size} 项", color = Muted, fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
            if (tasks.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有打卡任务", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(5.dp))
                    Text("添加一项日常习惯，安排提醒时间", color = Muted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(tasks, key = { it.id }) { task ->
                        TaskRow(task, tasks, onTasksChange, context)
                    }
                }
            }
            if (tasks.isEmpty()) Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f).height(52.dp).background(Color.White, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 17.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontSize = 15.sp),
                    decorationBox = { inner -> if (draft.isEmpty()) Text("例如：喝水、阅读、运动", color = Muted, fontSize = 14.sp); inner() }
                )
                Spacer(Modifier.width(9.dp))
                Button(
                    onClick = {
                        val title = draft.trim()
                        if (title.isNotEmpty()) {
                            onTasksChange(tasks + CheckinTask(title = title, times = emptyList()))
                            draft = ""
                        }
                    },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) { Text("添加") }
            }
            Text("提醒會以系統鬧鐘通知呈現，效果受系統權限與勿擾設定影響。", Modifier.padding(bottom = 12.dp), color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TaskRow(task: CheckinTask, allTasks: List<CheckinTask>, onTasksChange: (List<CheckinTask>) -> Unit, context: android.content.Context) {
    var currentTasks by rememberSaveable(task.id) { mutableStateOf(allTasks) }
    LaunchedEffect(allTasks) { currentTasks = allTasks }
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(10.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(task.title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("每日 ${task.times.size} 次 · 已完成 ${task.completed.count { it.startsWith(todayKey()) }} 次", color = Muted, fontSize = 12.sp)
            }
            TextButton(onClick = {
                val updated = currentTasks.filterNot { it.id == task.id }
                currentTasks = updated
                onTasksChange(updated)
            }) { Text("删除", color = Muted) }
        }
        Spacer(Modifier.height(8.dp))
        if (task.times.isEmpty()) Text("尚未设置提醒时间", color = Muted, fontSize = 12.sp)
        task.times.forEach { time ->
            val done = "${todayKey()} $time" in task.completed
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(time, Modifier.weight(1f), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(if (done) "已打卡" else "待完成", color = if (done) Accent else Muted, fontSize = 12.sp)
                Spacer(Modifier.width(5.dp))
                TextButton(onClick = {
                    val updated = currentTasks.map { item ->
                        if (item.id == task.id) item.copy(completed = if (done) item.completed - "${todayKey()} $time" else item.completed + "${todayKey()} $time") else item
                    }
                    currentTasks = updated
                    onTasksChange(updated)
                }) { Text(if (done) "撤销" else "打卡", color = Accent) }
                TextButton(onClick = {
                    val updated = currentTasks.map { item -> if (item.id == task.id) item.copy(times = item.times - time) else item }
                    currentTasks = updated
                    onTasksChange(updated)
                }) { Text("移除", color = Muted) }
            }
        }
        OutlinedButton(
            onClick = {
                val now = Calendar.getInstance()
                TimePickerDialog(context, { _, hour, minute ->
                    val time = "%02d:%02d".format(hour, minute)
                    val alarmOccurrence = task.times.size + 1
                    if (time !in task.times) {
                        val fresh = CheckinStore(context).load()
                        val updated = fresh.map { item -> if (item.id == task.id) item.copy(times = (item.times + time).sorted()) else item }
                        currentTasks = updated
                        onTasksChange(updated)
                        AlertDialog.Builder(context)
                            .setTitle("同步到系统时钟？")
                            .setMessage("是否在手机自带的时钟中添加 $time 闹钟？备注将显示为“${task.title}（第${alarmOccurrence}次）”。")
                            .setNegativeButton("暂不添加", null)
                            .setPositiveButton("添加闹钟") { _, _ ->
                                val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                                    putExtra(AlarmClock.EXTRA_MESSAGE, "${task.title}（第${alarmOccurrence}次）")
                                    putExtra(AlarmClock.EXTRA_DAYS, ArrayList((1..7).toList()))
                                    putExtra(AlarmClock.EXTRA_VIBRATE, true)
                                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                                }
                                try {
                                    context.startActivity(alarmIntent)
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(context, "未找到支持自动设置闹钟的时钟应用。", Toast.LENGTH_LONG).show()
                                } catch (_: SecurityException) {
                                    Toast.makeText(context, "系统时钟拒绝了自动创建请求，请检查时钟应用权限。", Toast.LENGTH_LONG).show()
                                }
                            }
                            .show()
                    }
                }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
            },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(8.dp)
        ) { Text("＋ 添加打卡时间", color = Accent) }
    }
}
