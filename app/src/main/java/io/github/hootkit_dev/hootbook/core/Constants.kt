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
     * Database Configuration
     */
    object Database {
        const val DATABASE_NAME = "cashiro_database"
        const val CURRENT_VERSION = 2
        const val TRANSACTION_HASH_DEFAULT = ""
    }

    /**
     * External Links
     */
    object Links {
        const val DISCORD_URL = "https://discord.gg/6qaYgpJTg"
        const val GITHUB_URL = "https://github.com/hootkit-dev/hootbook-android"
        const val UPSTREAM_GITHUB_URL = "https://github.com/ritesh-kanwar/Cashiro"
        const val DEBUG_RELEASE_URL = "https://github.com/hootkit-dev/hootbook-android/releases/tag/debug-latest"
        const val TESTING_RELEASE_URL = "https://github.com/hootkit-dev/hootbook-android/releases/tag/testing-latest"
        const val WEBSITE_URL = "https://ritesh-kanwar.github.io/cashiro.showcase"
        const val PRIVACY_POLICY_URL = "https://github.com/hootkit-dev/hootbook-android/blob/main/PRIVACY.md"
        const val TERMS_OF_SERVICE_URL = "https://ritesh-kanwar.github.io/cashiro.showcase/terms"
        const val FAQ_URL = "https://ritesh-kanwar.github.io/cashiro.showcase/faq"
        const val GUIDE_URL = "https://ritesh-kanwar.github.io/cashiro.showcase/guides"
        const val REPORT_BUG_URL = "https://github.com/hootkit-dev/hootbook-android/issues/new"
    }
}
