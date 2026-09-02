package me.shovon.sms2wallet.presentation.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing and sizing tokens.
 *
 * Every padding, gap and icon size in the app comes from here rather than a literal `dp` at
 * the call site, so the 4/8dp rhythm stays intact and a screen can't quietly drift to its own
 * spacing. Named by role, not by value, so the scale can be retuned in one place.
 */
object Spacing {
    /** Hairline gaps: between a label and the text it labels. */
    val xxs = 2.dp
    /** Tight gaps inside a single component, e.g. icon-to-text. */
    val xs = 4.dp
    /** Related elements inside one row or badge cluster. */
    val sm = 8.dp
    /** Between stacked fields in a form, and a row's internal vertical padding. */
    val md = 12.dp
    /** The standard screen gutter and card/row inset. */
    val lg = 16.dp
    /** Between distinct groups within a section. */
    val xl = 24.dp
    /** Between top-level sections of a screen. */
    val xxl = 32.dp
}

/**
 * Icon sizes as tokens. Mixing arbitrary values (18/20/22) across screens is one of the
 * clearest tells of unpolished UI, so the app uses exactly these four.
 */
object IconSize {
    /** Inline with body/label text, e.g. a status glyph beside a caption. */
    val sm = 16.dp
    /** Inline with a title, and the default for leading icons in list rows. */
    val md = 20.dp
    /** Standard action/nav icon, matching Material's own default. */
    val lg = 24.dp
    /** Empty-state and gate illustrations. */
    val xl = 48.dp
}

/**
 * The minimum Android touch target. Applied via `sizeIn` to controls whose *visual* size is
 * smaller than their tappable area (compact icon buttons, stepper controls, list affordances).
 */
val MinTouchTarget = 48.dp
