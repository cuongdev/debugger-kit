package io.github.cuongdev.debuggerkit.bubble

import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.cuongdev.debuggerkit.R
import io.github.cuongdev.debuggerkit.devtools.DevAction
import io.github.cuongdev.debuggerkit.devtools.DevTools
import io.github.cuongdev.debuggerkit.devtools.Outcome
import io.github.cuongdev.debuggerkit.devtools.PanelItem
import io.github.cuongdev.debuggerkit.devtools.Preset
import io.github.cuongdev.debuggerkit.graph
import io.github.cuongdev.debuggerkit.ui.Dk
import io.github.cuongdev.debuggerkit.ui.IconAction
import io.github.cuongdev.debuggerkit.ui.Mono
import io.github.cuongdev.debuggerkit.ui.OutlineButton
import io.github.cuongdev.debuggerkit.ui.ShellBadge
import io.github.cuongdev.debuggerkit.ui.resultMessage
import io.github.cuongdev.debuggerkit.ui.toast
import kotlinx.coroutines.launch

/** Where the bubble is on screen, in px, so the panel can open next to it instead of under it. */
data class PanelAnchor(
    val topPx: Int,
    val bottomPx: Int,
    val screenHeightPx: Int,
    val safeTopPx: Int,
    val safeBottomPx: Int,
) {
    val openBelow: Boolean get() = (topPx + bottomPx) / 2 < screenHeightPx / 2
}

/** The panel shown over any app after a long press on the bubble. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevPanelOverlay(anchor: PanelAnchor, onClose: () -> Unit, onOpenApp: (String) -> Unit) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val scope = rememberCoroutineScope()
    val state by g.controller.state.collectAsState()
    val prefs by g.prefs.flow.collectAsState()
    var items by remember { mutableStateOf<List<PanelItem>>(emptyList()) }
    var version by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var lastPreset by remember { mutableStateOf<Preset?>(null) }
    val actions = remember(prefs.panelActions) { DevAction.entries.filter { it.id in prefs.panelActions } }
    val shellReady = remember(prefs.shellBackend, version) { g.devTools.shellReady() }

    LaunchedEffect(actions, version, prefs.shellBackend) {
        items = g.devTools.readPanel(actions)
    }

    fun launchAction(block: suspend () -> Outcome) {
        if (busy) return
        scope.launch {
            busy = true
            val o = block()
            busy = false
            o.message?.let { ctx.toast(it) }
            if (o.closePanel) onClose() else version++
        }
    }

    val density = LocalDensity.current
    val gapPx = with(density) { 8.dp.roundToPx() }
    val (topPad, bottomPad) = with(density) {
        if (anchor.openBelow) {
            (anchor.bottomPx + gapPx).toDp() to (anchor.safeBottomPx + gapPx).toDp()
        } else {
            (anchor.safeTopPx + gapPx).toDp() to (anchor.screenHeightPx - anchor.topPx + gapPx).toDp()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x8C080A0C))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        val shape = RoundedCornerShape(24.dp)
        Box(Modifier.fillMaxSize().padding(top = topPad, bottom = bottomPad, start = 12.dp, end = 12.dp)) {
        Column(
            Modifier
                .align(if (anchor.openBelow) Alignment.TopCenter else Alignment.BottomCenter)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(Dk.Panel)
                .border(1.dp, Dk.Line, shape)
                // Swallow taps so they do not reach the scrim and close the panel.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.dev_panel), Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Dk.Text)
                StatusChip("USB", state.usb) {
                    launchAction { Outcome(resultMessage(ctx, g.controller.setUsb(!state.usb, "panel"))) }
                }
                StatusChip("WIFI", state.wireless) {
                    launchAction { Outcome(resultMessage(ctx, g.controller.setWireless(!state.wireless, "panel"))) }
                }
                IconAction(R.drawable.ic_close, stringResource(R.string.cd_close_panel), onClose, tint = Dk.Soft)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Preset.entries.forEach { p ->
                    val on = lastPreset == p
                    OutlineButton(
                        stringResource(p.title),
                        onClick = {
                            lastPreset = p
                            launchAction { g.devTools.applyPreset(p) }
                        },
                        height = 36.dp,
                        color = if (on) Dk.OnMint else Dk.Text,
                        border = if (on) Dk.Mint else Dk.Grip,
                        fill = if (on) Dk.Mint else Color.Transparent,
                    )
                }
                OutlineButton(
                    stringResource(R.string.action_restore),
                    onClick = {
                        lastPreset = null
                        launchAction { g.devTools.restore() }
                    },
                    height = 36.dp,
                    color = Dk.Soft,
                    border = Dk.OffBorder,
                )
            }

            if (actions.isEmpty()) {
                Text(stringResource(R.string.panel_empty), fontSize = 13.sp, color = Dk.Muted)
            }
            items.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { item ->
                        PanelTile(item, Modifier.weight(1f)) {
                            if (!item.available) ctx.toast(ctx.getString(R.string.msg_needs_shell))
                            else launchAction { g.devTools.perform(item.action) }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            val pkg = prefs.targetPackage
            if (g.devTools.isValidPackage(pkg)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Dk.Tile)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.panel_target_app),
                            Modifier.weight(1f),
                            fontFamily = Mono,
                            fontSize = 10.sp,
                            letterSpacing = 0.08.em,
                            color = Dk.Muted,
                        )
                        Text(pkg, fontFamily = Mono, fontSize = 11.sp, color = Dk.Soft, maxLines = 1)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlineButton(stringResource(R.string.force_stop), { launchAction { g.devTools.forceStop(pkg) } }, Modifier.weight(1f), border = Dk.Grip, enabled = shellReady)
                        OutlineButton(stringResource(R.string.clear_data), { launchAction { g.devTools.clearData(pkg) } }, Modifier.weight(1f), color = Dk.Coral, border = Dk.Grip, enabled = shellReady)
                        OutlineButton(stringResource(R.string.restart), { launchAction { g.devTools.restart(pkg) } }, Modifier.weight(1f), border = Dk.Grip, enabled = shellReady)
                    }
                }
            }

            if (!shellReady) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) { onOpenApp("setup") }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ShellBadge()
                    Text(
                        stringResource(R.string.panel_shell_hint),
                        fontSize = 12.sp,
                        color = Dk.Muted,
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun StatusChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        "$label ${stringResource(if (on) R.string.state_on_caps else R.string.state_off_caps)}",
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) Dk.MintDim else Dk.Surface2)
            .clickable(role = Role.Switch, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        fontFamily = Mono,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (on) Dk.Mint else Dk.Muted,
    )
}

@Composable
private fun PanelTile(item: PanelItem, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .heightIn(min = 66.dp)
            .clip(shape)
            .background(if (item.active) Dk.MintBg else Dk.Tile)
            .border(1.dp, if (item.active) Dk.MintBorder else Dk.Line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .alpha(if (item.available) 1f else 0.5f)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(item.action.title),
                Modifier.weight(1f, fill = false),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Dk.Text,
            )
            if (item.action.needsShell) ShellBadge()
        }
        Text(item.value, fontFamily = Mono, fontSize = 11.sp, color = if (item.active) Dk.Mint else Dk.Muted)
    }
}
