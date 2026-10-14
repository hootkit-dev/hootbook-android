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
import kotlinx.serialization.Serializable

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

@Serializable object AppLockSettings

@Serializable object OnBoarding

@Serializable object Home

@Serializable
data class Transactions(
    val accountId: String? = null,
    val categoryId: String? = null,
    val subscriptionId: String? = null,
    val tagId: String? = null,
    val from: String? = null,
    val to: String? = null,
    val currency: String? = null,
    val kinds: String? = null,
)

@Serializable object Settings
@Serializable data class CurrencySettings(val currency: String? = null)
@Serializable data class Subscriptions(val subscriptionId: String? = null)
@Serializable object Categories
@Serializable object TagsManagement
@Serializable object DuplicateDetection

@Serializable object Analytics


@Serializable data class TransactionDetail(val transactionId: String, val sharedElementKey: String? = null)

@Serializable data class AddTransaction(
    val initialTab: Int = 0,
    val subscriptionId: String? = null,
    val type: String? = null,
    // A quick template to fill the form with, from a launcher shortcut or the home-screen widget
    val templateId: String? = null
)

@Serializable data class AccountDetail(val accountId: String, val history: Boolean = false)



@Serializable object QuickTemplates

@Serializable object AiAssistant


@Serializable object Appearance

// Brokerage connections are Core-local string handles, transported as text in navigation.
@Serializable data class Investments(val connectionId: String? = null, val accountId: String? = null)

@Serializable object ManageAccounts

@Serializable object Profile

@Serializable data class Contacts(
    val personId: String? = null,
    val sharedElementKey: String? = null
)


@Serializable object DataPrivacy

/** Settings → Sync: multi-device sync through Firebase (docs/sync.md) */
@Serializable object CloudSync


@Serializable object NotificationSettings

@Serializable data class Budgets(val sharedElementPrefix: String? = null)

@Serializable data class BudgetDetail(
    val budgetId: String,
    val sharedElementKey: String? = null,
    val startDate: String? = null,
    val endDate: String? = null
)

@Serializable data class BudgetHistory(val budgetId: String)


@Serializable data class AddAccount(val category: String? = null)



@Serializable object About

@Serializable object Licenses

@Serializable data class LendBorrow(val filter: String? = null)

@Serializable data class PersonDetail(
    val personId: String,
    val sharedElementKey: String? = null
)

// Routes where bottom navigation should be visible
val BOTTOM_NAV_ROUTES = setOf(
    Home::class.qualifiedName,
    Analytics::class.qualifiedName,
    Transactions::class.qualifiedName
)

@Serializable data class AccountCategoryRoute(val category: String)

@Serializable data class MerchantIcons(val name: String? = null)
