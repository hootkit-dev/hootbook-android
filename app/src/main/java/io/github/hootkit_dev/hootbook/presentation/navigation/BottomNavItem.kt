package io.github.hootkit_dev.hootbook.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.icons.FavoriteChart
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Home
import io.github.hootkit_dev.hootbook.presentation.ui.icons.ReceiptItem
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import kotlin.reflect.KClass
import io.github.hootkit_dev.hootbook.presentation.navigation.Home as HomeDestination
import io.github.hootkit_dev.hootbook.presentation.navigation.Analytics as AnalyticsDestination
import io.github.hootkit_dev.hootbook.presentation.navigation.Transactions as TransactionsDestination

sealed class BottomNavItem(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val destination: Any,
    val destinationType: KClass<*>
) {
    data object Home : BottomNavItem(
        route = "home",
        titleRes = R.string.home_title,
        icon = Iconax.Home,
        destination = HomeDestination,
        destinationType = HomeDestination::class
    )
    
    data object Analytics : BottomNavItem(
        route = "analytics",
        titleRes = R.string.analytics,
        icon = Iconax.FavoriteChart,
        destination = AnalyticsDestination,
        destinationType = AnalyticsDestination::class
    )

    data object Transactions : BottomNavItem(
        route = "transactions",
        titleRes = R.string.transactions,
        icon = Iconax.ReceiptItem,
        destination = TransactionsDestination(),
        destinationType = TransactionsDestination::class
    )
}