package io.github.hootkit_dev.hootbook.core

/**
 * Application-wide constants to avoid hardcoded values
 */
object Constants {

    /**
     * UI Configuration - Moved to ui/theme/Dimensions.kt for better organization
     * Keeping only non-dimension constants here
     */
    object UI {
        const val BUTTON_WIDTH_RATIO = 0.8f
        const val PROGRESS_STROKE_WIDTH = 2f
    }

    /**
     * External Links
     */
    object Links {
        const val GITHUB_URL = "https://github.com/hootkit-dev/hootbook-android"
        const val DEBUG_RELEASE_URL = "https://github.com/hootkit-dev/hootbook-android/releases/tag/debug-latest"
        const val TESTING_RELEASE_URL = "https://github.com/hootkit-dev/hootbook-android/releases/tag/testing-latest"
        const val PRIVACY_POLICY_URL = "https://github.com/hootkit-dev/hootbook-android/blob/main/PRIVACY.md"
        const val REPORT_BUG_URL = "https://github.com/hootkit-dev/hootbook-android/issues/new"
    }
}
