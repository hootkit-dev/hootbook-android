package io.github.hootkit_dev.hootbook.presentation.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The window's width in Material's size classes. A phone, or a foldable folded, is compact;
 * an unfolded foldable or a tablet is medium or expanded. Read from the window, so split
 * screen counts too.
 */
data class WindowLayout(val widthDp: Int) {
    /** Navigation moves from the bottom bar to a rail at the start edge. */
    val useNavigationRail: Boolean get() = widthDp >= MEDIUM_WIDTH_DP

    companion object {
        const val MEDIUM_WIDTH_DP = 600
        const val EXPANDED_WIDTH_DP = 840
    }
}

val LocalWindowLayout = staticCompositionLocalOf { WindowLayout(widthDp = 0) }

@Composable
fun rememberWindowLayout(): WindowLayout {
    // The window's own size (split screen, freeform), not the display's
    val widthPx = LocalWindowInfo.current.containerSize.width
    val widthDp = if (widthPx > 0) {
        with(LocalDensity.current) { widthPx.toDp().value.toInt() }
    } else {
        // Before the window is measured: the configuration's width, close enough for a frame
        @Suppress("ConfigurationScreenWidthHeight")
        LocalConfiguration.current.screenWidthDp
    }
    return remember(widthDp) { WindowLayout(widthDp) }
}

/** Width of the collapsed M3E WideNavigationRail, which the content leaves free at the start edge. */
val NavigationRailWidth: Dp = 96.dp

/** Single-column screens stop growing here: wider lines and rows only get harder to read. */
val ReadableContentWidth: Dp = 720.dp

/** Content at most [ReadableContentWidth] wide, centered on wide windows. */
@Composable
fun ReadableWidth(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = ReadableContentWidth)) { content() }
    }
}

