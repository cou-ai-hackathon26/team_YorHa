package com.medidiary.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Catppuccin Mocha — https://catppuccin.com/palette (official hex values). */
object Mocha {
    val Rosewater = Color(0xFFF5E0DC)
    val Flamingo = Color(0xFFF2CDCD)
    val Pink = Color(0xFFF5C2E7)
    val Mauve = Color(0xFFCBA6F7)
    val Red = Color(0xFFF38BA8)
    val Maroon = Color(0xFFEBA0AC)
    val Peach = Color(0xFFFAB387)
    val Yellow = Color(0xFFF9E2AF)
    val Green = Color(0xFFA6E3A1)
    val Teal = Color(0xFF94E2D5)
    val Sky = Color(0xFF89DCEB)
    val Sapphire = Color(0xFF74C7EC)
    val Blue = Color(0xFF89B4FA)
    val Lavender = Color(0xFFB4BEFE)
    val Text = Color(0xFFCDD6F4)
    val Subtext1 = Color(0xFFBAC2DE)
    val Subtext0 = Color(0xFFA6ADC8)
    val Overlay2 = Color(0xFF9399B2)
    val Overlay1 = Color(0xFF7F849C)
    val Overlay0 = Color(0xFF6C7086)
    val Surface2 = Color(0xFF585B70)
    val Surface1 = Color(0xFF45475A)
    val Surface0 = Color(0xFF313244)
    val Base = Color(0xFF1E1E2E)
    val Mantle = Color(0xFF181825)
    val Crust = Color(0xFF11111B)
}

/** Semantic colours used across screens — change here, not in screens. */
val MedicineAccent = Mocha.Blue
val SymptomAccent = Mocha.Peach
val SuccessAccent = Mocha.Green
val WarningAccent = Mocha.Yellow
val DangerAccent = Mocha.Red

private val MochaScheme = darkColorScheme(
    primary = Mocha.Blue,
    onPrimary = Mocha.Crust,
    primaryContainer = Mocha.Surface0,
    onPrimaryContainer = Mocha.Blue,
    inversePrimary = Mocha.Lavender,
    secondary = Mocha.Mauve,
    onSecondary = Mocha.Crust,
    secondaryContainer = Mocha.Surface0,
    onSecondaryContainer = Mocha.Mauve,
    tertiary = Mocha.Peach,
    onTertiary = Mocha.Crust,
    tertiaryContainer = Mocha.Surface0,
    onTertiaryContainer = Mocha.Peach,
    background = Mocha.Base,
    onBackground = Mocha.Text,
    surface = Mocha.Base,
    onSurface = Mocha.Text,
    surfaceVariant = Mocha.Surface0,
    onSurfaceVariant = Mocha.Subtext0,
    surfaceTint = Mocha.Blue,
    inverseSurface = Mocha.Text,
    inverseOnSurface = Mocha.Base,
    error = Mocha.Red,
    onError = Mocha.Crust,
    errorContainer = Mocha.Surface0,
    onErrorContainer = Mocha.Red,
    outline = Mocha.Overlay0,
    outlineVariant = Mocha.Surface1,
    scrim = Mocha.Crust,
    surfaceBright = Mocha.Surface1,
    surfaceDim = Mocha.Crust,
    surfaceContainerLowest = Mocha.Crust,
    surfaceContainerLow = Mocha.Mantle,
    surfaceContainer = Mocha.Mantle,
    surfaceContainerHigh = Mocha.Surface0,
    surfaceContainerHighest = Mocha.Surface1,
)

/** MediDiary is always Catppuccin Mocha (dark), independent of the system theme. */
@Composable
fun MediDiaryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MochaScheme, content = content)
}
