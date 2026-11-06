package io.github.cuongdev.debuggerkit.ui

import android.content.ClipData
import androidx.compose.ui.res.stringResource
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.cuongdev.debuggerkit.R
import io.github.cuongdev.debuggerkit.core.DebugController
import kotlinx.coroutines.delay

// Layout

@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    spacing: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

@Composable
fun ScreenTitle(title: String, sub: String? = null, onBack: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconAction(R.drawable.ic_back, stringResource(R.string.cd_back), onBack, Modifier.offset(x = (-12).dp))
            }
            Text(
                title,
                modifier = if (onBack != null) Modifier.offset(x = (-8).dp) else Modifier,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.02).em,
                color = Dk.Text,
            )
        }
        if (sub != null) Text(sub, fontSize = 13.sp, lineHeight = 19.sp, color = Dk.Muted)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier,
        fontFamily = Mono,
        fontSize = 11.sp,
        letterSpacing = 0.08.em,
        color = Dk.Muted,
    )
}

@Composable
fun DkCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Dk.Surface)
            .border(1.dp, Dk.Line, shape),
        content = content,
    )
}

@Composable
fun RowDivider() {
    HorizontalDivider(thickness = 1.dp, color = Dk.Divider)
}

// Controls

@Composable
fun DkSwitch(checked: Boolean, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = null,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Dk.OnMint,
            checkedTrackColor = Dk.Mint,
            checkedBorderColor = Dk.Mint,
            uncheckedThumbColor = Dk.Muted,
            uncheckedTrackColor = Dk.Surface2,
            uncheckedBorderColor = Dk.OffBorder,
        ),
    )
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    sub: String? = null,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Dk.Text)
            if (sub != null) Text(sub, fontSize = 12.sp, lineHeight = 17.sp, color = Dk.Muted)
        }
        DkSwitch(checked, enabled)
    }
}

@Composable
fun ValueRow(title: String, value: String, onClick: (() -> Unit)? = null, valueColor: Color = Dk.Muted) {
    val click = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        Modifier
            .fillMaxWidth()
            .then(click)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Dk.Text)
        // Capped so a long translated value wraps instead of squeezing the title.
        Text(value, Modifier.widthIn(max = 180.dp), fontSize = 13.sp, color = valueColor, textAlign = TextAlign.End)
        if (onClick != null) {
            Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(16.dp), tint = Dk.Muted)
        }
    }
}

@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
) {
    // Long labels (other languages, large font sizes) wrap and every segment grows to the same height.
    Row(
        modifier
            .height(IntrinsicSize.Max)
            .clip(RoundedCornerShape(12.dp))
            .background(Dk.Bg)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = height)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) Dk.Text else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) })
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = if (on) Dk.Bg else Dk.Soft,
                )
            }
        }
    }
}

@Composable
fun IconAction(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Dk.Text,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, Modifier.size(20.dp), tint = tint)
    }
}

@Composable
fun IconTile(@DrawableRes icon: Int, bg: Color, fg: Color, size: Dp = 40.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, Modifier.size(size / 2), tint = fg)
    }
}

@Composable
fun ShellBadge() {
    Text(
        "S",
        Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Dk.BlueBorder)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        fontFamily = Mono,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = Dk.BlueText,
    )
}

@Composable
fun Chip(text: String, @DrawableRes icon: Int? = null, onClick: (() -> Unit)? = null) {
    val click = if (onClick != null) {
        Modifier.clickable(role = Role.Switch, onClick = onClick).heightIn(min = 36.dp)
    } else {
        Modifier
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Dk.Surface2)
            .then(click)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(painterResource(icon), null, Modifier.size(12.dp), tint = Dk.Soft)
        Text(text, fontFamily = Mono, fontSize = 11.sp, color = Dk.Soft, maxLines = 1, softWrap = false)
    }
}

@Composable
fun Banner(
    title: String,
    body: String?,
    fg: Color,
    bodyColor: Color,
    bg: Color,
    border: Color,
    @DrawableRes icon: Int? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) Icon(painterResource(icon), null, Modifier.size(20.dp), tint = fg)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg)
            if (body != null) Text(body, fontSize = 12.sp, lineHeight = 17.sp, color = bodyColor)
        }
        action?.invoke(this)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Dk.Mint,
    textColor: Color = Dk.OnMint,
    height: Dp = 48.dp,
) {
    Box(
        modifier
            .heightIn(min = height)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) color else Dk.Surface2)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = if (enabled) textColor else Dk.Muted)
    }
}

@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Dk.Text,
    border: Color = Dk.Line,
    height: Dp = 44.dp,
    fill: Color = Color.Transparent,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .heightIn(min = height)
            .clip(shape)
            .background(fill)
            .border(1.dp, if (enabled) border else Dk.Divider, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = if (enabled) color else Dk.Muted)
    }
}

// Bottom navigation

@Composable
fun BottomNav(current: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Dk.Bg)
            .drawBehind { drawLine(Color(0xFF20262B), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        NavItem("home", stringResource(R.string.nav_home), R.drawable.ic_home, current, onSelect)
        NavItem("bubble", stringResource(R.string.nav_bubble), R.drawable.ic_bubble, current, onSelect)
        NavItem("tiles", stringResource(R.string.nav_tiles), R.drawable.ic_grid, current, onSelect)
        NavItem("settings", stringResource(R.string.nav_settings), R.drawable.ic_sliders, current, onSelect)
    }
}

@Composable
private fun RowScope.NavItem(
    route: String,
    label: String,
    @DrawableRes icon: Int,
    current: String,
    onSelect: (String) -> Unit,
) {
    val selected = route == current
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(route) })
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(width = 56.dp, height = 30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (selected) Dk.MintDim else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, Modifier.size(20.dp), tint = if (selected) Dk.Mint else Dk.Muted)
        }
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) Dk.Text else Dk.Muted,
        )
    }
}

// Helpers

/** Current time that refreshes every [periodMs], only while [active], so idle screens do not recompose. */
@Composable
fun rememberNow(active: Boolean = true, periodMs: Long = 1_000L): Long {
    val now by produceState(System.currentTimeMillis(), active, periodMs) {
        value = System.currentTimeMillis()
        while (active) {
            delay(periodMs)
            value = System.currentTimeMillis()
        }
    }
    return now
}

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun Context.copyText(label: String, text: String) {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun openOverlaySettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

fun resultMessage(context: Context, r: DebugController.Result): String? = when (r) {
    DebugController.Result.OK -> null
    DebugController.Result.NO_PERMISSION -> context.getString(R.string.msg_grant_in_setup)
    DebugController.Result.BLOCKED -> context.getString(R.string.msg_blocked)
    DebugController.Result.FAILED -> context.getString(R.string.msg_failed)
}

/** Full height spacer that only reserves the navigation bar inset. */
@Composable
fun NavBarSpacer() {
    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars))
}

/** Opens a web page in the user's browser. The app itself has no internet permission. */
fun Context.openUrl(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { toast(getString(R.string.msg_no_browser)) }
}
