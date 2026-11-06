package io.github.cuongdev.debuggerkit.tile

import android.annotation.SuppressLint
import io.github.cuongdev.debuggerkit.R
import android.app.PendingIntent
import android.content.Intent
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import io.github.cuongdev.debuggerkit.core.DebugController
import io.github.cuongdev.debuggerkit.core.DebugState
import io.github.cuongdev.debuggerkit.core.Keys
import io.github.cuongdev.debuggerkit.graph
import io.github.cuongdev.debuggerkit.ui.MainActivity

/** Shared behavior: live state while the shade is open, unlock guard, and permission routing. */
abstract class BaseTile : TileService() {
    protected val g get() = applicationContext.graph
    abstract val tileKey: String
    private var observer: ContentObserver? = null

    override fun onTileAdded() {
        g.prefs.setTileAdded(tileKey, true)
        update()
    }

    override fun onTileRemoved() {
        g.prefs.setTileAdded(tileKey, false)
    }

    override fun onStartListening() {
        g.prefs.setTileAdded(tileKey, true)
        update()
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = update()
        }
        WATCHED.forEach { contentResolver.registerContentObserver(Settings.Global.getUriFor(it), false, obs) }
        observer = obs
    }

    override fun onStopListening() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
    }

    override fun onClick() {
        if (isLocked && g.prefs.value.tileRequireUnlock) unlockAndRun { handleClick() } else handleClick()
    }

    protected abstract fun handleClick()
    protected abstract fun update()

    protected fun render(active: Boolean, enabled: Boolean, subtitle: String?) {
        val t = qsTile ?: return
        t.state = when {
            !enabled -> Tile.STATE_UNAVAILABLE
            active -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) t.subtitle = subtitle
        t.updateTile()
    }

    /** Subtitle when the app cannot act yet. A blocked tile is shown as unavailable. */
    protected fun accessSubtitle(s: DebugState): String? = when {
        s.blocked -> getString(R.string.tile_blocked)
        !s.hasPermission -> getString(R.string.tile_tap_setup)
        else -> null
    }

    protected fun handleResult(r: DebugController.Result) {
        when (r) {
            DebugController.Result.OK -> Unit
            DebugController.Result.NO_PERMISSION -> openActivity(
                Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "setup"),
            )
            DebugController.Result.BLOCKED -> toast(getString(R.string.msg_blocked))
            DebugController.Result.FAILED -> toast(getString(R.string.msg_failed))
        }
        update()
    }

    // The Intent overload is only used below Android 14, where it is the only option.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    protected fun openActivity(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun toast(msg: String) = Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()

    private companion object {
        val WATCHED = listOf(Keys.ADB_ENABLED, Keys.ADB_WIFI_ENABLED, Keys.DEV_SETTINGS, Keys.STAY_ON)
    }
}

class UsbTile : BaseTile() {
    override val tileKey = "usb"

    override fun update() {
        val s = g.controller.read()
        val p = g.prefs.value
        val sub = accessSubtitle(s) ?: when {
            s.usb && p.autoOffAt > 0 -> {
                val min = ((p.autoOffAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
                getString(R.string.tile_auto_off_in, min)
            }
            s.usb -> getString(R.string.state_on)
            else -> getString(R.string.state_off)
        }
        render(s.usb, !s.blocked, sub)
    }

    override fun handleClick() {
        handleResult(g.controller.setUsb(!g.controller.read().usb, "tile"))
    }
}

class WirelessTile : BaseTile() {
    override val tileKey = "wifi"

    override fun update() {
        val s = g.controller.read()
        val sub = accessSubtitle(s) ?: if (s.wireless) (g.sys.wifiIpv4() ?: getString(R.string.state_on)) else getString(R.string.state_off)
        render(s.wireless, !s.blocked, sub)
    }

    override fun handleClick() {
        handleResult(g.controller.setWireless(!g.controller.read().wireless, "tile"))
    }
}

class DevOptionsTile : BaseTile() {
    override val tileKey = "dev"

    override fun update() {
        val s = g.controller.read()
        render(s.devOptions, !s.blocked, accessSubtitle(s) ?: if (s.devOptions) getString(R.string.state_on) else getString(R.string.state_off))
    }

    override fun handleClick() {
        handleResult(g.controller.setDevOptions(!g.controller.read().devOptions, "tile"))
    }
}

class KeepAwakeTile : BaseTile() {
    override val tileKey = "awake"

    override fun update() {
        val s = g.controller.read()
        val sub = if (!s.hasPermission) getString(R.string.tile_tap_setup) else if (s.keepAwake) getString(R.string.awake_while_charging) else getString(R.string.state_off)
        render(s.keepAwake, true, sub)
    }

    override fun handleClick() {
        handleResult(g.controller.setKeepAwake(!g.controller.read().keepAwake, "tile"))
    }
}

class DevPanelTile : BaseTile() {
    override val tileKey = "panel"

    override fun update() = render(false, true, getString(R.string.action_open))

    override fun handleClick() {
        // Overlays draw below the shade, so collapse it through a tiny trampoline activity first.
        openActivity(Intent(this, TilePrefsActivity::class.java).setAction(TilePrefsActivity.ACTION_SHOW_PANEL))
    }
}
