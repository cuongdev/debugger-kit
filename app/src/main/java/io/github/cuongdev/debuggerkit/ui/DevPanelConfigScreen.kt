package io.github.cuongdev.debuggerkit.ui

import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cuongdev.debuggerkit.R
import io.github.cuongdev.debuggerkit.core.AppPrefs
import io.github.cuongdev.debuggerkit.core.ShellBackend
import io.github.cuongdev.debuggerkit.devtools.DevAction
import io.github.cuongdev.debuggerkit.devtools.Outcome
import io.github.cuongdev.debuggerkit.devtools.Preset
import io.github.cuongdev.debuggerkit.graph
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun DevPanelConfigScreen(prefs: AppPrefs, onBack: () -> Unit, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val scope = rememberCoroutineScope()
    val savedAt by g.snapshot.savedAt.collectAsStateWithLifecycle()
    var applied by remember { mutableStateOf<Preset?>(null) }
    var busy by remember { mutableStateOf(false) }
    var target by rememberSaveable(prefs.targetPackage) { mutableStateOf(prefs.targetPackage) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val shellReady = remember(tick, prefs.shellBackend) { g.shell.available() }
    val targetValid = target.isEmpty() || g.devTools.isValidPackage(target)

    fun launchAction(block: suspend () -> Outcome) {
        if (busy) return
        scope.launch {
            busy = true
            val o = block()
            busy = false
            o.message?.let { ctx.toast(it) }
        }
    }

    ScreenColumn(Modifier.imePadding()) {
        ScreenTitle(
            stringResource(R.string.dev_panel),
            stringResource(R.string.panel_config_sub),
            onBack = onBack,
        )

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF12171B))
                .border(1.dp, Dk.Divider, RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { go("setup") }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ShellBadge()
            Text(
                when {
                    shellReady && prefs.shellBackend == ShellBackend.SHIZUKU -> stringResource(R.string.shell_shizuku_ok)
                    shellReady -> stringResource(R.string.shell_root_ok)
                    else -> stringResource(R.string.shell_missing)
                },
                Modifier.weight(1f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = if (shellReady) Dk.Mint else Dk.Muted,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.shortcuts), Modifier.weight(1f))
            Text(
                stringResource(R.string.in_panel_count, prefs.panelActions.count { DevAction.byId(it) != null }, DevAction.entries.size),
                fontFamily = Mono,
                fontSize = 11.sp,
                color = Dk.Soft,
            )
        }
        DevAction.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { a ->
                    ActionCard(a, a.id in prefs.panelActions, Modifier.weight(1f)) { g.prefs.setPanelAction(a.id, it) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        SectionLabel(stringResource(R.string.target_app))
        OutlinedTextField(
            value = target,
            onValueChange = { target = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.package_name)) },
            placeholder = { Text("com.example.app", fontFamily = Mono) },
            isError = !targetValid,
            supportingText = {
                Text(
                    if (targetValid) stringResource(R.string.target_hint)
                    else stringResource(R.string.target_invalid),
                )
            },
            textStyle = TextStyle(fontFamily = Mono, fontSize = 14.sp, color = Dk.Text),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (targetValid) g.prefs.setTargetPackage(target) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Dk.Mint,
                unfocusedBorderColor = Dk.Line,
                focusedLabelColor = Dk.Mint,
                unfocusedLabelColor = Dk.Muted,
                cursorColor = Dk.Mint,
                focusedTextColor = Dk.Text,
                unfocusedTextColor = Dk.Text,
                focusedPlaceholderColor = Dk.Grip,
                unfocusedPlaceholderColor = Dk.Grip,
                focusedSupportingTextColor = Dk.Muted,
                unfocusedSupportingTextColor = Dk.Muted,
            ),
        )
        if (target != prefs.targetPackage) {
            OutlineButton(
                stringResource(R.string.save_target),
                { g.prefs.setTargetPackage(target) },
                enabled = targetValid,
                color = Dk.Mint,
                border = Dk.Mint,
            )
        }

        SectionLabel(stringResource(R.string.presets))
        DkCard {
            Preset.entries.forEachIndexed { i, p ->
                if (i > 0) RowDivider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(p.title), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Dk.Text)
                        Text(stringResource(p.summary), fontFamily = Mono, fontSize = 11.sp, color = Dk.Muted)
                    }
                    val on = applied == p
                    OutlineButton(
                        stringResource(if (on) R.string.action_applied else R.string.action_apply),
                        {
                            applied = p
                            launchAction { g.devTools.applyPreset(p) }
                        },
                        height = 40.dp,
                        color = if (on) Dk.OnMint else Dk.Text,
                        border = if (on) Dk.Mint else Dk.Grip,
                        fill = if (on) Dk.Mint else Color.Transparent,
                    )
                }
            }
        }

        val shape = RoundedCornerShape(18.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Color(0xFF12171B))
                .border(1.dp, Dk.Grip, shape)
                .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(R.drawable.ic_refresh, Dk.Surface2, Dk.Soft, size = 36.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.restore_original), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Dk.Text)
                Text(
                    if (savedAt > 0) {
                        stringResource(R.string.snapshot_saved, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(savedAt)))
                    } else {
                        stringResource(R.string.snapshot_none)
                    },
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Dk.Muted,
                )
            }
            OutlineButton(
                stringResource(R.string.action_restore),
                {
                    applied = null
                    launchAction { g.devTools.restore() }
                },
                enabled = savedAt > 0 && !busy,
                height = 40.dp,
                border = Dk.Grip,
            )
        }
    }
}

@Composable
private fun ActionCard(action: DevAction, selected: Boolean, modifier: Modifier, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(if (selected) Dk.MintBg else Dk.Surface)
            .border(1.dp, if (selected) Dk.MintBorder else Dk.Line, shape)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = onChange)
            .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(action.title),
                    Modifier.weight(1f, fill = false),
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Dk.Text,
                )
                if (action.needsShell) ShellBadge()
            }
            Text(stringResource(action.sub), fontSize = 11.sp, lineHeight = 15.sp, color = Dk.Muted)
        }
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) Dk.Mint else Color.Transparent)
                .border(2.dp, if (selected) Dk.Mint else Dk.OffBorder, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(12.dp), tint = Dk.OnMint)
        }
    }
}
