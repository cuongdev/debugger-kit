package io.github.cuongdev.debuggerkit.ui

import android.os.Build
import androidx.compose.ui.res.stringResource
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.cuongdev.debuggerkit.R
import io.github.cuongdev.debuggerkit.core.AppPrefs
import io.github.cuongdev.debuggerkit.core.DebugController
import io.github.cuongdev.debuggerkit.core.DebugState
import io.github.cuongdev.debuggerkit.graph
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(state: DebugState, prefs: AppPrefs, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val now = rememberNow()
    val handle: (DebugController.Result) -> Unit = { r ->
        resultMessage(ctx, r)?.let { ctx.toast(it) }
        if (r == DebugController.Result.NO_PERMISSION) go("setup")
    }

    ScreenColumn(spacing = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(R.drawable.ic_notif, Dk.Mint, Dk.OnMint, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.app_name), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Dk.Text)
                Text(
                    stringResource(R.string.home_device, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
                    fontFamily = Mono,
                    fontSize = 11.sp,
                    color = Dk.Muted,
                )
            }
            IconAction(R.drawable.ic_refresh, stringResource(R.string.home_refresh), {
                g.controller.refresh()
                ctx.toast(ctx.getString(R.string.home_refreshed))
            })
        }

        when {
            state.blocked -> Banner(
                stringResource(R.string.managed_title),
                stringResource(R.string.managed_body),
                fg = Dk.Coral, bodyColor = Dk.CoralBody, bg = Dk.CoralBg, border = Dk.CoralBorder,
                icon = R.drawable.ic_lock,
            )
            !state.hasPermission -> Banner(
                stringResource(R.string.perm_needed_title),
                stringResource(R.string.perm_needed_body),
                fg = Dk.AmberText, bodyColor = Dk.AmberBody, bg = Dk.AmberBg, border = Dk.AmberBorder,
            ) {
                PrimaryButton(stringResource(R.string.action_set_up), { go("setup") }, color = Dk.Amber, textColor = Dk.OnAmber, height = 44.dp)
            }
            else -> Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Dk.MintBg)
                    .border(1.dp, Color24432F, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(painterResource(R.drawable.ic_check), null, Modifier.size(16.dp), tint = Dk.Mint)
                Text(stringResource(R.string.perm_granted_line), fontFamily = Mono, fontSize = 12.sp, color = Dk.MintLight)
            }
        }

        UsbHero(
            state, prefs, now,
            onToggle = { handle(g.controller.setUsb(!state.usb, "app")) },
            onAutoOffToggle = {
                val on = !prefs.autoOffEnabled
                g.controller.configureAutoOff(on, source = "app")
                ctx.toast(if (on) ctx.getString(R.string.toast_auto_off_on, prefs.autoOffMinutes) else ctx.getString(R.string.toast_auto_off_off))
            },
        )

        WideToggle(
            icon = R.drawable.ic_tile_wifi,
            title = stringResource(R.string.wireless_debugging),
            sub = if (state.wireless) (g.sys.wifiIpv4() ?: stringResource(R.string.state_on)) else stringResource(R.string.wireless_off_sub),
            monoSub = state.wireless,
            on = state.wireless,
            enabled = state.ready,
        ) { handle(g.controller.setWireless(!state.wireless, "app")) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallToggle(
                R.drawable.ic_tile_code, stringResource(R.string.developer_options),
                if (state.devOptions) stringResource(R.string.dev_menu_visible) else stringResource(R.string.dev_menu_hidden),
                state.devOptions, state.ready, Modifier.weight(1f),
            ) { handle(g.controller.setDevOptions(!state.devOptions, "app")) }
            SmallToggle(
                R.drawable.ic_tile_coffee, stringResource(R.string.keep_awake),
                if (state.keepAwake) stringResource(R.string.awake_while_charging) else stringResource(R.string.state_off),
                state.keepAwake, state.hasPermission, Modifier.weight(1f),
            ) { handle(g.controller.setKeepAwake(!state.keepAwake, "app")) }
        }

        SectionLabel(stringResource(R.string.shortcuts))
        ShortcutRow(
            R.drawable.ic_bubble, stringResource(R.string.floating_button),
            if (prefs.bubbleEnabled) stringResource(if (prefs.bubbleRight) R.string.bubble_showing_right else R.string.bubble_showing_left) else stringResource(R.string.state_off),
        ) { go("bubble") }
        ShortcutRow(R.drawable.ic_grid, stringResource(R.string.quick_settings_tiles), stringResource(R.string.tiles_added_count, prefs.tilesAdded.size, 5)) { go("tiles") }
        ShortcutRow(
            R.drawable.ic_shield, stringResource(R.string.debug_off_per_app),
            when {
                prefs.appRules.isEmpty() -> stringResource(R.string.rules_empty_sub)
                prefs.appRulesEnabled -> stringResource(R.string.rules_auto_sub, prefs.appRules.size)
                else -> stringResource(R.string.rules_manual_sub, prefs.appRules.size)
            },
        ) { go("rules") }
    }
}

private val Color24432F = androidx.compose.ui.graphics.Color(0xFF24432F)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UsbHero(
    state: DebugState,
    prefs: AppPrefs,
    now: Long,
    onToggle: () -> Unit,
    onAutoOffToggle: () -> Unit,
) {
    val on = state.usb && !state.blocked
    val enabled = state.ready
    val toggleLabel = stringResource(R.string.cd_toggle_usb)
    val stateLabel = stringResource(if (on) R.string.state_on else R.string.state_off)
    val sub = when {
        state.blocked -> stringResource(R.string.usb_sub_blocked)
        !state.hasPermission -> stringResource(R.string.usb_sub_readonly)
        on && prefs.autoOffAt > now ->
            stringResource(R.string.usb_sub_on_until, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(prefs.autoOffAt)))
        on -> stringResource(R.string.usb_sub_on)
        else -> stringResource(R.string.usb_sub_off)
    }
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.55f)
            .clip(shape)
            .background(Dk.Surface)
            .border(1.dp, Dk.Line, shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ic_tile_usb), null, Modifier.size(16.dp), tint = Dk.Muted)
                    Text(stringResource(R.string.usb_debugging_caps), fontFamily = Mono, fontSize = 11.sp, letterSpacing = 0.08.em, color = Dk.Muted)
                }
                Text(
                    stringResource(if (on) R.string.state_on_caps else R.string.state_off_caps),
                    fontSize = 52.sp,
                    lineHeight = 52.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.03).em,
                    color = if (on) Dk.Mint else Dk.Text,
                )
                Text(sub, fontSize = 13.sp, lineHeight = 18.sp, color = Dk.Muted)
            }
            Box(
                Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(if (on) Dk.Mint else Dk.Surface)
                    .border(2.dp, if (on) Dk.Mint else Dk.OffBorder, CircleShape)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onToggle)
                    .semantics {
                        contentDescription = toggleLabel
                        stateDescription = stateLabel
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_power), null, Modifier.size(36.dp), tint = if (on) Dk.OnMint else Dk.Text)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip("adb_enabled = ${if (state.usb) 1 else 0}")
            Chip(
                if (prefs.autoOffEnabled) stringResource(R.string.chip_auto_off_min, prefs.autoOffMinutes) else stringResource(R.string.chip_auto_off_off),
                R.drawable.ic_timer,
                onClick = onAutoOffToggle,
            )
        }
    }
}

@Composable
private fun WideToggle(
    @DrawableRes icon: Int,
    title: String,
    sub: String,
    monoSub: Boolean,
    on: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.55f)
            .clip(shape)
            .background(Dk.Surface)
            .border(1.dp, Dk.Line, shape)
            .toggleable(value = on, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() })
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon, if (on) Dk.MintDim else Dk.Surface2, if (on) Dk.Mint else Dk.Muted)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Dk.Text)
            // Mono only for machine values such as the IP address.
            Text(sub, fontFamily = if (monoSub) Mono else Grotesk, fontSize = 12.sp, lineHeight = 17.sp, color = Dk.Muted)
        }
        DkSwitch(on, enabled)
    }
}

@Composable
private fun SmallToggle(
    @DrawableRes icon: Int,
    title: String,
    sub: String,
    on: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .alpha(if (enabled) 1f else 0.55f)
            .clip(shape)
            .background(Dk.Surface)
            .border(1.dp, Dk.Line, shape)
            .toggleable(value = on, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() })
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, if (on) Dk.MintDim else Dk.Surface2, if (on) Dk.Mint else Dk.Muted, size = 36.dp)
            Box(Modifier.weight(1f))
            DkSwitch(on, enabled)
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Dk.Text)
            Text(sub, fontSize = 12.sp, color = Dk.Muted)
        }
    }
}

@Composable
private fun ShortcutRow(@DrawableRes icon: Int, title: String, sub: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(Dk.Surface)
            .border(1.dp, Dk.Line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon, Dk.BlueBg, Dk.Blue, size = 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Dk.Text)
            Text(sub, fontSize = 12.sp, color = Dk.Muted)
        }
        Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(18.dp), tint = Dk.Muted)
    }
}
