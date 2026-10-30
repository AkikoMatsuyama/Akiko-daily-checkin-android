package cn.cloudlane.checkin.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class CheckinTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val times: List<String>,
    val completed: Set<String> = emptySet(),
    val enabled: Boolean = true
) {
    val todayCount: Int get() = completed.count { it.startsWith(todayKey()) }
}

fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

class CheckinStore(context: Context) {
    private val prefs = context.getSharedPreferences("checkins", Context.MODE_PRIVATE)

    fun load(): List<CheckinTask> {
        val array = runCatching { JSONArray(prefs.getString("tasks", "[]")) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                val timesJson = item.getJSONArray("times")
                val completedJson = item.getJSONArray("completed")
                CheckinTask(
                    id = item.getString("id"),
                    title = item.getString("title"),
                    times = (0 until timesJson.length()).map { timesJson.getString(it) }.sorted(),
                    completed = (0 until completedJson.length()).map { completedJson.getString(it) }.toSet(),
                    enabled = item.optBoolean("enabled", true)
                )
            }.getOrNull()
        }
    }

    fun save(tasks: List<CheckinTask>) {
        val array = JSONArray()
        tasks.forEach { task ->
            array.put(JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("times", JSONArray(task.times))
                put("completed", JSONArray(task.completed.toList()))
                put("enabled", task.enabled)
            })
        }
        prefs.edit().putString("tasks", array.toString()).apply()
    }
}
