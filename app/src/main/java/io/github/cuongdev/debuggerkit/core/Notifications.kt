package io.github.cuongdev.debuggerkit.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.cuongdev.debuggerkit.R
import io.github.cuongdev.debuggerkit.bubble.BubbleService
import io.github.cuongdev.debuggerkit.ui.MainActivity
import java.text.DateFormat
import java.util.Date

object Notifications {
    const val CHANNEL_SESSION = "session"
    const val ID_SESSION = 10

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(CHANNEL_SESSION, context.getString(R.string.floating_button), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    fun session(context: Context, state: DebugState, prefs: AppPrefs, holdLabel: String?): Notification {
        val title = when {
            holdLabel != null -> context.getString(R.string.notif_paused_title, holdLabel)
            state.usb -> context.getString(R.string.notif_usb_on)
            prefs.bubbleEnabled -> context.getString(R.string.notif_bubble_active)
            else -> context.getString(R.string.notif_rules_watching)
        }
        val text = when {
            holdLabel != null -> context.getString(R.string.notif_paused_text)
            state.usb && prefs.autoOffAt > 0 ->
                context.getString(R.string.notif_turns_off_at, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(prefs.autoOffAt)))
            state.usb -> context.getString(R.string.notif_no_auto_off)
            prefs.bubbleEnabled -> context.getString(R.string.notif_long_press)
            else -> context.getString(R.string.notif_apps_count, prefs.appRules.size)
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(context, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
        if (state.usb) b.addAction(0, context.getString(R.string.notif_turn_off), receiver(context, AutoOffReceiver.ACTION_TURN_OFF, 2))
        if (state.usb && prefs.autoOffAt > 0) b.addAction(0, context.getString(R.string.notif_add_30), receiver(context, AutoOffReceiver.ACTION_EXTEND, 3))
        b.addAction(0, context.getString(R.string.dev_panel), PendingIntent.getService(
            context, 4,
            Intent(context, BubbleService::class.java).setAction(BubbleService.ACTION_SHOW_PANEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ))
        return b.build()
    }

    private fun receiver(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context, code,
        Intent(context, AutoOffReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
