package io.github.cuongdev.debuggerkit.ui

import android.content.Intent
import androidx.compose.ui.res.stringResource
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cuongdev.debuggerkit.AppInfo
import io.github.cuongdev.debuggerkit.BuildConfig
import io.github.cuongdev.debuggerkit.R
import io.github.cuongdev.debuggerkit.core.ActivityLog
import io.github.cuongdev.debuggerkit.core.AppPrefs
import io.github.cuongdev.debuggerkit.core.DebugState
import io.github.cuongdev.debuggerkit.core.KeepMode
import io.github.cuongdev.debuggerkit.core.ShellBackend
import io.github.cuongdev.debuggerkit.graph
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AUTO_OFF_STEPS = listOf(15, 30, 60)

@Composable
fun SettingsScreen(state: DebugState, prefs: AppPrefs, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val log by g.log.entries.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        // Picks up a newly granted exact alarm permission for a countdown already running.
        g.autoOff.resync(g.controller.read().usb)
        onPauseOrDispose { }
    }
    val exactAlarms = remember(tick) { g.autoOff.canExact() }

    ScreenColumn(spacing = 16.dp) {
        ScreenTitle(stringResource(R.string.nav_settings))

        SectionLabel(stringResource(R.string.safety))
        DkCard {
            SwitchRow(
                stringResource(R.string.auto_off_usb),
                prefs.autoOffEnabled,
                { g.controller.configureAutoOff(it, source = "app") },
                sub = if (prefs.autoOffEnabled) {
                    stringResource(R.string.auto_off_on_sub)
                } else {
                    stringResource(R.string.auto_off_off_sub)
                },
            )
            if (prefs.autoOffEnabled) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.after), fontSize = 14.sp, color = Dk.Muted)
                    Segmented(
                        AUTO_OFF_STEPS.map { stringResource(R.string.minutes_short, it) },
                        AUTO_OFF_STEPS.indexOf(prefs.autoOffMinutes).coerceAtLeast(0),
                        { g.controller.configureAutoOff(true, AUTO_OFF_STEPS[it], "app") },
                        Modifier.weight(1f),
                        height = 36.dp,
                    )
                }
            }
            if (prefs.autoOffEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RowDivider()
                SwitchRow(
                    stringResource(R.string.exact_timing),
                    exactAlarms,
                    {
                        runCatching {
                            ctx.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")),
                            )
                        }
                    },
                    sub = if (exactAlarms) {
                        stringResource(R.string.exact_on_sub)
                    } else {
                        stringResource(R.string.exact_off_sub)
                    },
                )
            }
            RowDivider()
            SwitchRow(
                stringResource(R.string.off_on_lock),
                prefs.offOnScreenLock,
                { g.prefs.setOffOnScreenLock(it) },
                sub = stringResource(R.string.needs_bubble_sub),
            )
            RowDivider()
            SwitchRow(
                stringResource(R.string.wifi_guard),
                prefs.wirelessGuard,
                { g.prefs.setWirelessGuard(it) },
                sub = stringResource(R.string.wifi_guard_sub),
            )
        }

        SectionLabel(stringResource(R.string.screen_while_debugging))
        DkCard {
            SwitchRow(
                stringResource(R.string.keep_screen_on),
                prefs.keepScreenOn,
                { g.prefs.setKeepScreenOn(it) },
                sub = stringResource(R.string.keep_screen_on_sub),
            )
            RowDivider()
            Column(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .alpha(if (prefs.keepScreenOn) 1f else 0.45f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.mode), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Dk.Text)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Dk.Bg)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ModeOption(stringResource(R.string.awake_while_charging), stringResource(R.string.recommended), prefs.keepMode == KeepMode.CHARGING, prefs.keepScreenOn, Modifier.weight(1f)) {
                        g.prefs.setKeepMode(KeepMode.CHARGING)
                    }
                    ModeOption(stringResource(R.string.mode_always), stringResource(R.string.mode_needs_bubble), prefs.keepMode == KeepMode.ALWAYS, prefs.keepScreenOn, Modifier.weight(1f)) {
                        g.prefs.setKeepMode(KeepMode.ALWAYS)
                    }
                }
            }
            RowDivider()
            SwitchRow(
                stringResource(R.string.restore_screen),
                prefs.restoreScreen,
                { g.prefs.setRestoreScreen(it) },
                sub = stringResource(R.string.restore_screen_sub),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF12171B))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(painterResource(R.drawable.ic_lock), null, Modifier.size(18.dp), tint = Dk.Muted)
                Text(
                    stringResource(R.string.lock_note),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Dk.Muted,
                )
            }
        }

        SectionLabel(stringResource(R.string.activity_log))
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Dk.Code)
                .border(1.dp, Dk.Line, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (log.isEmpty()) Text(stringResource(R.string.log_empty), fontFamily = Mono, fontSize = 12.sp, color = Dk.Muted)
            log.take(8).forEach { e ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(fmt.format(Date(e.time)), Modifier.width(44.dp), fontFamily = Mono, fontSize = 12.sp, color = Dk.Muted)
                    Text(
                        ActivityLog.describe(ctx, e.message),
                        Modifier.weight(1f),
                        fontFamily = Mono,
                        fontSize = 12.sp,
                        color = if (e.message in ActivityLog.ON_EVENTS) Dk.Mint else Dk.Text,
                        maxLines = 1,
                    )
                    Text(e.source, fontFamily = Mono, fontSize = 12.sp, color = Dk.Muted)
                }
            }
            Box(Modifier.padding(top = 8.dp)) {
                OutlineButton(
                    stringResource(R.string.export_csv),
                    {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/csv")
                            .putExtra(Intent.EXTRA_SUBJECT, ctx.getString(R.string.export_subject))
                            .putExtra(Intent.EXTRA_TEXT, g.log.toCsv(ctx))
                        ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.export_chooser)))
                    },
                    enabled = log.isNotEmpty(),
                    height = 40.dp,
                    fill = Dk.Surface,
                )
            }
        }

        SectionLabel(stringResource(R.string.about))
        DkCard {
            LanguageRow()
            RowDivider()
            ValueRow(
                stringResource(R.string.permissions),
                stringResource(if (state.hasPermission) R.string.granted else R.string.not_granted),
                onClick = { go("setup") },
                valueColor = if (state.hasPermission) Dk.Mint else Dk.Amber,
            )
            RowDivider()
            ValueRow(
                stringResource(R.string.shell_access),
                when (prefs.shellBackend) {
                    ShellBackend.SHIZUKU -> if (g.shell.available()) "Shizuku" else stringResource(R.string.shell_shizuku_off)
                    ShellBackend.ROOT -> "Root"
                    ShellBackend.NONE -> stringResource(R.string.shell_none)
                },
                onClick = { go("setup") },
            )
            RowDivider()
            ValueRow(stringResource(R.string.version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        }

        SectionLabel(stringResource(R.string.project))
        DkCard {
            ValueRow(stringResource(R.string.author), AppInfo.AUTHOR, onClick = { ctx.openUrl(AppInfo.AUTHOR_URL) })
            RowDivider()
            ValueRow(stringResource(R.string.source_code), "GitHub", onClick = { ctx.openUrl(AppInfo.REPO_URL) })
            RowDivider()
            ValueRow(stringResource(R.string.report_issue), "GitHub issues", onClick = { ctx.openUrl(AppInfo.ISSUES_URL) })
            RowDivider()
            ValueRow(stringResource(R.string.privacy_policy), stringResource(R.string.no_data_collected), onClick = { ctx.openUrl(AppInfo.PRIVACY_URL) })
            RowDivider()
            ValueRow(stringResource(R.string.oss_licenses), "", onClick = { go("licenses") })
        }
    }
}

@Composable
private fun ModeOption(
    title: String,
    sub: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Dk.Text else Color.Transparent)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Dk.Bg else Dk.Soft)
        Text(sub, fontSize = 11.sp, color = if (selected) Dk.Bg.copy(alpha = 0.75f) else Dk.Muted)
    }
}
