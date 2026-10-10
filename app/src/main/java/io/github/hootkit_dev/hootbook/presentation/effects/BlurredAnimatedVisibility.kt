package io.github.hootkit_dev.hootbook.presentation.effects

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.hootkit_dev.hootbook.presentation.ui.theme.MotionDurations

/**
 * Visibility toggle used by the bottom navigation, FAB, and selection toolbars.
 *
 * Historically this animated a blur [androidx.compose.ui.graphics.RenderEffect] (and a
 * RenderScript blur on Android 10/11) on top of the enter/exit transition. The blur forced an
 * offscreen render pass for the whole subtree on every frame of the transition, and the
 * RenderScript path allocated bitmaps per draw. It is now a plain, short fade; the name is kept so
 * call sites stay unchanged.
 */
@Composable
fun BlurredAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn(tween(MotionDurations.short)),
    exit: ExitTransition = fadeOut(tween(MotionDurations.short)),
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = enter,
        exit = exit,
        modifier = modifier,
        content = content
    )
}
