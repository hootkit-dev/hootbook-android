package io.github.hootkit_dev.hootbook.presentation.navigation

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.toRoute
import io.github.hootkit_dev.hootbook.BuildConfig
import kotlinx.coroutines.flow.first

internal fun NavBackStackEntry.mainTabTag(): String? {
    fun matches(name: String?) = name != null && destination.hierarchy.any { it.route?.contains(name) == true }
    return when {
        matches(Home::class.qualifiedName) -> "home"
        matches(Analytics::class.qualifiedName) -> "analytics"
        matches(Transactions::class.qualifiedName) && runCatching { toRoute<Transactions>() == Transactions() }.getOrDefault(false) -> "transactions"
        else -> null
    }
}

private val TAB_ORDER = listOf("home", "analytics", "transactions")

/**
 * Every screen change moves sideways with a fade. Between the bottom-bar tabs it is a short
 * shared-axis X motion, in the direction of the tab bar (Home → Analytics → Transactions
 * slides left); to and from other screens, the same horizontal slide as every sub-screen.
 *
 * Tab switches were once instant because a tap landing mid-animation could leave the old
 * tab's content on screen; the tab motion is kept short (MotionDurations.short) for that.
 */
internal object MainTabMotion {
    /** +1 when moving to a tab to the right in the bar, -1 to the left, null if not tab to tab. */
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabDirection(): Int? {
        val from = TAB_ORDER.indexOf(initialState.mainTabTag() ?: return null)
        val to = TAB_ORDER.indexOf(targetState.mainTabTag() ?: return null)
        return if (to >= from) 1 else -1
    }

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        tabDirection()?.let { HootbookTransitions.sharedAxisXEnter(it) } ?: HootbookTransitions.horizontalSlideEnter(this)
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        tabDirection()?.let { HootbookTransitions.sharedAxisXExit(it) } ?: HootbookTransitions.horizontalSlideExit(this)
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        tabDirection()?.let { HootbookTransitions.sharedAxisXEnter(it) } ?: HootbookTransitions.horizontalSlidePopEnter(this)
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        tabDirection()?.let { HootbookTransitions.sharedAxisXExit(it) } ?: HootbookTransitions.horizontalSlidePopExit(this)
    }
}

/** Local debug-only timing, from accepted tab request to the target entry becoming RESUMED. */
internal object MainTabTiming {
    private data class Pending(val id: Int, val tab: String, val start: Long)
    private var sequence = 0
    private var pending: Pending? = null
    fun request(tab: String) {
        if (!BuildConfig.DEBUG) return
        pending?.let { Log.d("HootbookTabPerf", "SUPERSEDED|${it.id}|${it.tab}") }
        val p = Pending(++sequence, tab, SystemClock.elapsedRealtimeNanos())
        pending = p
        Log.d("HootbookTabPerf", "REQUEST|${p.id}|${p.tab}|${p.start}")
    }
    fun settled(tab: String) {
        if (!BuildConfig.DEBUG) return
        val p = pending ?: return
        if (p.tab != tab) return
        Log.d("HootbookTabPerf", "SETTLED|${p.id}|$tab|${(SystemClock.elapsedRealtimeNanos() - p.start) / 1_000_000.0}")
        pending = null
    }
}

@Composable
internal fun ObserveMainTabSettled(entry: NavBackStackEntry?) {
    if (!BuildConfig.DEBUG) return
    LaunchedEffect(entry?.id) {
        val current = entry ?: return@LaunchedEffect
        val tab = current.mainTabTag() ?: return@LaunchedEffect
        current.lifecycle.currentStateFlow.first { it == Lifecycle.State.RESUMED }
        MainTabTiming.settled(tab)
    }
}
