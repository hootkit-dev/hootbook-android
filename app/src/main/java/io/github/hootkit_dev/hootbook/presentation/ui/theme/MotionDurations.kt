package io.github.hootkit_dev.hootbook.presentation.ui.theme

/**
 * Shared animation durations in milliseconds.
 *
 * The app used to drive screen transitions and shared-element bounds with low-stiffness springs,
 * which take well over half a second to settle and keep both the incoming and outgoing screens
 * composed and drawing for that whole time. Short, fixed-length tweens are cheaper and let the
 * navigation lifecycle reach RESUMED quickly.
 */
object MotionDurations {
    /** Screen transitions, shared-element bounds, list item motion. */
    const val standard = 250

    /** Small, local state changes (chip selection, expand/collapse). */
    const val short = 150
}
