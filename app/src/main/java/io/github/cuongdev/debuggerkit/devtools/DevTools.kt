package io.github.cuongdev.debuggerkit.devtools

import android.content.ClipData
import androidx.annotation.StringRes
import io.github.cuongdev.debuggerkit.R
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.github.cuongdev.debuggerkit.core.ActivityLog
import io.github.cuongdev.debuggerkit.core.Keys
import io.github.cuongdev.debuggerkit.core.Prefs
import io.github.cuongdev.debuggerkit.core.SnapshotStore
import io.github.cuongdev.debuggerkit.core.SystemAccess
import io.github.cuongdev.debuggerkit.shell.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes dev panel shortcuts and presets.
 * Every write goes through [change], which snapshots the original value first.
 */
class DevTools(
    private val context: Context,
    private val sys: SystemAccess,
    private val shell: Shell,
    private val snapshot: SnapshotStore,
    private val log: ActivityLog,
    private val prefs: Prefs,
) {
    private fun str(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private interface Knob {
        val id: String
        val needsShell: Boolean
        suspend fun read(): String?
        suspend fun write(value: String?): Boolean
    }

    private inner class GlobalKnob(override val id: String) : Knob {
        override val needsShell = false
        override suspend fun read() = sys.globalString(id)
        override suspend fun write(value: String?) = sys.putGlobalString(id, value)
    }

    /** Settings.System. Private keys can only be written through the shell. */
    private inner class SystemKnob(private val key: String, private val shellOnly: Boolean) : Knob {
        override val id = "system:$key"
        override val needsShell = shellOnly
        override suspend fun read(): String? = sys.systemString(key)
            ?: shell.exec("settings get system $key")?.text?.trim()?.takeUnless { it.isEmpty() || it == "null" }

        override suspend fun write(value: String?): Boolean {
            if (!shellOnly && sys.putSystemString(key, value)) return true
            val cmd = if (value == null) "settings delete system $key" else "settings put system $key $value"
            return shell.exec(cmd)?.ok == true
        }
    }

    /** System property plus a poke so running apps pick it up (same as the Settings app does). */
    private inner class PropKnob(private val name: String) : Knob {
        override val id = "prop:$name"
        override val needsShell = true
        override suspend fun read() = shell.exec("getprop $name")?.text?.trim()?.takeUnless { it.isEmpty() }
        override suspend fun write(value: String?): Boolean {
            val v = value?.takeUnless { it.isEmpty() } ?: "false"
            return shell.exec("setprop $name $v && $SYSPROPS_POKE")?.ok == true
        }
    }

    private inner class NightKnob : Knob {
        override val id = "night"
        override val needsShell = true
        override suspend fun read(): String? = shell.exec("cmd uimode night")?.text
            ?.substringAfter(":", "")?.trim()?.takeUnless { it.isEmpty() }

        override suspend fun write(value: String?): Boolean {
            val v = value?.takeIf { it in NIGHT_VALUES } ?: "auto"
            return shell.exec("cmd uimode night $v")?.ok == true
        }
    }

    private inner class DemoKnob : Knob {
        override val id = "demo"
        override val needsShell = true
        override suspend fun read() = if (prefs.value.demoOn) "1" else "0"
        override suspend fun write(value: String?): Boolean {
            val on = value == "1"
            val out = shell.exec(if (on) DEMO_ENTER else DEMO_EXIT) ?: return false
            prefs.setDemoOn(on)
            return out.ok
        }
    }

    private val window = GlobalKnob(Keys.WINDOW_ANIM)
    private val transition = GlobalKnob(Keys.TRANSITION_ANIM)
    private val animator = GlobalKnob(Keys.ANIMATOR_DURATION)
    private val stayOn = GlobalKnob(Keys.STAY_ON)
    private val alwaysFinish = GlobalKnob(Keys.ALWAYS_FINISH)
    private val fontScale = SystemKnob(Keys.FONT_SCALE, shellOnly = false)
    private val touches = SystemKnob(Keys.SHOW_TOUCHES, shellOnly = true)
    private val pointer = SystemKnob(Keys.POINTER_LOCATION, shellOnly = true)
    private val layoutBounds = PropKnob("debug.layout")
    private val overdraw = PropKnob("debug.hwui.overdraw")
    private val profile = PropKnob("debug.hwui.profile")
    private val night = NightKnob()
    private val demo = DemoKnob()

    private val knobs: List<Knob>
        get() = listOf(
            window, transition, animator, stayOn, alwaysFinish, fontScale,
            touches, pointer, layoutBounds, overdraw, profile, night, demo,
        )

    private val animKnobs get() = listOf(window, transition, animator)

    fun shellReady(): Boolean = shell.available()

    private suspend fun change(knob: Knob, value: String?): Boolean {
        if (!snapshot.has(knob.id)) snapshot.save(knob.id, knob.read())
        return knob.write(value)
    }

    suspend fun readPanel(actions: List<DevAction>): List<PanelItem> = withContext(Dispatchers.IO) {
        val hasShell = shell.available()
        actions.map { read(it, hasShell) }
    }

    private suspend fun read(a: DevAction, hasShell: Boolean): PanelItem {
        val available = !a.needsShell || hasShell
        fun switchItem(on: Boolean) = PanelItem(a, onOff(on), on, available)
        return when (a) {
            DevAction.ANIMATIONS -> {
                val s = sys.globalFloat(Keys.WINDOW_ANIM, 1f)
                PanelItem(a, scaleLabel(s), s != 1f, true)
            }
            DevAction.SHOW_TAPS -> switchItem(touches.read() == "1")
            DevAction.POINTER -> switchItem(pointer.read() == "1")
            DevAction.KEEP_AWAKE -> switchItem(sys.globalInt(Keys.STAY_ON) != 0)
            DevAction.DONT_KEEP -> switchItem(sys.globalInt(Keys.ALWAYS_FINISH) == 1)
            DevAction.FONT_SCALE -> {
                val f = sys.systemString(Keys.FONT_SCALE)?.toFloatOrNull() ?: 1f
                PanelItem(a, "${number(f)}x", f != 1f, true)
            }
            DevAction.ADB_CONNECT -> PanelItem(a, sys.wifiIpv4() ?: str(R.string.val_no_wifi), false, true)
            DevAction.DEV_OPTIONS -> PanelItem(a, str(R.string.val_open), false, true)
            DevAction.LAYOUT_BOUNDS -> switchItem(hasShell && layoutBounds.read() == "true")
            DevAction.OVERDRAW -> switchItem(hasShell && overdraw.read() == "show")
            DevAction.GPU_BARS -> switchItem(hasShell && profile.read() == "visual_bars")
            DevAction.DARK_MODE -> {
                val v = if (hasShell) night.read() else null
                PanelItem(a, v ?: "?", v == "yes", available)
            }
            DevAction.DEMO_MODE -> switchItem(prefs.value.demoOn)
        }
    }

    suspend fun perform(a: DevAction): Outcome = withContext(Dispatchers.IO) {
        if (a.needsShell && !shell.available()) return@withContext Outcome(str(R.string.msg_needs_shell))
        when (a) {
            DevAction.ANIMATIONS -> {
                val cur = sys.globalFloat(Keys.WINDOW_ANIM, 1f)
                val next = when {
                    cur >= 1f -> "0.5"
                    cur >= 0.5f -> "0"
                    else -> "1"
                }
                val ok = animKnobs.map { change(it, next) }.all { it }
                done(ok, str(R.string.out_animations, scaleLabel(next.toFloat())))
            }
            DevAction.SHOW_TAPS -> flip(touches, "1", "0", str(R.string.dev_taps))
            DevAction.POINTER -> flip(pointer, "1", "0", str(R.string.dev_pointer))
            DevAction.KEEP_AWAKE -> {
                val on = sys.globalInt(Keys.STAY_ON) == 0
                done(change(stayOn, if (on) "7" else "0"), str(R.string.out_toggle, str(R.string.dev_stay_awake), onOff(on)))
            }
            DevAction.DONT_KEEP -> {
                val on = sys.globalInt(Keys.ALWAYS_FINISH) != 1
                done(change(alwaysFinish, if (on) "1" else "0"), str(R.string.out_toggle, str(R.string.dev_dont_keep), onOff(on)))
            }
            DevAction.FONT_SCALE -> {
                val cur = sys.systemString(Keys.FONT_SCALE)?.toFloatOrNull() ?: 1f
                val next = when {
                    cur < 1.3f -> "1.3"
                    cur < 2f -> "2.0"
                    else -> "1.0"
                }
                done(change(fontScale, next), str(R.string.out_font_scale, number(next.toFloat())))
            }
            DevAction.ADB_CONNECT -> adbConnect()
            DevAction.DEV_OPTIONS -> {
                val opened = openDeveloperOptions()
                Outcome(if (opened) null else str(R.string.out_devopts_missing), closePanel = opened)
            }
            DevAction.LAYOUT_BOUNDS -> flip(layoutBounds, "true", "false", str(R.string.dev_bounds))
            DevAction.OVERDRAW -> flip(overdraw, "show", "false", str(R.string.dev_overdraw))
            DevAction.GPU_BARS -> flip(profile, "visual_bars", "false", str(R.string.dev_gpubars))
            DevAction.DARK_MODE -> flip(night, "yes", "no", str(R.string.dev_dark))
            DevAction.DEMO_MODE -> {
                val on = !prefs.value.demoOn
                done(change(demo, if (on) "1" else "0"), str(R.string.out_toggle, str(R.string.dev_demo), onOff(on)))
            }
        }
    }

    private suspend fun flip(knob: Knob, onValue: String, offValue: String, label: String): Outcome {
        val on = knob.read() != onValue
        return done(change(knob, if (on) onValue else offValue), str(R.string.out_toggle, label, onOff(on)))
    }

    suspend fun applyPreset(p: Preset): Outcome = withContext(Dispatchers.IO) {
        val hasShell = shell.available()
        var changed = 0
        var skipped = 0
        suspend fun set(k: Knob, v: String) {
            if (k.needsShell && !hasShell) {
                skipped++
                return
            }
            if (change(k, v)) changed++
        }
        when (p) {
            Preset.UI_TEST -> {
                animKnobs.forEach { set(it, "0") }
                set(stayOn, "7")
                set(touches, "1")
            }
            Preset.SCREENSHOT -> {
                set(demo, "1")
                set(night, "no")
                set(touches, "0")
            }
            Preset.PERF -> {
                animKnobs.forEach { set(it, "1") }
                set(profile, "visual_bars")
                set(overdraw, "show")
            }
        }
        log.add(str(R.string.log_preset_applied, str(p.title)), "panel")
        val msg = buildString {
            append(str(R.string.out_preset_applied, str(p.title), changed))
            if (skipped > 0) append(" ").append(str(R.string.out_preset_skipped, skipped))
        }
        Outcome(msg)
    }

    /** Writes back every value saved before the first change, then forgets the snapshot. */
    suspend fun restore(): Outcome = withContext(Dispatchers.IO) {
        val saved = snapshot.all()
        if (saved.isEmpty()) return@withContext Outcome(str(R.string.out_nothing_restore))
        val byId = knobs.associateBy { it.id }
        val hasShell = shell.available()
        var restored = 0
        var left = 0
        for ((id, value) in saved) {
            val k = byId[id]
            if (k == null) {
                snapshot.remove(id)
                continue
            }
            if (k.needsShell && !hasShell) {
                left++
                continue
            }
            if (k.write(value)) {
                snapshot.remove(id)
                restored++
            } else {
                left++
            }
        }
        log.add(str(R.string.log_restored, restored), "panel")
        Outcome(
            if (left == 0) str(R.string.out_restored_all, restored)
            else str(R.string.out_restored_partial, restored, left),
        )
    }

    // Target app

    fun isValidPackage(pkg: String): Boolean = PACKAGE_REGEX.matches(pkg)

    suspend fun forceStop(pkg: String): Outcome = targetCommand(pkg, "am force-stop $pkg", str(R.string.out_force_stopped, pkg))

    suspend fun clearData(pkg: String): Outcome = targetCommand(pkg, "pm clear $pkg", str(R.string.out_cleared, pkg))

    suspend fun restart(pkg: String): Outcome = withContext(Dispatchers.IO) {
        if (!isValidPackage(pkg)) return@withContext Outcome(str(R.string.out_bad_package))
        if (!shell.available()) return@withContext Outcome(str(R.string.msg_needs_shell))
        shell.exec("am force-stop $pkg")
        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return@withContext Outcome(str(R.string.out_no_launcher, pkg))
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        runCatching { context.startActivity(launch) }
        log.add(str(R.string.out_restarted, pkg), "panel")
        Outcome(str(R.string.out_restarted, pkg), closePanel = true)
    }

    private suspend fun targetCommand(pkg: String, cmd: String, msg: String): Outcome = withContext(Dispatchers.IO) {
        if (!isValidPackage(pkg)) return@withContext Outcome(str(R.string.out_bad_package))
        if (!shell.available()) return@withContext Outcome(str(R.string.msg_needs_shell))
        done(shell.exec(cmd)?.ok == true, msg)
    }

    // Helpers

    private suspend fun adbConnect(): Outcome {
        if (sys.globalInt(Keys.ADB_WIFI_ENABLED) != 1) return Outcome(str(R.string.out_wireless_first))
        val ip = sys.wifiIpv4() ?: return Outcome(str(R.string.out_no_wifi))
        val port = if (shell.available()) {
            shell.exec("getprop service.adb.tls.port")?.text?.trim()?.toIntOrNull()?.takeIf { it > 0 }
        } else {
            null
        }
        val text = if (port != null) "adb connect $ip:$port" else ip
        withContext(Dispatchers.Main) {
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("adb connect", text))
        }
        return Outcome(
            if (port != null) str(R.string.out_copied, text)
            else str(R.string.out_copied_ip, ip),
        )
    }

    fun openDeveloperOptions(): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)

    private fun done(ok: Boolean, msg: String): Outcome {
        if (ok) log.add(msg, "panel")
        return Outcome(if (ok) msg else str(R.string.out_failed, msg))
    }

    private fun onOff(on: Boolean) = str(if (on) R.string.val_on else R.string.val_off)

    private fun scaleLabel(s: Float) = if (s == 0f) str(R.string.val_off) else "${number(s)}x"

    private fun number(f: Float): String =
        if (f == f.toInt().toFloat()) f.toInt().toString() else "%.1f".format(java.util.Locale.US, f)

    companion object {

        /** IBinder.SYSPROPS_TRANSACTION, makes running apps reread debug system properties. */
        private const val SYSPROPS_POKE = "service call activity 1599295570 > /dev/null"

        private val NIGHT_VALUES = setOf("yes", "no", "auto")
        private val PACKAGE_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

        private const val DEMO = "am broadcast -a com.android.systemui.demo -e command"
        private val DEMO_ENTER = listOf(
            "settings put global sysui_demo_allowed 1",
            "$DEMO enter",
            "$DEMO clock -e hhmm 1200",
            "$DEMO battery -e level 100 -e plugged false",
            "$DEMO network -e wifi show -e level 4",
            "$DEMO network -e mobile show -e datatype none -e level 4",
            "$DEMO notifications -e visible false",
        ).joinToString(" && ")
        private const val DEMO_EXIT = "$DEMO exit"
    }
}
