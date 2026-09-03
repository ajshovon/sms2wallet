package me.shovon.sms2wallet.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.rememberDynamicColorScheme
import me.shovon.sms2wallet.domain.model.AccentColor
import me.shovon.sms2wallet.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    outlineVariant = md_theme_light_outlineVariant
)

private val DarkColors = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    outlineVariant = md_theme_dark_outlineVariant
)

/**
 * Re-grounds any dark [ColorScheme] on true black.
 *
 * Written as a transform rather than a fixed scheme so AMOLED composes with whatever palette is
 * active - dynamic colour or any accent - instead of being one hardcoded variant of the brand
 * dark theme, which is what it used to be.
 *
 * Only the backdrop tokens go to #000. Containers stay slightly lifted and `outlineVariant` is
 * raised, because once the background is pure black a surface can no longer be distinguished by
 * being marginally lighter; the separation has to come from outlines instead.
 */
private fun ColorScheme.asAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = md_theme_amoled_surfaceVariant,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = md_theme_amoled_container,
    surfaceContainer = md_theme_amoled_container,
    surfaceContainerHigh = md_theme_amoled_containerHigh,
    surfaceContainerHighest = md_theme_amoled_containerHigh,
    outlineVariant = md_theme_amoled_outlineVariant,
)

/** Seed colours the palette is generated from, one per [AccentColor]. */
private fun AccentColor.seed(): Color = when (this) {
    AccentColor.DYNAMIC, AccentColor.BRAND -> md_theme_light_primary
    AccentColor.BLUE -> Color(0xFF2E5AAC)
    AccentColor.VIOLET -> Color(0xFF6A4FA3)
    AccentColor.ROSE -> Color(0xFFB03A5B)
    AccentColor.AMBER -> Color(0xFF9A6300)
    AccentColor.FOREST -> Color(0xFF2E6B34)
}

/** The swatch shown in the picker: the seed itself, which is what the palette is built from. */
fun AccentColor.swatch(): Color = seed()

/**
 * App-wide Material 3 theme. Uses dynamic (Material You) colour on Android 12+ when
 * [useDynamicColor] is true, and the hand-picked [LightColors]/[DarkColors] fallback
 * everywhere else (including all API < 31 devices).
 */
@Composable
fun Sms2WalletTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColor: AccentColor = AccentColor.DYNAMIC,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val amoled = themeMode == ThemeMode.AMOLED
    val context = LocalContext.current
    val wallpaperColour = accentColor == AccentColor.DYNAMIC &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val basePalette = when {
        wallpaperColour && darkTheme -> dynamicDarkColorScheme(context)
        wallpaperColour && !darkTheme -> dynamicLightColorScheme(context)
        // Every named accent goes through the same seed-to-tonal-palette generation, so the
        // brand colour is not a special case with hand-written tokens while the others are
        // derived - they all get the same contrast guarantees.
        else -> rememberDynamicColorScheme(
            seedColor = accentColor.seed(),
            isDark = darkTheme,
            isAmoled = false,
        )
    }

    // Applied last, so true black wins over whatever palette produced the surfaces.
    val colorScheme = if (amoled) basePalette.asAmoled() else basePalette

    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

/** Convenience accessor for [ExtendedColorScheme], mirroring `MaterialTheme.colorScheme`. */
object Sms2WalletTheme {
    val extendedColors: ExtendedColorScheme
        @Composable get() = LocalExtendedColors.current
}
