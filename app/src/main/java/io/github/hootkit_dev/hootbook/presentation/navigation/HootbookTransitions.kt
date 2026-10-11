package io.github.hootkit_dev.hootbook.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.MotionDurations

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

// Centralized transition definitions
object HootbookTransitions {

    // Horizontal slide transitions for sub-screens
    val horizontalSlideEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(durationMillis = MotionDurations.standard))
    }

    val horizontalSlideExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { -it / 4 },
            animationSpec = tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(durationMillis = MotionDurations.standard))
    }

    val horizontalSlidePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { -it / 4 },
            animationSpec = tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(durationMillis = MotionDurations.standard))
    }

    val horizontalSlidePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(durationMillis = MotionDurations.standard))
    }

    // Shared axis X between peer tabs: a short slide (a tenth of the width) with a fade, in
    // the direction of travel (+1 to the right in the tab bar, -1 to the left)
    fun sharedAxisXEnter(direction: Int): EnterTransition =
        slideInHorizontally(
            initialOffsetX = { direction * it / 10 },
            animationSpec = tween(durationMillis = MotionDurations.short, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(durationMillis = MotionDurations.short))

    fun sharedAxisXExit(direction: Int): ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { -direction * it / 10 },
            animationSpec = tween(durationMillis = MotionDurations.short, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(durationMillis = MotionDurations.short))

    // FAB to screen scale transitions
    val fabScaleEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(animationSpec = tween(durationMillis = MotionDurations.standard)) +
            scaleIn(
                initialScale = 0.8f,
                animationSpec = tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing)
            )
    }

    val fabScaleExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(animationSpec = tween(durationMillis = MotionDurations.standard)) +
            scaleOut(
                targetScale = 1.1f,
                animationSpec = tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing)
            )
    }

    val fabScalePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(animationSpec = tween(durationMillis = MotionDurations.standard)) +
            scaleIn(
                initialScale = 1.1f,
                animationSpec = tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing)
            )
    }

    val fabScalePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(animationSpec = tween(durationMillis = MotionDurations.standard)) +
            scaleOut(
                targetScale = 0.8f,
                animationSpec = tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing)
            )
    }

    // Scale transitions for detail screens
    val scaleEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(animationSpec = tween(durationMillis = MotionDurations.standard)) +
            scaleIn(animationSpec = tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing))
    }

    val scaleExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(animationSpec = tween(durationMillis = MotionDurations.standard)) +
            scaleOut(animationSpec = tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing))
    }

    // None transitions - for screens using shared element transitions entirely
    val noneEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(animationSpec = tween(durationMillis = MotionDurations.standard))
    }

    val noneExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(animationSpec = tween(durationMillis = MotionDurations.standard))
    }
}
