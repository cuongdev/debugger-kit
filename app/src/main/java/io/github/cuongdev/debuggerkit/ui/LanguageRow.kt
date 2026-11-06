package io.github.cuongdev.debuggerkit.ui

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cuongdev.debuggerkit.R

/** Language names stay in their own language so people can always find theirs. */
private val LANGUAGES = listOf("" to null, "en" to "English", "vi" to "Tiếng Việt")

/**
 * App language picker. Android 13 and later support per app languages natively
 * (also in system Settings, Apps, Language). Older versions simply follow the system language.
 */
@Composable
fun LanguageRow() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        ValueRow(stringResource(R.string.language), stringResource(R.string.language_follows_system))
        return
    }
    val ctx = LocalContext.current
    val lm = remember { ctx.getSystemService(LocaleManager::class.java) }
    var selected by remember {
        val tag = lm?.applicationLocales?.toLanguageTags().orEmpty()
        mutableIntStateOf(LANGUAGES.indexOfFirst { (code, _) -> code.isNotEmpty() && tag.startsWith(code) }.coerceAtLeast(0))
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.language), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Dk.Text)
        Segmented(
            LANGUAGES.map { (_, name) -> name ?: stringResource(R.string.language_system) },
            selected,
            { i ->
                selected = i
                val code = LANGUAGES[i].first
                // The system recreates the activity in the new language.
                lm?.applicationLocales = if (code.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(code)
            },
            Modifier.fillMaxWidth(),
            height = 36.dp,
        )
    }
}
