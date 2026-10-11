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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import io.github.hootkit_dev.hootbook.presentation.core.navigation.selectCoreTab

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
                    it.route?.substringBefore('?') == item.route
                } == true
                ShortNavigationBarItem(
                    selected = selected,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        navController.selectCoreTab(item.route)
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
                    it.route?.substringBefore('?') == item.route
                } == true
                WideNavigationRailItem(
                    selected = selected,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        navController.selectCoreTab(item.route)
                    },
                    icon = { Icon(imageVector = item.icon, contentDescription = null) },
                    label = { Text(text = stringResource(item.titleRes), maxLines = 1) },
                    railExpanded = false
                )
            }
        }
    }
}
