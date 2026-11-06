package io.github.cuongdev.debuggerkit.ui

import android.app.StatusBarManager
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon as PlatformIcon
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cuongdev.debuggerkit.R
import io.github.cuongdev.debuggerkit.core.AppPrefs
import io.github.cuongdev.debuggerkit.core.DebugState
import io.github.cuongdev.debuggerkit.graph
import io.github.cuongdev.debuggerkit.tile.DevOptionsTile
import io.github.cuongdev.debuggerkit.tile.DevPanelTile
import io.github.cuongdev.debuggerkit.tile.KeepAwakeTile
import io.github.cuongdev.debuggerkit.tile.UsbTile
import io.github.cuongdev.debuggerkit.tile.WirelessTile

private data class TileInfo(
    val key: String,
    val cls: Class<*>,
    @StringRes val label: Int,
    @StringRes val desc: Int,
    @DrawableRes val icon: Int,
)

private val TILES = listOf(
    TileInfo("usb", UsbTile::class.java, R.string.tile_usb, R.string.tile_usb_desc, R.drawable.ic_tile_usb),
    TileInfo("wifi", WirelessTile::class.java, R.string.tile_wireless, R.string.tile_wifi_desc, R.drawable.ic_tile_wifi),
    TileInfo("dev", DevOptionsTile::class.java, R.string.tile_dev, R.string.tile_dev_desc, R.drawable.ic_tile_code),
    TileInfo("awake", KeepAwakeTile::class.java, R.string.tile_awake, R.string.tile_awake_desc, R.drawable.ic_tile_coffee),
    TileInfo("panel", DevPanelTile::class.java, R.string.tile_panel, R.string.tile_panel_desc, R.drawable.ic_tile_panel),
)

@Composable
fun TilesScreen(state: DebugState, prefs: AppPrefs) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val added = TILES.filter { it.key in prefs.tilesAdded }

    ScreenColumn {
        ScreenTitle(stringResource(R.string.quick_settings_tiles), stringResource(R.string.tiles_sub))

        SectionLabel(stringResource(R.string.tiles_preview))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(androidx.compose.ui.graphics.Color(0xFF1A1F23))
                .border(1.dp, Dk.Line, RoundedCornerShape(24.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (added.isEmpty()) {
                Text(stringResource(R.string.tiles_none), Modifier.padding(8.dp), fontSize = 13.sp, color = Dk.Muted)
            }
            added.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { t ->
                        val active = when (t.key) {
                            "usb" -> state.usb
                            "wifi" -> state.wireless
                            "dev" -> state.devOptions
                            "awake" -> state.keepAwake
                            else -> false
                        }
                        val sub = when {
                            t.key == "panel" -> stringResource(R.string.action_open)
                            active -> stringResource(R.string.state_on)
                            else -> stringResource(R.string.state_off)
                        }
                        Row(
                            Modifier
                                .weight(1f)
                                .height(60.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (active) Dk.Mint else androidx.compose.ui.graphics.Color(0xFF2A3036))
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val ink = if (active) Dk.OnMint else Dk.Text
                            Icon(painterResource(t.icon), null, Modifier.size(20.dp), tint = ink)
                            Column {
                                Text(stringResource(t.label), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(sub, fontSize = 11.sp, color = if (active) Dk.OnMint else Dk.Soft)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.tiles_available), Modifier.weight(1f))
            Text(stringResource(R.string.tiles_count, added.size, TILES.size), fontFamily = Mono, fontSize = 11.sp, color = Dk.Soft)
        }
        DkCard {
            TILES.forEachIndexed { i, t ->
                if (i > 0) RowDivider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 66.dp).padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(t.label), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Dk.Text)
                        Text(stringResource(t.desc), fontSize = 12.sp, lineHeight = 17.sp, color = Dk.Muted)
                    }
                    if (t.key in prefs.tilesAdded) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(painterResource(R.drawable.ic_check), null, Modifier.size(14.dp), tint = Dk.Mint)
                            Text(stringResource(R.string.action_added), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Dk.Mint)
                        }
                    } else {
                        OutlineButton(stringResource(R.string.action_add), { requestAddTile(ctx, t) }, height = 40.dp, color = Dk.Mint, border = Dk.Mint)
                    }
                }
            }
        }

        SectionLabel(stringResource(R.string.tiles_behavior))
        DkCard {
            SwitchRow(
                stringResource(R.string.tiles_require_unlock),
                prefs.tileRequireUnlock,
                { g.prefs.setTileRequireUnlock(it) },
                sub = stringResource(R.string.tiles_require_unlock_sub),
            )
            RowDivider()
            ValueRow(stringResource(R.string.tiles_long_press), stringResource(R.string.developer_options))
        }

        Text(
            stringResource(R.string.tiles_note),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = Dk.Muted,
        )
    }
}

private fun requestAddTile(ctx: Context, t: TileInfo) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val sbm = ctx.getSystemService(StatusBarManager::class.java) ?: return
        sbm.requestAddTileService(
            ComponentName(ctx, t.cls),
            ctx.getString(t.label),
            PlatformIcon.createWithResource(ctx, t.icon),
            ctx.mainExecutor,
        ) { result ->
            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            ) {
                ctx.graph.prefs.setTileAdded(t.key, true)
            }
        }
    } else {
        ctx.toast(ctx.getString(R.string.tiles_add_manual, ctx.getString(t.label)))
    }
}
