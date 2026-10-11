package io.github.hootkit_dev.hootbook.presentation.core.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavBackStackEntry
import io.github.hootkit_dev.hootbook.presentation.navigation.HootbookTransitions

/** Original short shared-axis tab motion, with horizontal transitions for pushed screens. */
internal object CoreNavigationMotion {
    private val tabs = listOf("home", "analytics", "transactions")
    private fun NavBackStackEntry.mainTab(): String? {
        val route = destination.route?.substringBefore('?') ?: return null
        if (route !in tabs) return null
        if (route == "transactions" && listOf("accountId", "transactionId", "subscriptionId", "templateId",
                "categoryId", "newKind", "from", "to", "currency", "kinds").any { arguments?.getString(it) != null }) return null
        return route
    }
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.direction(): Int? {
        val from = tabs.indexOf(initialState.mainTab() ?: return null)
        val to = tabs.indexOf(targetState.mainTab() ?: return null)
        return if(to >= from) 1 else -1
    }
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        direction()?.let(HootbookTransitions::sharedAxisXEnter) ?: HootbookTransitions.horizontalSlideEnter(this)
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        direction()?.let(HootbookTransitions::sharedAxisXExit) ?: HootbookTransitions.horizontalSlideExit(this)
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        direction()?.let(HootbookTransitions::sharedAxisXEnter) ?: HootbookTransitions.horizontalSlidePopEnter(this)
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        direction()?.let(HootbookTransitions::sharedAxisXExit) ?: HootbookTransitions.horizontalSlidePopExit(this)
    }
}
