package io.github.hootkit_dev.hootbook.presentation.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The shell's Add FAB and bottom navigation follow the route. A screen that shows a detail or a
 * form of its own on the same route (a transaction, a subscription) hides both with
 * [HideHostChrome] meanwhile, as any other secondary page has neither.
 */
class HostChromeState {
    var suppressed by mutableIntStateOf(0)
        internal set
}

val LocalHostChrome = staticCompositionLocalOf { HostChromeState() }

/** Hides the shell's Add FAB and bottom navigation while this is composed with [active] true. */
@Composable
fun HideHostChrome(active: Boolean = true) {
    val host = LocalHostChrome.current
    DisposableEffect(host, active) {
        if (active) host.suppressed++
        onDispose { if (active) host.suppressed-- }
    }
}
