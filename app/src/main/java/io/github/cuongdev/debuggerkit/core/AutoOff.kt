package io.github.cuongdev.debuggerkit.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.cuongdev.debuggerkit.graph

/** Turns USB debugging off after a timer. Uses an inexact alarm so no extra permission is needed. */
class AutoOff(private val context: Context, private val prefs: Prefs) {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, AutoOffReceiver::class.java).setAction(AutoOffReceiver.ACTION_AUTO_OFF),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun start(minutes: Int) {
        if (minutes <= 0) {
            cancel()
            return
        }
        val total = minutes * 60_000L
        val at = System.currentTimeMillis() + total
        prefs.setAutoOff(at, total)
        schedule(at)
    }

    fun extend(minutes: Int) {
        val p = prefs.value
        if (p.autoOffAt <= 0) return
        val add = minutes * 60_000L
        val at = p.autoOffAt + add
        prefs.setAutoOff(at, p.autoOffTotal + add)
        schedule(at)
    }

    /**
     * Alarms can be dropped when the app is updated or killed. Called on every process start
     * so a running countdown always has a live alarm behind it.
     */
    fun resync(usbOn: Boolean) {
        val at = prefs.value.autoOffAt
        if (at <= 0) return
        if (!usbOn) cancel() else schedule(maxOf(at, System.currentTimeMillis() + 1_000L))
    }

    fun cancel() {
        alarms?.cancel(alarmIntent())
        prefs.setAutoOff(0, 0)
    }

    /** True when Android lets us fire the auto off alarm at the exact minute. */
    fun canExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms?.canScheduleExactAlarms() == true

    private fun schedule(at: Long) {
        val am = alarms ?: return
        val pi = alarmIntent()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            // Without "Alarms and reminders" access Android may still delay this by up to about 10 minutes.
            // The bubble service, when running, turns debugging off on time regardless.
            am.setWindow(AlarmManager.RTC_WAKEUP, at, 60_000L, pi)
        }
    }
}

class AutoOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val g = context.graph
        when (intent.action) {
            ACTION_AUTO_OFF ->
                if (g.controller.read().usb) g.controller.setUsb(false, "auto off") else g.autoOff.cancel()
            ACTION_TURN_OFF -> g.controller.setUsb(false, "notification")
            ACTION_EXTEND -> g.autoOff.extend(30)
        }
    }

    companion object {
        const val ACTION_AUTO_OFF = "io.github.cuongdev.debuggerkit.action.AUTO_OFF"
        const val ACTION_TURN_OFF = "io.github.cuongdev.debuggerkit.action.TURN_OFF"
        const val ACTION_EXTEND = "io.github.cuongdev.debuggerkit.action.EXTEND"
    }
}
