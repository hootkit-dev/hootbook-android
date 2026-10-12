package io.github.hootkit_dev.hootbook.presentation.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

/**
 * Extension for NavController to perform navigation safely.
 * Prevents "Double Navigation" or "Double Pop" race conditions during transitions
 * by checking if the current back stack entry is in the RESUMED state.
 */
fun NavController.safeNavigate(
    route: Any,
    builder: NavOptionsBuilder.() -> Unit = {}
) {
    if (currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true) {
        navigate(route, builder)
    }
}

/**
 * Extension for NavController to pop the back stack safely.
 * Prevents race conditions during transitions by checking lifecycle state.
 */
fun NavController.safePopBackStack() {
    if (currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true) {
        popBackStack()
    }
}
