package io.github.hootkit_dev.hootbook.presentation.navigation

import androidx.compose.foundation.layout.fillMaxHeight
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.toRoute

/**
 * Material 3 Expressive [ShortNavigationBar]: default colours, a pill indicator behind the icon
 * and labels always shown.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HootbookBottomNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    currentDestination: NavDestination?,
    visible: Boolean
) {
    val navigationItems = listOf(BottomNavItem.Home, BottomNavItem.Analytics, BottomNavItem.Transactions)
    val view = LocalView.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        ShortNavigationBar {
            navigationItems.forEach { item ->
                val selected = currentDestination?.hierarchy?.any {
                    it.route?.contains(item.destinationType.qualifiedName ?: "") == true
                } == true
                ShortNavigationBarItem(
                    selected = selected,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        navController.selectMainTab(item)
                    },
                    // The label names the item; the icon would only repeat it
                    icon = { Icon(imageVector = item.icon, contentDescription = null) },
                    label = { Text(text = stringResource(item.titleRes), maxLines = 1) }
                )
            }
        }
    }
}

/**
 * The same destinations as a Material 3 Expressive [WideNavigationRail] (collapsed) at the
 * start edge, for wide windows (an unfolded foldable, a tablet), where a bottom bar would
 * stretch across the screen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HootbookNavigationRail(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    currentDestination: NavDestination?,
    visible: Boolean
) {
    val navigationItems = listOf(BottomNavItem.Home, BottomNavItem.Analytics, BottomNavItem.Transactions)
    val view = LocalView.current
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        WideNavigationRail(
            modifier = Modifier.fillMaxHeight(),
            arrangement = Arrangement.Center
        ) {
            navigationItems.forEach { item ->
                val selected = currentDestination?.hierarchy?.any {
                    it.route?.contains(item.destinationType.qualifiedName ?: "") == true
                } == true
                WideNavigationRailItem(
                    selected = selected,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        navController.selectMainTab(item)
                    },
                    icon = { Icon(imageVector = item.icon, contentDescription = null) },
                    label = { Text(text = stringResource(item.titleRes), maxLines = 1) },
                    railExpanded = false
                )
            }
        }
    }
}

internal fun NavHostController.selectMainTab(item: BottomNavItem) {
    // Keep an already selected root page; contextual routes still reset below.
    if (isCurrentMainTabRoot(item)) return
    MainTabTiming.request(item.route)
    if (item.destination == Home) {
        popBackStack(Home, inclusive = false, saveState = true)
    } else if (isCurrentMainTab(item)) {
        safeNavigate(item.destination) {
            popUpTo(item.destinationType) { inclusive = true }
            launchSingleTop = true
        }
    } else {
        safeNavigate(item.destination) {
            popUpTo<Home>() { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

/** Do not confuse a filtered/search transaction route with the default main tab. */
private fun NavHostController.isCurrentMainTabRoot(item: BottomNavItem): Boolean {
    val entry = currentBackStackEntry ?: return false
    val routeName = item.destinationType.qualifiedName ?: return false
    if (entry.destination.hierarchy.none { it.route?.contains(routeName) == true }) return false
    return when (item) {
        BottomNavItem.Transactions -> runCatching {
            entry.toRoute<Transactions>() == Transactions()
        }.getOrDefault(false)
        else -> true
    }
}

/** Read live navigation state: a rapid tap may arrive before the selected UI recomposes. */
private fun NavHostController.isCurrentMainTab(item: BottomNavItem): Boolean {
    val name = item.destinationType.qualifiedName ?: return false
    return currentDestination?.hierarchy?.any { it.route?.contains(name) == true } == true
}
