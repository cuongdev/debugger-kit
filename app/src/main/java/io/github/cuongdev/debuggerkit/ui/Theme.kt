package io.github.cuongdev.debuggerkit.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.cuongdev.debuggerkit.R

/** Design tokens from the Debugger Kit canvas. */
object Dk {
    val Bg = Color(0xFF0E1113)
    val Surface = Color(0xFF161A1D)
    val Surface2 = Color(0xFF1E2327)
    val Panel = Color(0xFF12161A)
    val Tile = Color(0xFF1C2125)
    val Code = Color(0xFF07090A)
    val Line = Color(0xFF2A3137)
    val Divider = Color(0xFF22292E)
    val Text = Color(0xFFE9EDF0)
    val Soft = Color(0xFFC9D1D7)
    val Muted = Color(0xFF9BA6AF)
    val OffBorder = Color(0xFF7C8790)
    val Grip = Color(0xFF4A545C)

    val Mint = Color(0xFF7EE2A8)
    val MintLight = Color(0xFFBFEFD2)
    val OnMint = Color(0xFF0B1F14)
    val MintDim = Color(0xFF1F3328)
    val MintBg = Color(0xFF132219)
    val MintBorder = Color(0xFF3E7A57)

    val Amber = Color(0xFFF2B55A)
    val AmberText = Color(0xFFF7CD8A)
    val AmberBody = Color(0xFFE2CFAE)
    val AmberBg = Color(0xFF2A2113)
    val AmberBorder = Color(0xFF5A4520)
    val OnAmber = Color(0xFF1E1608)

    val Coral = Color(0xFFFFB4A8)
    val CoralBody = Color(0xFFE8C6C0)
    val CoralBg = Color(0xFF2A1714)
    val CoralBorder = Color(0xFF5C2E27)

    val Blue = Color(0xFF8DB6FF)
    val BlueText = Color(0xFFB7C9EA)
    val BlueBg = Color(0xFF1A2333)
    val BlueBorder = Color(0xFF2C3A52)
}

val Grotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_semibold, FontWeight.Bold),
)

val Mono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private val Scheme = darkColorScheme(
    primary = Dk.Mint,
    onPrimary = Dk.OnMint,
    secondary = Dk.Blue,
    background = Dk.Bg,
    onBackground = Dk.Text,
    surface = Dk.Surface,
    onSurface = Dk.Text,
    surfaceVariant = Dk.Surface2,
    onSurfaceVariant = Dk.Muted,
    outline = Dk.Line,
    error = Dk.Coral,
)

private fun TextStyle.g() = copy(fontFamily = Grotesk)

private val AppTypography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.g(),
        displayMedium = t.displayMedium.g(),
        displaySmall = t.displaySmall.g(),
        headlineLarge = t.headlineLarge.g(),
        headlineMedium = t.headlineMedium.g(),
        headlineSmall = t.headlineSmall.g(),
        titleLarge = t.titleLarge.g(),
        titleMedium = t.titleMedium.g(),
        titleSmall = t.titleSmall.g(),
        bodyLarge = t.bodyLarge.g(),
        bodyMedium = t.bodyMedium.g(),
        bodySmall = t.bodySmall.g(),
        labelLarge = t.labelLarge.g(),
        labelMedium = t.labelMedium.g(),
        labelSmall = t.labelSmall.g(),
    )
}

@Composable
fun DebugKitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
