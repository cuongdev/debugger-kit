package io.github.cuongdev.debuggerkit.ui

import android.os.Build
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.cuongdev.debuggerkit.AppInfo
import io.github.cuongdev.debuggerkit.R
import io.github.cuongdev.debuggerkit.core.DebugState
import io.github.cuongdev.debuggerkit.core.ShellBackend
import io.github.cuongdev.debuggerkit.graph
import io.github.cuongdev.debuggerkit.shell.Shell
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(state: DebugState, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val scope = rememberCoroutineScope()
    var method by rememberSaveable { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf("") }
    val oem = remember { detectOem() }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    LifecycleResumeEffect(Unit) {
        tick++
        g.controller.refresh()
        onPauseOrDispose { }
    }
    val overlay = remember(tick) { g.sys.canDrawOverlays() }
    val shizukuRunning = remember(tick) { g.shell.shizukuRunning }
    val command = "adb shell pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS"
    // ColorOS blocks "pm grant" from adb, but still grants requested permissions at install time.
    // This pulls the installed APKs (works for Play installs with split APKs) and reinstalls them with grants.
    val installCommand = "adb shell pm path ${ctx.packageName} | tr -d '\\r' | sed 's/package://' | " +
        "xargs -n1 adb pull && adb install-multiple -r -g *.apk"
    val copy: (String) -> Unit = {
        ctx.copyText("adb command", it)
        copied = it
    }

    suspend fun grantVia(label: String) {
        val out = g.shell.grantSelf()
        g.controller.refresh()
        message = if (g.sys.hasSecureSettings()) {
            ctx.getString(R.string.setup_granted_with, label)
        } else {
            ctx.getString(R.string.setup_grant_failed, out?.text?.trim().orEmpty())
        }
    }

    ScreenColumn(spacing = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(stringResource(R.string.setup_label))
            Text(
                stringResource(R.string.setup_title),
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold,
                color = Dk.Text,
            )
            Text(
                stringResource(R.string.setup_body),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = Dk.Muted,
            )
        }

        DkCard {
            CheckRow(1, stringResource(R.string.developer_options), if (state.devOptions) stringResource(R.string.setup_dev_enabled) else stringResource(R.string.setup_dev_turn_on), state.devOptions, warn = false)
            RowDivider()
            CheckRow(2, stringResource(R.string.setup_secure_perm), if (state.hasPermission) stringResource(R.string.granted) else stringResource(R.string.not_granted), state.hasPermission, warn = true)
            RowDivider()
            CheckRow(
                3, stringResource(R.string.display_over_apps),
                if (overlay) stringResource(R.string.allowed) else stringResource(R.string.setup_overlay_optional),
                overlay, warn = false,
                onClick = if (overlay) null else ({ openOverlaySettings(ctx) }),
            )
        }

        if (state.blocked) {
            Banner(
                stringResource(R.string.managed_title),
                stringResource(R.string.setup_blocked_body),
                fg = Dk.Coral, bodyColor = Dk.CoralBody, bg = Dk.CoralBg, border = Dk.CoralBorder,
                icon = R.drawable.ic_lock,
            )
        }

        Segmented(
            listOf("ADB", "Shizuku", "Root"),
            method,
            { method = it; message = null },
            Modifier.fillMaxWidth(),
            height = 44.dp,
        )

        when (method) {
            0 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val reinstallNote = stringResource(R.string.setup_reinstall_note)
                if (oem == Oem.OPLUS) {
                    Text(
                        stringResource(R.string.setup_coloros),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = Dk.Soft,
                    )
                    CommandBox(installCommand, copied == installCommand) { copy(installCommand) }
                    Text(reinstallNote, fontSize = 12.sp, lineHeight = 17.sp, color = Dk.Muted)
                } else {
                    Text(stringResource(R.string.setup_plug_in), fontSize = 13.sp, color = Dk.Soft)
                    CommandBox(command, copied == command) { copy(command) }
                    if (oem == Oem.XIAOMI) {
                        Text(
                            stringResource(R.string.setup_xiaomi),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = Dk.AmberText,
                        )
                    }
                    Text(
                        stringResource(R.string.setup_if_fails, reinstallNote),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = Dk.Muted,
                    )
                    CommandBox(installCommand, copied == installCommand) { copy(installCommand) }
                }
                Text(
                    stringResource(R.string.setup_guide_link),
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { ctx.openUrl(AppInfo.GRANT_GUIDE_URL) }
                        .padding(vertical = 8.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Dk.Mint,
                )
            }
            1 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (shizukuRunning) {
                        stringResource(R.string.setup_shizuku_running)
                    } else {
                        stringResource(R.string.setup_shizuku_start)
                    },
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Dk.Soft,
                )
                OutlineButton(
                    if (shizukuRunning) stringResource(R.string.setup_shizuku_connect) else stringResource(R.string.setup_shizuku_open),
                    onClick = {
                        if (!g.shell.shizukuRunning) {
                            val launch = ctx.packageManager.getLaunchIntentForPackage(Shell.SHIZUKU_PACKAGE)
                            if (launch != null) ctx.startActivity(launch)
                            else message = ctx.getString(R.string.setup_shizuku_install)
                        } else {
                            g.shell.requestShizukuPermission { granted ->
                                if (!granted) {
                                    message = ctx.getString(R.string.setup_shizuku_denied)
                                } else {
                                    g.prefs.setShellBackend(ShellBackend.SHIZUKU)
                                    scope.launch {
                                        busy = true
                                        grantVia("Shizuku")
                                        busy = false
                                    }
                                }
                            }
                        }
                    },
                    enabled = !busy,
                    color = Dk.Blue,
                    border = Dk.Blue,
                )
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.setup_root_body),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Dk.Soft,
                )
                OutlineButton(
                    if (busy) stringResource(R.string.setup_root_waiting) else stringResource(R.string.setup_root_grant),
                    onClick = {
                        scope.launch {
                            busy = true
                            if (g.shell.checkRoot()) {
                                g.prefs.setShellBackend(ShellBackend.ROOT)
                                grantVia("root")
                            } else {
                                message = ctx.getString(R.string.setup_root_denied)
                            }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    color = Dk.Blue,
                    border = Dk.Blue,
                )
            }
        }

        message?.let { Text(it, fontSize = 13.sp, lineHeight = 19.sp, color = Dk.Soft) }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF12171B))
                .border(1.dp, Dk.Divider, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(painterResource(R.drawable.ic_shield), null, Modifier.size(18.dp), tint = Dk.Muted)
            Text(
                stringResource(R.string.setup_privacy),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = Dk.Muted,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                stringResource(R.string.setup_check),
                onClick = {
                    g.controller.refresh()
                    if (g.sys.hasSecureSettings()) onDone()
                    else message = ctx.getString(R.string.setup_not_yet)
                },
                modifier = Modifier.weight(1f),
                height = 52.dp,
            )
            OutlineButton(stringResource(R.string.action_skip), onDone, height = 52.dp)
        }
    }
}

private enum class Oem { OPLUS, XIAOMI, OTHER }

private fun detectOem(): Oem {
    val m = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
    return when {
        listOf("oppo", "oneplus", "realme").any { it in m } -> Oem.OPLUS
        listOf("xiaomi", "redmi", "poco").any { it in m } -> Oem.XIAOMI
        else -> Oem.OTHER
    }
}

@Composable
private fun CommandBox(command: String, copied: Boolean, onCopy: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Dk.Code)
            .border(1.dp, Dk.Line, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(command, Modifier.weight(1f), fontFamily = Mono, fontSize = 12.sp, lineHeight = 19.sp, color = Dk.MintLight)
        OutlineButton(stringResource(if (copied) R.string.action_copied else R.string.action_copy), onCopy, height = 36.dp, fill = Dk.Surface)
    }
}

@Composable
private fun CheckRow(
    index: Int,
    title: String,
    status: String,
    done: Boolean,
    warn: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val click = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        Modifier
            .fillMaxWidth()
            .then(click)
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val ring = if (warn && !done) Dk.Amber else Dk.Grip
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .then(if (done) Modifier.background(Dk.Mint) else Modifier.border(2.dp, ring, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(painterResource(R.drawable.ic_check), null, Modifier.size(16.dp), tint = Dk.OnMint)
            } else {
                Text("$index", fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (warn) Dk.Amber else Dk.Muted)
            }
        }
        Text(title, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Dk.Text)
        Text(
            status,
            Modifier.widthIn(max = 150.dp),
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            color = when {
                done -> Dk.Mint
                warn -> Dk.Amber
                else -> Dk.Muted
            },
        )
    }
}
