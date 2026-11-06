package io.github.cuongdev.debuggerkit.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class KeepMode { CHARGING, ALWAYS }

enum class ShellBackend { NONE, SHIZUKU, ROOT }

/** What to turn off while a listed app is open. DEV also hides Developer options, which most banking apps check. */
enum class RuleMode { USB, DEV }

data class AppPrefs(
    val autoOffEnabled: Boolean,
    val autoOffMinutes: Int,
    val offOnScreenLock: Boolean,
    val wirelessGuard: Boolean,
    val keepScreenOn: Boolean,
    val keepMode: KeepMode,
    val restoreScreen: Boolean,
    val bubbleEnabled: Boolean,
    val bubbleSize: Int,
    val idleOpacity: Int,
    val dockWhenIdle: Boolean,
    val bubbleKeepScreenOn: Boolean,
    val bubbleRight: Boolean,
    val bubbleY: Int,
    val panelActions: Set<String>,
    val targetPackage: String,
    val shellBackend: ShellBackend,
    val tileRequireUnlock: Boolean,
    val tilesAdded: Set<String>,
    val autoOffAt: Long,
    val autoOffTotal: Long,
    val stayOnBefore: Int,
    val demoOn: Boolean,
    val appRulesEnabled: Boolean,
    val appRules: Map<String, RuleMode>,
    /** Package that made us turn debugging off, plus what we turned off, so we can put it back. */
    val holdPkg: String,
    val holdUsb: Boolean,
    val holdWifi: Boolean,
    val holdDev: Boolean,
)

/** App configuration. SharedPreferences so tiles and receivers can read it synchronously. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("debugkit", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val flow: StateFlow<AppPrefs> = state.asStateFlow()

    /** Always the latest committed values. */
    val value: AppPrefs get() = load()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> state.value = load() }

    init {
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun load(): AppPrefs = AppPrefs(
        // Older builds stored "never" as 0 minutes.
        autoOffEnabled = sp.getBoolean(AUTO_OFF_ENABLED, sp.getInt(AUTO_OFF, 30) > 0),
        autoOffMinutes = sp.getInt(AUTO_OFF, 30).let { if (it <= 0) 30 else it },
        offOnScreenLock = sp.getBoolean(OFF_ON_LOCK, false),
        wirelessGuard = sp.getBoolean(WIRELESS_GUARD, true),
        keepScreenOn = sp.getBoolean(KEEP_ON, true),
        keepMode = runCatching { KeepMode.valueOf(sp.getString(KEEP_MODE, null) ?: "") }
            .getOrDefault(KeepMode.CHARGING),
        restoreScreen = sp.getBoolean(RESTORE_SCREEN, true),
        bubbleEnabled = sp.getBoolean(BUBBLE, false),
        bubbleSize = sp.getInt(BUBBLE_SIZE, 1),
        idleOpacity = sp.getInt(IDLE_OPACITY, 60),
        dockWhenIdle = sp.getBoolean(DOCK, true),
        bubbleKeepScreenOn = sp.getBoolean(BUBBLE_KEEP_ON, false),
        bubbleRight = sp.getBoolean(BUBBLE_RIGHT, true),
        bubbleY = sp.getInt(BUBBLE_Y, -1),
        panelActions = sp.getStringSet(PANEL, null)?.toSet() ?: DEFAULT_PANEL,
        targetPackage = sp.getString(TARGET, "") ?: "",
        shellBackend = runCatching { ShellBackend.valueOf(sp.getString(SHELL, null) ?: "") }
            .getOrDefault(ShellBackend.NONE),
        tileRequireUnlock = sp.getBoolean(TILE_UNLOCK, true),
        tilesAdded = sp.getStringSet(TILES, null)?.toSet() ?: emptySet(),
        autoOffAt = sp.getLong(AUTO_OFF_AT, 0L),
        autoOffTotal = sp.getLong(AUTO_OFF_TOTAL, 0L),
        stayOnBefore = sp.getInt(STAY_ON_BEFORE, -1),
        demoOn = sp.getBoolean(DEMO_ON, false),
        appRulesEnabled = sp.getBoolean(RULES_ON, false),
        appRules = sp.getStringSet(RULES, null).orEmpty().mapNotNull { entry ->
            val i = entry.lastIndexOf('|')
            if (i <= 0) return@mapNotNull null
            val mode = runCatching { RuleMode.valueOf(entry.substring(i + 1)) }.getOrDefault(RuleMode.DEV)
            entry.substring(0, i) to mode
        }.toMap(),
        holdPkg = sp.getString(HOLD_PKG, "") ?: "",
        holdUsb = sp.getBoolean(HOLD_USB, false),
        holdWifi = sp.getBoolean(HOLD_WIFI, false),
        holdDev = sp.getBoolean(HOLD_DEV, false),
    )

    private inline fun edit(block: SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(block).apply()
        state.value = load()
    }

    fun setAutoOffConfig(enabled: Boolean, minutes: Int) =
        edit { putBoolean(AUTO_OFF_ENABLED, enabled); putInt(AUTO_OFF, minutes.coerceAtLeast(1)) }
    fun setOffOnScreenLock(v: Boolean) = edit { putBoolean(OFF_ON_LOCK, v) }
    fun setWirelessGuard(v: Boolean) = edit { putBoolean(WIRELESS_GUARD, v) }
    fun setKeepScreenOn(v: Boolean) = edit { putBoolean(KEEP_ON, v) }
    fun setKeepMode(v: KeepMode) = edit { putString(KEEP_MODE, v.name) }
    fun setRestoreScreen(v: Boolean) = edit { putBoolean(RESTORE_SCREEN, v) }
    fun setBubbleEnabled(v: Boolean) = edit { putBoolean(BUBBLE, v) }
    fun setBubbleSize(v: Int) = edit { putInt(BUBBLE_SIZE, v.coerceIn(0, 2)) }
    fun setIdleOpacity(v: Int) = edit { putInt(IDLE_OPACITY, v.coerceIn(20, 100)) }
    fun setDockWhenIdle(v: Boolean) = edit { putBoolean(DOCK, v) }
    fun setBubbleKeepScreenOn(v: Boolean) = edit { putBoolean(BUBBLE_KEEP_ON, v) }
    fun setBubblePosition(right: Boolean, y: Int) = edit { putBoolean(BUBBLE_RIGHT, right); putInt(BUBBLE_Y, y) }
    fun setTargetPackage(v: String) = edit { putString(TARGET, v.trim()) }
    fun setShellBackend(v: ShellBackend) = edit { putString(SHELL, v.name) }
    fun setTileRequireUnlock(v: Boolean) = edit { putBoolean(TILE_UNLOCK, v) }
    fun setAutoOff(at: Long, total: Long) = edit { putLong(AUTO_OFF_AT, at); putLong(AUTO_OFF_TOTAL, total) }
    fun setStayOnBefore(v: Int) = edit { putInt(STAY_ON_BEFORE, v) }
    fun setDemoOn(v: Boolean) = edit { putBoolean(DEMO_ON, v) }

    fun setAppRulesEnabled(v: Boolean) = edit { putBoolean(RULES_ON, v) }

    /** Adds or updates a rule. A null mode removes the app from the list. */
    fun setAppRule(pkg: String, mode: RuleMode?) {
        val next = value.appRules.toMutableMap()
        if (mode == null) next.remove(pkg) else next[pkg] = mode
        edit { putStringSet(RULES, next.map { (p, m) -> "$p|${m.name}" }.toSet()) }
    }

    fun setHold(pkg: String, usb: Boolean, wifi: Boolean, dev: Boolean) = edit {
        putString(HOLD_PKG, pkg)
        putBoolean(HOLD_USB, usb)
        putBoolean(HOLD_WIFI, wifi)
        putBoolean(HOLD_DEV, dev)
    }

    fun clearHold() = setHold("", usb = false, wifi = false, dev = false)

    fun setPanelAction(id: String, on: Boolean) {
        val cur = value.panelActions
        edit { putStringSet(PANEL, if (on) cur + id else cur - id) }
    }

    fun setTileAdded(id: String, added: Boolean) {
        val cur = value.tilesAdded
        if ((id in cur) == added) return
        edit { putStringSet(TILES, if (added) cur + id else cur - id) }
    }

    companion object {
        private const val AUTO_OFF = "auto_off_minutes"
        private const val AUTO_OFF_ENABLED = "auto_off_enabled"
        private const val OFF_ON_LOCK = "off_on_screen_lock"
        private const val WIRELESS_GUARD = "wireless_guard"
        private const val KEEP_ON = "keep_screen_on"
        private const val KEEP_MODE = "keep_mode"
        private const val RESTORE_SCREEN = "restore_screen"
        private const val BUBBLE = "bubble_enabled"
        private const val BUBBLE_SIZE = "bubble_size"
        private const val IDLE_OPACITY = "idle_opacity"
        private const val DOCK = "dock_when_idle"
        private const val BUBBLE_KEEP_ON = "bubble_keep_screen_on"
        private const val BUBBLE_RIGHT = "bubble_right"
        private const val BUBBLE_Y = "bubble_y"
        private const val PANEL = "panel_actions"
        private const val TARGET = "target_package"
        private const val SHELL = "shell_backend"
        private const val TILE_UNLOCK = "tile_require_unlock"
        private const val TILES = "tiles_added"
        private const val AUTO_OFF_AT = "auto_off_at"
        private const val AUTO_OFF_TOTAL = "auto_off_total"
        private const val STAY_ON_BEFORE = "stay_on_before"
        private const val DEMO_ON = "demo_on"
        private const val RULES_ON = "app_rules_enabled"
        private const val RULES = "app_rules"
        private const val HOLD_PKG = "hold_pkg"
        private const val HOLD_USB = "hold_usb"
        private const val HOLD_WIFI = "hold_wifi"
        private const val HOLD_DEV = "hold_dev"

        val DEFAULT_PANEL = setOf("anim", "taps", "pointer", "awake", "dka", "adbc", "bounds", "overdraw", "dark")
    }
}
