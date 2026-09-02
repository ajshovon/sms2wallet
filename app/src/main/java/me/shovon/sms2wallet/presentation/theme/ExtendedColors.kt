package me.shovon.sms2wallet.presentation.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colour tokens that Material 3's default [androidx.compose.material3.ColorScheme]
 * doesn't provide: income/expense amount colours and the "not mapped" warning state used
 * throughout Settings and the Review queue.
 */
data class ExtendedColorScheme(
    val income: Color,
    val expense: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color
)

val LightExtendedColors = ExtendedColorScheme(
    income = income_light,
    expense = expense_light,
    warning = warning_light,
    warningContainer = warningContainer_light,
    onWarningContainer = onWarningContainer_light
)

val DarkExtendedColors = ExtendedColorScheme(
    income = income_dark,
    expense = expense_dark,
    warning = warning_dark,
    warningContainer = warningContainer_dark,
    onWarningContainer = onWarningContainer_dark
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
