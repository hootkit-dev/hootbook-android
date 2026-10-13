package io.github.hootkit_dev.hootbook.data.preferences

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.hootkit_dev.hootbook.R

enum class AppIcon(
    val activityAliasSuffix: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val previewRes: Int,
) {
    // Keep installed launcher component names stable across the brand update.
    HOOTBOOK_WHITE("Original", R.string.logo_hootbook_white, R.drawable.hootbook_icon_white),
    HOOTBOOK_CREAM("Anarchy", R.string.logo_hootbook_cream, R.drawable.hootbook_icon_cream),
    HOOT_WHITE("Zenith", R.string.logo_hoot_white, R.drawable.hoot_icon_white),
    HOOT_CREAM("Comic", R.string.logo_hoot_cream, R.drawable.hoot_icon_cream),
    BOOK_WHITE("Monochrome", R.string.logo_book_white, R.drawable.book_icon_white),
    BOOK_CREAM("BookCream", R.string.logo_book_cream, R.drawable.book_icon_cream);

    companion object {
        private val legacyNames = mapOf(
            "ORIGINAL" to HOOTBOOK_WHITE,
            "ANARCHY" to HOOTBOOK_CREAM,
            "ZENITH" to HOOT_WHITE,
            "COMIC" to HOOT_CREAM,
            "MONOCHROME" to BOOK_WHITE,
        )
        val storedNames: Set<String> = entries.map { it.name }.toSet() + legacyNames.keys

        fun fromStoredName(name: String): AppIcon = legacyNames[name] ?: valueOf(name)
    }
}
