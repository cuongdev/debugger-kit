package io.github.cuongdev.debuggerkit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import io.github.cuongdev.debuggerkit.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cuongdev.debuggerkit.AppInfo

private data class License(
    val name: String,
    val license: String,
    val url: String,
    /** Full text bundled in assets/licenses, when the license asks for it to ship with the app. */
    val asset: String? = null,
)

private val LICENSES = listOf(
    License("Debugger Kit", "MIT License", "${AppInfo.REPO_URL}/blob/main/LICENSE"),
    License("Android Jetpack (AndroidX, Compose, Lifecycle)", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    License("Kotlin and kotlinx.coroutines", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    License("Shizuku API", "MIT License", "https://github.com/RikkaApps/Shizuku-API/blob/master/LICENSE"),
    License("Space Grotesk font", "SIL Open Font License 1.1", "https://openfontlicense.org", "licenses/OFL-SpaceGrotesk.txt"),
    License("JetBrains Mono font", "SIL Open Font License 1.1", "https://openfontlicense.org", "licenses/OFL-JetBrainsMono.txt"),
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf<String?>(null) }

    ScreenColumn {
        ScreenTitle(stringResource(R.string.oss_licenses), stringResource(R.string.licenses_sub), onBack = onBack)
        DkCard {
            LICENSES.forEachIndexed { i, l ->
                if (i > 0) RowDivider()
                LicenseRow(l.name, l.license, onClick = {
                    if (l.asset != null) open = if (open == l.asset) null else l.asset else ctx.openUrl(l.url)
                })
                if (l.asset != null && open == l.asset) {
                    val text = remember(l.asset) {
                        runCatching { ctx.assets.open(l.asset).bufferedReader().use { it.readText() } }.getOrDefault("")
                    }
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text, fontFamily = Mono, fontSize = 10.sp, lineHeight = 14.sp, color = Dk.Muted)
                    }
                }
            }
        }
        Text(
            stringResource(R.string.licenses_footer, AppInfo.AUTHOR, AppInfo.REPO_URL.removePrefix("https://")),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Dk.Muted,
        )
    }
}

/** Name above license, so long project names never get squeezed and broken mid word. */
@Composable
private fun LicenseRow(name: String, license: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = Dk.Text)
            Text(license, fontSize = 12.sp, lineHeight = 16.sp, color = Dk.Muted)
        }
        Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(16.dp), tint = Dk.Muted)
    }
}
