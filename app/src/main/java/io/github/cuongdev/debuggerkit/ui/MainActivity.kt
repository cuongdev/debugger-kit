package io.github.cuongdev.debuggerkit.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cuongdev.debuggerkit.bubble.BubbleService
import io.github.cuongdev.debuggerkit.graph

class MainActivity : ComponentActivity() {

    private val route = mutableStateOf("home")

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        route.value = savedInstanceState?.getString(KEY_ROUTE) ?: intent?.getStringExtra(EXTRA_ROUTE) ?: "home"
        setContent {
            DebugKitTheme { AppRoot(route) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { route.value = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_ROUTE, route.value)
    }

    override fun onResume() {
        super.onResume()
        graph.controller.refresh()
        syncBubble(this)
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        private const val KEY_ROUTE = "route"

        /** Starts or stops the background service so it matches the user's settings. */
        fun syncBubble(context: Context) = BubbleService.sync(context)
    }
}

@Composable
fun AppRoot(route: MutableState<String>) {
    val g = LocalContext.current.graph
    val state by g.controller.state.collectAsStateWithLifecycle()
    val prefs by g.prefs.flow.collectAsStateWithLifecycle()
    val r = route.value
    val go: (String) -> Unit = { route.value = it }

    BackHandler(enabled = r != "home") {
        route.value = when (r) {
            "panel" -> "bubble"
            "rules_pick" -> "rules"
            "licenses" -> "settings"
            else -> "home"
        }
    }

    Column(Modifier.fillMaxSize().background(Dk.Bg)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            when (r) {
                "setup" -> SetupScreen(state, onDone = { go("home") })
                "bubble" -> BubbleScreen(state, prefs, go)
                "panel" -> DevPanelConfigScreen(prefs, onBack = { go("bubble") }, go = go)
                "tiles" -> TilesScreen(state, prefs)
                "settings" -> SettingsScreen(state, prefs, go)
                "rules" -> RulesScreen(state, prefs, go)
                "rules_pick" -> AppPickerScreen(prefs, onBack = { go("rules") })
                "licenses" -> LicensesScreen(onBack = { go("settings") })
                else -> HomeScreen(state, prefs, go)
            }
        }
        if (r == "setup") NavBarSpacer() else BottomNav(current = tabOf(r), onSelect = go)
    }
}

private fun tabOf(route: String) = when (route) {
    "bubble", "panel" -> "bubble"
    "tiles" -> "tiles"
    "settings", "licenses" -> "settings"
    else -> "home"
}
