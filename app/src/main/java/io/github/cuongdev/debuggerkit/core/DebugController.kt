package io.github.cuongdev.debuggerkit.core

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single entry point for every debug toggle (app, tiles, bubble, notification).
 * Keeps auto off, keep awake and the activity log consistent no matter where a change starts.
 */
class DebugController(
    private val context: Context,
    private val sys: SystemAccess,
    private val prefs: Prefs,
    private val log: ActivityLog,
    private val autoOff: AutoOff,
) {
    enum class Result { OK, NO_PERMISSION, BLOCKED, FAILED }

    private val _state = MutableStateFlow(read())
    val state: StateFlow<DebugState> = _state.asStateFlow()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    init {
        WATCHED.forEach { key ->
            context.contentResolver.registerContentObserver(Settings.Global.getUriFor(key), false, observer)
        }
    }

    fun refresh() {
        _state.value = read()
    }

    fun read(): DebugState = DebugState(
        hasPermission = sys.hasSecureSettings(),
        blocked = sys.debuggingBlockedByPolicy(),
        usb = sys.globalInt(Keys.ADB_ENABLED) == 1,
        wireless = sys.globalInt(Keys.ADB_WIFI_ENABLED) == 1,
        devOptions = sys.globalInt(Keys.DEV_SETTINGS) == 1,
        stayOn = sys.globalInt(Keys.STAY_ON),
    )

    fun setUsb(on: Boolean, source: String): Result {
        checkDebugAccess()?.let { return it }
        val target = if (on) 1 else 0
        if (on && sys.globalInt(Keys.DEV_SETTINGS) != 1) sys.putGlobalInt(Keys.DEV_SETTINGS, 1)
        sys.putGlobalInt(Keys.ADB_ENABLED, target)
        if (sys.globalInt(Keys.ADB_ENABLED) != target) {
            refresh()
            return Result.FAILED
        }
        val p = prefs.value
        if (on) {
            if (p.autoOffEnabled) autoOff.start(p.autoOffMinutes) else autoOff.cancel()
            if (p.keepScreenOn && p.keepMode == KeepMode.CHARGING) holdStayOn()
        } else {
            autoOff.cancel()
            if (p.restoreScreen) releaseStayOn()
        }
        log.add(if (on) ActivityLog.USB_ON else ActivityLog.USB_OFF, source)
        refresh()
        return Result.OK
    }

    fun setWireless(on: Boolean, source: String): Result {
        checkDebugAccess()?.let { return it }
        val target = if (on) 1 else 0
        if (on && sys.globalInt(Keys.DEV_SETTINGS) != 1) sys.putGlobalInt(Keys.DEV_SETTINGS, 1)
        sys.putGlobalInt(Keys.ADB_WIFI_ENABLED, target)
        refresh()
        if (sys.globalInt(Keys.ADB_WIFI_ENABLED) != target) return Result.FAILED
        log.add(if (on) ActivityLog.WIFI_ON else ActivityLog.WIFI_OFF, source)
        return Result.OK
    }

    fun setDevOptions(on: Boolean, source: String): Result {
        checkDebugAccess()?.let { return it }
        if (!on) {
            // Same as the system Settings app: hiding the menu also stops debugging.
            sys.putGlobalInt(Keys.ADB_ENABLED, 0)
            sys.putGlobalInt(Keys.ADB_WIFI_ENABLED, 0)
            autoOff.cancel()
            if (prefs.value.restoreScreen) releaseStayOn()
        }
        sys.putGlobalInt(Keys.DEV_SETTINGS, if (on) 1 else 0)
        refresh()
        if (sys.globalInt(Keys.DEV_SETTINGS) != (if (on) 1 else 0)) return Result.FAILED
        log.add(if (on) ActivityLog.DEV_ON else ActivityLog.DEV_OFF, source)
        return Result.OK
    }

    fun setKeepAwake(on: Boolean, source: String): Result {
        if (!sys.hasSecureSettings()) return Result.NO_PERMISSION
        sys.putGlobalInt(Keys.STAY_ON, if (on) STAY_ON_ALL else 0)
        prefs.setStayOnBefore(-1)
        refresh()
        log.add(if (on) ActivityLog.AWAKE_ON else ActivityLog.AWAKE_OFF, source)
        return Result.OK
    }

    /**
     * Turns the auto off timer on or off. Turning it off cancels a running countdown right away,
     * so USB debugging then stays on until the user turns it off.
     */
    fun configureAutoOff(enabled: Boolean, minutes: Int = prefs.value.autoOffMinutes, source: String) {
        prefs.setAutoOffConfig(enabled, minutes)
        if (!enabled) autoOff.cancel() else if (read().usb) autoOff.start(minutes)
        log.add(if (enabled) ActivityLog.autoOff(minutes) else ActivityLog.AUTO_OFF_DISABLED, source)
    }

    private fun checkDebugAccess(): Result? = when {
        sys.debuggingBlockedByPolicy() -> Result.BLOCKED
        !sys.hasSecureSettings() -> Result.NO_PERMISSION
        else -> null
    }

    /** Keep the screen on while plugged in, remembering the user's own value. */
    private fun holdStayOn() {
        val current = sys.globalInt(Keys.STAY_ON)
        if (prefs.value.stayOnBefore < 0) prefs.setStayOnBefore(current)
        if (current != STAY_ON_ALL) sys.putGlobalInt(Keys.STAY_ON, STAY_ON_ALL)
    }

    private fun releaseStayOn() {
        val before = prefs.value.stayOnBefore
        if (before < 0) return
        sys.putGlobalInt(Keys.STAY_ON, before)
        prefs.setStayOnBefore(-1)
    }

    companion object {
        /** AC, USB and wireless charging (BatteryManager plug flags 1 | 2 | 4). */
        const val STAY_ON_ALL = 7
        private val WATCHED = listOf(Keys.ADB_ENABLED, Keys.ADB_WIFI_ENABLED, Keys.DEV_SETTINGS, Keys.STAY_ON)
    }
}
