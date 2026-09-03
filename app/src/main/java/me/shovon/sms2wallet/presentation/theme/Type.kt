package me.shovon.sms2wallet.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

private val Default = Typography()

/**
 * The app's type scale.
 *
 * Built on the Material 3 scale (no custom font is bundled, and adding one for an app this size
 * would buy little), but with the emphasis weights baked in rather than left to call sites.
 *
 * Screens previously reached for `style = bodyLarge, fontWeight = Medium` or
 * `headlineSmall.copy(fontWeight = SemiBold)` in sixteen places, which is how a type scale stops
 * being a scale: the same visual role ends up spelled differently on each screen and drifts. Every
 * role below is now picked by *meaning*, and no composable overrides a weight.
 *
 * Roles as used here:
 * - [Typography.headlineMedium]/[Typography.headlineSmall] - the one thing a screen is about
 *   (a screen's lead line, a stat's value).
 * - [Typography.titleLarge] - bottom-sheet titles.
 * - [Typography.titleSmall] - section headers, which sit above content and need to out-rank it.
 * - [Typography.titleMedium] - the primary line of a list row.
 * - [Typography.bodyLarge] - editable/selectable values inside a field.
 * - [Typography.bodyMedium]/[Typography.bodySmall] - supporting and secondary text.
 * - [Typography.labelMedium]/[Typography.labelSmall] - captions above a value, and badges.
 */
val AppTypography = Typography(
    // Emphasis lives in the scale, so a headline is heavy everywhere it appears.
    headlineLarge = Default.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = Default.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Default.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Default.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    // A section header must read as a level above the rows it introduces; M3's default Medium
    // is not enough separation at 14sp against a titleMedium row title.
    titleSmall = Default.titleSmall.copy(fontWeight = FontWeight.SemiBold),
)
