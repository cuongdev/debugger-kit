package io.github.cuongdev.debuggerkit.core

import android.content.Context
import io.github.cuongdev.debuggerkit.R
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
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    @Synchronized
    fun add(message: String, source: String) {
        val entry = LogEntry(System.currentTimeMillis(), message.clean(), source.clean())
        val list = (listOf(entry) + _entries.value).take(MAX)
        _entries.value = list
        // Toggles happen on the main thread; keep disk writes off it.
        writer.execute {
            runCatching { file.writeText(list.joinToString("\n") { "${it.time}\t${it.message}\t${it.source}" }) }
        }
    }

    fun toCsv(context: Context): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        return buildString {
            append("time,event,source\n")
            entries.value.forEach {
                append("${fmt.format(Date(it.time))},${csv(describe(context, it.message))},${csv(it.source)}\n")
            }
        }
    }

    private fun csv(value: String) = "\"" + value.replace("\"", "\"\"") + "\""

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

        // Entries keep a language neutral event code and are translated when shown, so switching
        // the app language also changes older lines. Lines written by 1.0.0 builds before this
        // change hold plain text and are shown as they are.
        const val USB_ON = "usb_on"
        const val USB_OFF = "usb_off"
        const val WIFI_ON = "wifi_on"
        const val WIFI_OFF = "wifi_off"
        const val DEV_ON = "dev_on"
        const val DEV_OFF = "dev_off"
        const val AWAKE_ON = "awake_on"
        const val AWAKE_OFF = "awake_off"
        const val AUTO_OFF_DISABLED = "auto_off_disabled"
        private const val AUTO_OFF_PREFIX = "auto_off:"

        val ON_EVENTS = setOf(USB_ON, WIFI_ON, DEV_ON, AWAKE_ON)

        private val LABELS = mapOf(
            USB_ON to R.string.log_usb_on,
            USB_OFF to R.string.log_usb_off,
            WIFI_ON to R.string.log_wifi_on,
            WIFI_OFF to R.string.log_wifi_off,
            DEV_ON to R.string.log_dev_on,
            DEV_OFF to R.string.log_dev_off,
            AWAKE_ON to R.string.log_awake_on,
            AWAKE_OFF to R.string.log_awake_off,
            AUTO_OFF_DISABLED to R.string.log_auto_off_disabled,
        )

        fun autoOff(minutes: Int) = "$AUTO_OFF_PREFIX$minutes"

        fun describe(context: Context, event: String): String {
            LABELS[event]?.let { return context.getString(it) }
            if (event.startsWith(AUTO_OFF_PREFIX)) {
                event.removePrefix(AUTO_OFF_PREFIX).toIntOrNull()?.let { return context.getString(R.string.chip_auto_off_min, it) }
            }
            return event
        }
    }
}
