package io.github.cuongdev.debuggerkit.ui

import androidx.compose.foundation.layout.Arrangement
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
                ValueRow(l.name, l.license, onClick = {
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
