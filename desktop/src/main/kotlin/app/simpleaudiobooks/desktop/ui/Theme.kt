package app.simpleaudiobooks.desktop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import app.simpleaudiobooks.data.AppSettings
import app.simpleaudiobooks.data.ColorPalette
import app.simpleaudiobooks.data.ThemeMode
import app.simpleaudiobooks.ui.theme.Palettes
import app.simpleaudiobooks.ui.theme.Palettes.pureBlack

private val BaseType = Typography()

private val AppType = Typography(
    displaySmall = BaseType.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineLarge = BaseType.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = BaseType.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = BaseType.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseType.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseType.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = BaseType.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseType.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Dieselben Farbschemata wie auf Android; „Aus dem Hintergrundbild“ gibt es am Desktop nicht. */
@Composable
fun DesktopTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = isDark(settings.theme)
    val palette = if (settings.palette == ColorPalette.WALLPAPER) ColorPalette.NIGHT else settings.palette
    val base = Palettes.scheme(palette, dark)
    MaterialTheme(colorScheme = if (dark && settings.pureBlack) base.pureBlack() else base, typography = AppType, content = content)
}
