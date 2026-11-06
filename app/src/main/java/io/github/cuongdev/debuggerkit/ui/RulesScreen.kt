package io.github.cuongdev.debuggerkit.ui

import android.content.Context
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.cuongdev.debuggerkit.R
import io.github.cuongdev.debuggerkit.bubble.BubbleService
import io.github.cuongdev.debuggerkit.core.AppPrefs
import io.github.cuongdev.debuggerkit.core.DebugState
import io.github.cuongdev.debuggerkit.core.RuleMode
import io.github.cuongdev.debuggerkit.graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap?)

@Composable
fun RulesScreen(state: DebugState, prefs: AppPrefs, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val g = ctx.graph
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        BubbleService.sync(ctx)
        onPauseOrDispose { }
    }
    val usage = remember(tick) { g.appRules.hasUsageAccess() }
    val entries by produceState(emptyMap<String, AppEntry>(), prefs.appRules.keys) {
        value = withContext(Dispatchers.IO) { prefs.appRules.keys.mapNotNull { appEntry(ctx, it) }.associateBy { it.pkg } }
    }

    ScreenColumn {
        ScreenTitle(
            stringResource(R.string.debug_off_per_app),
            stringResource(R.string.rules_sub),
            onBack = { go("home") },
        )

        DkCard {
            SwitchRow(
                stringResource(R.string.rules_auto),
                prefs.appRulesEnabled,
                { on ->
                    g.prefs.setAppRulesEnabled(on)
                    BubbleService.sync(ctx)
                    if (on && !usage) openUsageAccess(ctx)
                },
                sub = if (usage) stringResource(R.string.rules_auto_sub_ok)
                else stringResource(R.string.rules_auto_sub_need),
            )
            RowDivider()
            ValueRow(
                stringResource(R.string.usage_access),
                stringResource(if (usage) R.string.allowed else R.string.allow),
                onClick = if (usage) null else ({ openUsageAccess(ctx) }),
                valueColor = if (usage) Dk.Mint else Dk.Amber,
            )
        }

        if (!state.ready) {
            Banner(
                stringResource(if (state.blocked) R.string.managed_title else R.string.perm_needed_title),
                if (state.blocked) stringResource(R.string.rules_blocked_body)
                else stringResource(R.string.rules_perm_body),
                fg = Dk.AmberText, bodyColor = Dk.AmberBody, bg = Dk.AmberBg, border = Dk.AmberBorder,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.apps), Modifier.weight(1f))
            OutlineButton(stringResource(R.string.add_app), { go("rules_pick") }, height = 36.dp, color = Dk.Mint, border = Dk.Mint)
        }
        if (prefs.appRules.isEmpty()) {
            Text(
                stringResource(R.string.rules_none),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Dk.Muted,
            )
        }
        prefs.appRules.entries.sortedBy { entries[it.key]?.label?.lowercase() ?: it.key }.forEach { (pkg, mode) ->
            RuleCard(
                entry = entries[pkg] ?: AppEntry(pkg, pkg, null),
                mode = mode,
                held = prefs.holdPkg == pkg,
                onMode = { g.prefs.setAppRule(pkg, it) },
                onOpen = {
                    g.appRules.launchSafely(ctx, pkg)?.let { ctx.toast(it) }
                    BubbleService.sync(ctx)
                },
                onPin = {
                    if (!g.appRules.pinShortcut(ctx, pkg)) {
                        ctx.toast(ctx.getString(R.string.rules_no_shortcuts))
                    }
                },
                onRemove = {
                    g.prefs.setAppRule(pkg, null)
                    if (prefs.holdPkg == pkg) g.appRules.restore()
                    BubbleService.sync(ctx)
                },
            )
        }

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
                stringResource(R.string.rules_note),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = Dk.Muted,
            )
        }
    }
}

@Composable
private fun RuleCard(
    entry: AppEntry,
    mode: RuleMode,
    held: Boolean,
    onMode: (RuleMode) -> Unit,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onRemove: () -> Unit,
) {
    DkCard {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(entry.icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Dk.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (held) stringResource(R.string.rules_paused_now) else entry.pkg,
                    fontFamily = if (held) Grotesk else Mono,
                    fontSize = 11.sp,
                    color = if (held) Dk.Mint else Dk.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconAction(R.drawable.ic_close, stringResource(R.string.cd_remove, entry.label), onRemove, tint = Dk.Muted)
        }
        Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.rules_turn_off), fontSize = 12.sp, color = Dk.Muted)
            Segmented(
                listOf(stringResource(R.string.usb_debugging), stringResource(R.string.developer_options)),
                if (mode == RuleMode.USB) 0 else 1,
                { onMode(if (it == 0) RuleMode.USB else RuleMode.DEV) },
                Modifier.fillMaxWidth(),
                height = 36.dp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton(stringResource(R.string.open_without_debugging), onOpen, Modifier.weight(1f), height = 40.dp, color = Dk.Mint, border = Dk.MintBorder)
                OutlineButton(stringResource(R.string.add_to_home), onPin, height = 40.dp, border = Dk.Grip)
            }
        }
    }
}

@Composable
fun AppPickerScreen(prefs: AppPrefs, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val g = ctx.graph
    var query by rememberSaveable { mutableStateOf("") }
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) { loadLaunchableApps(ctx) }
    }
    val shown = remember(apps, query) {
        val q = query.trim().lowercase()
        apps.orEmpty().filter { q.isEmpty() || it.label.lowercase().contains(q) || it.pkg.contains(q) }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScreenTitle(stringResource(R.string.add_apps), stringResource(R.string.add_apps_sub), onBack = onBack)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_apps)) },
                textStyle = TextStyle(fontSize = 15.sp, color = Dk.Text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Dk.Mint,
                    unfocusedBorderColor = Dk.Line,
                    cursorColor = Dk.Mint,
                    focusedTextColor = Dk.Text,
                    unfocusedTextColor = Dk.Text,
                    focusedPlaceholderColor = Dk.Grip,
                    unfocusedPlaceholderColor = Dk.Grip,
                ),
            )
        }
        if (apps == null) {
            Text(stringResource(R.string.loading_apps), Modifier.padding(20.dp), fontSize = 13.sp, color = Dk.Muted)
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(shown, key = { it.pkg }) { app ->
                val selected = app.pkg in prefs.appRules
                val shape = RoundedCornerShape(14.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .clip(shape)
                        .background(if (selected) Dk.MintBg else Dk.Surface)
                        .border(1.dp, if (selected) Dk.MintBorder else Dk.Line, shape)
                        .clickable(role = Role.Checkbox) {
                            if (selected) {
                                g.prefs.setAppRule(app.pkg, null)
                            } else {
                                if (prefs.appRules.isEmpty()) g.prefs.setAppRulesEnabled(true)
                                g.prefs.setAppRule(app.pkg, RuleMode.DEV)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIcon(app.icon)
                    Column(Modifier.weight(1f)) {
                        Text(app.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Dk.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(app.pkg, fontFamily = Mono, fontSize = 11.sp, color = Dk.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) Dk.Mint else Color.Transparent)
                            .border(2.dp, if (selected) Dk.Mint else Dk.OffBorder, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(14.dp), tint = Dk.OnMint)
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun AppIcon(icon: ImageBitmap?) {
    if (icon != null) {
        Image(icon, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
    } else {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Dk.Surface2))
    }
}

private fun openUsageAccess(ctx: Context) {
    runCatching { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
}

private fun appEntry(ctx: Context, pkg: String): AppEntry? = runCatching {
    val pm = ctx.packageManager
    val info = pm.getApplicationInfo(pkg, 0)
    val icon = runCatching { pm.getApplicationIcon(info).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    AppEntry(pkg, pm.getApplicationLabel(info).toString(), icon)
}.getOrNull()

private fun loadLaunchableApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(intent, 0)
    }
    return infos.map { it.activityInfo.packageName }
        .distinct()
        .filter { it != ctx.packageName }
        .mapNotNull { appEntry(ctx, it) }
        .sortedBy { it.label.lowercase() }
}
