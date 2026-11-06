package io.github.cuongdev.debuggerkit.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(val time: Long, val message: String, val source: String)

/** Local audit trail of every change the app makes. Never leaves the device unless the user exports it. */
class ActivityLog(context: Context) {
    private val file = File(context.filesDir, "activity.log")
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    @Synchronized
    fun add(message: String, source: String) {
        val entry = LogEntry(System.currentTimeMillis(), message.clean(), source.clean())
        val list = (listOf(entry) + _entries.value).take(MAX)
        _entries.value = list
        runCatching {
            file.writeText(list.joinToString("\n") { "${it.time}\t${it.message}\t${it.source}" })
        }
    }

    fun toCsv(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        return buildString {
            append("time,event,source\n")
            entries.value.forEach { append("${fmt.format(Date(it.time))},${it.message},${it.source}\n") }
        }
    }

    private fun load(): List<LogEntry> = runCatching {
        if (!file.exists()) return@runCatching emptyList()
        file.readLines().mapNotNull { line ->
            val p = line.split('\t')
            val t = p.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            if (p.size == 3) LogEntry(t, p[1], p[2]) else null
        }
    }.getOrDefault(emptyList())

    private fun String.clean() = replace('\t', ' ').replace('\n', ' ').replace(',', ' ')

    companion object {
        private const val MAX = 200
    }
}
