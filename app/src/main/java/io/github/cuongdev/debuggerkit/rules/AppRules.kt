package io.github.cuongdev.debuggerkit.rules

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Process
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import io.github.cuongdev.debuggerkit.R
import io.github.cuongdev.debuggerkit.core.AppPrefs
import io.github.cuongdev.debuggerkit.core.DebugController
import io.github.cuongdev.debuggerkit.core.Prefs
import io.github.cuongdev.debuggerkit.core.RuleMode

/**
 * "Debug off per app": while a listed app is in front, USB debugging (or all of Developer options)
 * is really turned off, then put back when the user leaves the app.
 * Nothing is faked: the listed app reads the true system value.
 */
class AppRules(
    private val context: Context,
    private val prefs: Prefs,
    private val controller: DebugController,
) {
    private var foreground: String? = null
    private var lastEventTs = 0L
    private var restoreAt = 0L
    private var suppressRestoreUntil = 0L
    private var usageOk = false
    private var usageCheckedAt = 0L
    private val power = context.getSystemService(android.os.PowerManager::class.java)

    fun isActive(p: AppPrefs = prefs.value): Boolean = p.appRulesEnabled && p.appRules.isNotEmpty()

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        // MODE_DEFAULT falls back to the permission itself, which "adb install -g" or pm grant can grant.
        return mode == AppOpsManager.MODE_ALLOWED ||
            (mode == AppOpsManager.MODE_DEFAULT &&
                context.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED)
    }

    /** Called about every 700 ms by the background service. */
    @Synchronized
    fun poll() {
        val p = prefs.flow.value
        if (!isActive(p)) return
        // Nothing can come to the front while the screen is off, so skip the binder calls.
        if (power?.isInteractive == false) return
        val now = System.currentTimeMillis()
        if (now - usageCheckedAt > USAGE_RECHECK_MS) {
            usageOk = hasUsageAccess()
            usageCheckedAt = now
        }
        if (!usageOk) return
        val fg = readForeground(now) ?: foreground
        if (fg != foreground) {
            foreground = fg
            val mode = fg?.let { p.appRules[it] }
            if (fg != null && mode != null) {
                restoreAt = 0
                apply(fg, mode, "app rule")
            }
        }
        val inListedApp = fg != null && fg in p.appRules
        val held = prefs.flow.value.holdPkg.isNotEmpty()
        when {
            inListedApp || !held -> restoreAt = 0
            now < suppressRestoreUntil -> Unit
            // Short grace period, so a quick hop to an OTP or SMS app does not flip debugging back on.
            restoreAt == 0L -> restoreAt = now + RESTORE_DELAY_MS
            now >= restoreAt -> {
                restoreAt = 0
                restore()
            }
        }
    }

    /** Turns debugging off for [pkg] and remembers exactly what was on. */
    @Synchronized
    fun apply(pkg: String, mode: RuleMode, source: String) {
        val s = controller.read()
        if (!s.ready) return
        val p = prefs.value
        var usb = p.holdUsb
        var wifi = p.holdWifi
        var dev = p.holdDev
        if (mode == RuleMode.DEV && s.devOptions) {
            dev = true
            usb = usb || s.usb
            wifi = wifi || s.wireless
            controller.setDevOptions(false, source)
        } else {
            if (s.usb) {
                usb = true
                controller.setUsb(false, source)
            }
            if (s.wireless) {
                wifi = true
                controller.setWireless(false, source)
            }
        }
        if (usb || wifi || dev) prefs.setHold(pkg, usb, wifi, dev)
    }

    /** Puts back only what [apply] turned off. */
    @Synchronized
    fun restore() {
        val p = prefs.value
        if (p.holdPkg.isEmpty()) return
        if (p.holdDev) controller.setDevOptions(true, "app rule")
        if (p.holdUsb) controller.setUsb(true, "app rule")
        if (p.holdWifi) controller.setWireless(true, "app rule")
        prefs.clearHold()
    }

    /**
     * Turns debugging off first, then opens the app, so even apps that check at startup see it off.
     * Returns a message to show, or null when the watcher will turn debugging back on by itself.
     */
    fun launchSafely(from: Context, pkg: String): String? {
        val p = prefs.value
        val mode = p.appRules[pkg] ?: return context.getString(R.string.rules_not_listed)
        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return context.getString(R.string.rules_app_not_found)
        synchronized(this) {
            apply(pkg, mode, "safe launch")
            foreground = pkg
            restoreAt = 0
            suppressRestoreUntil = System.currentTimeMillis() + LAUNCH_GRACE_MS
        }
        from.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return if (isActive(p) && hasUsageAccess()) null
        else context.getString(R.string.rules_stays_off)
    }

    fun label(pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** Home screen shortcut that opens [pkg] with debugging off. */
    fun pinShortcut(from: Context, pkg: String): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(from)) return false
        val pm = context.packageManager
        val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return false
        val name = pm.getApplicationLabel(info).toString()
        val icon = runCatching { badged(pm.getApplicationIcon(info).toBitmap(192, 192)) }.getOrNull()
        val intent = Intent(context, SafeLaunchActivity::class.java)
            .setAction(SafeLaunchActivity.ACTION)
            .putExtra(SafeLaunchActivity.EXTRA_PACKAGE, pkg)
        val shortcut = ShortcutInfoCompat.Builder(context, "safe_$pkg")
            .setShortLabel(name)
            .setLongLabel(context.getString(R.string.rules_shortcut_long, name))
            .setIcon(if (icon != null) IconCompat.createWithBitmap(icon) else IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(from, shortcut, null)
    }

    /** Small dark badge with a mint ring, so the shortcut is not mistaken for the app's own icon. */
    private fun badged(src: Bitmap): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val s = out.width.toFloat()
        val r = s * 0.2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFF0E1113.toInt()
        c.drawCircle(s - r, s - r, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.28f
        paint.color = 0xFF7EE2A8.toInt()
        c.drawCircle(s - r, s - r, r * 0.55f, paint)
        return out
    }

    private fun readForeground(now: Long): String? {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val from = if (lastEventTs == 0L) now - LOOKBACK_MS else lastEventTs
        val events = runCatching { usm.queryEvents(from, now) }.getOrNull() ?: return null
        val e = UsageEvents.Event()
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            @Suppress("DEPRECATION")
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && e.packageName !in TRANSIENT) {
                latest = e.packageName
            }
            if (e.timeStamp > lastEventTs) lastEventTs = e.timeStamp
        }
        return latest
    }

    companion object {
        private const val LOOKBACK_MS = 10 * 60_000L
        private const val RESTORE_DELAY_MS = 3_000L
        private const val LAUNCH_GRACE_MS = 5_000L
        private const val USAGE_RECHECK_MS = 15_000L

        /** System screens that can pop up over a listed app without the user leaving it. */
        private val TRANSIENT = setOf(
            "android",
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
        )
    }
}
