package me.shovon.sms2wallet.presentation.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Motion tokens.
 *
 * Motion here exists to explain a change, never to decorate one: a row leaving the review queue
 * should read as *that row was dealt with*, not as the list suddenly being different. Durations
 * are scaled by how far something travels and how much of the screen it affects, so a small
 * in-place change is quick and a full-screen swap is not.
 */
object MotionDuration {
    /** In-place state changes: a badge appearing, a colour or weight settling. */
    const val QUICK_MILLIS = 120

    /** The default for content entering or leaving within a screen. */
    const val STANDARD_MILLIS = 220

    /** Larger rearrangements, e.g. a list item being removed and its neighbours closing the gap. */
    const val EMPHASISED_MILLIS = 320
}

/**
 * Material 3's standard easing: accelerates away and decelerates in, so movement reads as
 * physical rather than linear.
 */
val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
