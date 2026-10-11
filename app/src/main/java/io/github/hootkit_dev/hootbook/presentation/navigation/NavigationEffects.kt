package io.github.hootkit_dev.hootbook.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.MotionDurations

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimatedContentScope = compositionLocalOf<AnimatedContentScope?> { null }

// Shared bounds transform configurations
object SharedTransitionTransforms {
    val smooth: BoundsTransform = BoundsTransform { _, _ ->
        tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing)
    }

    val snappy: BoundsTransform = BoundsTransform { _, _ ->
        tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing)
    }

    val quick: BoundsTransform = BoundsTransform { _, _ ->
        tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing)
    }
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedBounds(
    key: Any,
    boundsTransform: BoundsTransform = SharedTransitionTransforms.smooth
) =
    with(LocalSharedTransitionScope.current) {
        val animatedContentScope = LocalAnimatedContentScope.current
        return@with if (this != null && animatedContentScope != null) {
            sharedBounds(
                rememberSharedContentState(key),
                animatedContentScope,
                boundsTransform = boundsTransform
            ).skipToLookaheadSize()
        } else this@sharedBounds
    }

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedElement(
    key: Any,
    boundsTransform: BoundsTransform = SharedTransitionTransforms.smooth
) =
    with(LocalSharedTransitionScope.current) {
        val animatedContentScope = LocalAnimatedContentScope.current
        return@with if (this != null && animatedContentScope != null) {
            sharedElement(
                rememberSharedContentState(key),
                animatedContentScope,
                boundsTransform = boundsTransform
            )
        } else this@sharedElement
    }
