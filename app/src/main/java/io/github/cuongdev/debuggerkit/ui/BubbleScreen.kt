package io.github.cuongdev.debuggerkit.ui

import android.Manifest
import androidx.compose.ui.res.stringResource
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.cuongdev.debuggerkit.R
import io.github.cuongdev.debuggerkit.bubble.BubbleView
import io.github.cuongdev.debuggerkit.core.AppPrefs
import io.github.cuongdev.debuggerkit.core.DebugState
import io.github.cuongdev.debuggerkit.graph
import kotlin.math.roundToInt

@Composable
fun BubbleScreen(state: DebugState, prefs: AppPrefs, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val g = ctx.graph
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val overlay = remember(tick) { g.sys.canDrawOverlays() }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val now = rememberNow()

    ScreenColumn {
        ScreenTitle(stringResource(R.string.floating_button), stringResource(R.string.bubble_sub))

        DkCard {
            SwitchRow(
                stringResource(R.string.bubble_show),
                prefs.bubbleEnabled,
                { on ->
                    g.prefs.setBubbleEnabled(on)
                    if (on && !overlay) {
                        ctx.toast(ctx.getString(R.string.bubble_allow_toast))
                        openOverlaySettings(ctx)
                    } else if (on && Build.VERSION.SDK_INT >= 33 &&
                        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    MainActivity.syncBubble(ctx)
                },
                sub = if (overlay) stringResource(R.string.bubble_overlay_allowed) else stringResource(R.string.bubble_overlay_not_allowed),
            )
        }

        // Live preview of bubble style C with the current size, state and timer.
        val progress = when {
            !state.usb -> -1f
            prefs.autoOffAt > 0 && prefs.autoOffTotal > 0 ->
                ((prefs.autoOffAt - now).toFloat() / prefs.autoOffTotal).coerceIn(0f, 1f)
            else -> 1f
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFE8EBEE)),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(120.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFC3C9CF)))
                repeat(2) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFD3D8DD)))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.width(150.dp).height(10.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFCDD2D7)))
                            Box(Modifier.width(90.dp).height(10.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFD8DCE0)))
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.preview_caps),
                Modifier.align(Alignment.BottomStart).padding(16.dp, 10.dp),
                fontFamily = Mono,
                fontSize = 10.sp,
                color = Color(0xFF4A545C),
            )
            if (prefs.bubbleEnabled) {
                val density = ctx.resources.displayMetrics.density
                AndroidView(
                    factory = { BubbleView(it) },
                    update = { v ->
                        v.diameter = (BUBBLE_SIZES[prefs.bubbleSize.coerceIn(0, 2)] * density).roundToInt()
                        v.on = state.usb
                        v.wireless = state.wireless
                        v.needsPermission = !state.ready
                        v.progress = progress
                        v.alpha = if (prefs.dockWhenIdle) 1f else prefs.idleOpacity / 100f
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }

        SectionLabel(stringResource(R.string.gestures))
        DkCard {
            ValueRow(stringResource(R.string.gesture_tap), stringResource(R.string.cd_toggle_usb))
            RowDivider()
            ValueRow(stringResource(R.string.gesture_long_press), stringResource(R.string.gesture_long_value))
            RowDivider()
            ValueRow(stringResource(R.string.gesture_double_tap), stringResource(R.string.gesture_double_value))
            RowDivider()
            ValueRow(stringResource(R.string.gesture_drag), stringResource(R.string.gesture_drag_value))
        }

        val shape = RoundedCornerShape(18.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(shape)
                .background(Dk.BlueBg)
                .border(1.dp, Dk.BlueBorder, shape)
                .clickable(role = Role.Button) { go("panel") }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(R.drawable.ic_tile_panel, Dk.Blue, Color(0xFF0E1520))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.customize_panel), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Dk.Text)
                Text(stringResource(R.string.customize_panel_sub, prefs.panelActions.size), fontSize = 12.sp, color = Dk.BlueText)
            }
            Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(18.dp), tint = Dk.BlueText)
        }

        SectionLabel(stringResource(R.string.behavior))
        DkCard {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.size), Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Dk.Text)
                Segmented(listOf("S", "M", "L"), prefs.bubbleSize, { g.prefs.setBubbleSize(it) }, Modifier.width(132.dp), height = 36.dp)
            }
            RowDivider()
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.idle_opacity), Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Dk.Text)
                    Text("${prefs.idleOpacity}%", fontFamily = Mono, fontSize = 12.sp, color = Dk.Soft)
                }
                Text(stringResource(R.string.idle_opacity_sub), fontSize = 12.sp, color = Dk.Muted)
                Slider(
                    value = prefs.idleOpacity.toFloat(),
                    onValueChange = { g.prefs.setIdleOpacity(((it / 5f).roundToInt() * 5)) },
                    valueRange = 20f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = Dk.Mint,
                        activeTrackColor = Dk.Mint,
                        inactiveTrackColor = Dk.Surface2,
                    ),
                )
            }
            RowDivider()
            SwitchRow(
                stringResource(R.string.dock_idle),
                prefs.dockWhenIdle,
                { g.prefs.setDockWhenIdle(it) },
                sub = stringResource(R.string.dock_idle_sub),
            )
            RowDivider()
            SwitchRow(
                stringResource(R.string.keep_on_visible),
                prefs.bubbleKeepScreenOn,
                { g.prefs.setBubbleKeepScreenOn(it) },
                sub = stringResource(R.string.keep_on_visible_sub),
            )
        }
    }
}

private val BUBBLE_SIZES = floatArrayOf(44f, 52f, 60f)
