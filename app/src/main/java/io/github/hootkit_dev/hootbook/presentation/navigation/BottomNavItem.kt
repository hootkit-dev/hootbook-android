package io.github.hootkit_dev.hootbook.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.icons.*

/** Presentation-only routes; ledger identifiers remain Core string identifiers. */
sealed class BottomNavItem(val route: String, @StringRes val titleRes: Int, val icon: ImageVector) {
    data object Home : BottomNavItem("home", R.string.home_title, Iconax.Home)
    data object Analytics : BottomNavItem("analytics", R.string.analytics, Iconax.FavoriteChart)
    data object Transactions : BottomNavItem("transactions", R.string.transactions, Iconax.ReceiptItem)
}
