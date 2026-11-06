package io.github.cuongdev.debuggerkit.tile

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.core.content.IntentCompat
import io.github.cuongdev.debuggerkit.bubble.BubbleService
import io.github.cuongdev.debuggerkit.graph
import io.github.cuongdev.debuggerkit.ui.MainActivity

/**
 * Invisible activity.
 * Long press on a tile opens Developer options (or the app for the other tiles),
 * and the Dev panel tile uses it to collapse the shade before showing the panel.
 */
class TilePrefsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == ACTION_SHOW_PANEL) {
            val svc = BubbleService.instance
            if (svc != null) svc.showPanel() else openApp("bubble")
        } else {
            val tile = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
            when (tile?.className) {
                UsbTile::class.java.name, WirelessTile::class.java.name, DevOptionsTile::class.java.name ->
                    if (!graph.devTools.openDeveloperOptions()) openApp("home")
                else -> openApp("tiles")
            }
        }
        finish()
    }

    private fun openApp(route: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_ROUTE, route),
        )
    }

    companion object {
        const val ACTION_SHOW_PANEL = "io.github.cuongdev.debuggerkit.action.SHOW_PANEL_FROM_TILE"
    }
}
