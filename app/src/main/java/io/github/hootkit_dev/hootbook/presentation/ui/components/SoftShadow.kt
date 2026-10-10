package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A light, diffuse shadow that lifts a card off the background without a hard edge. Dark
 * surfaces barely show it, which is fine: there tonal color already separates cards.
 */
fun Modifier.softShadow(shape: Shape, elevation: Dp = 3.dp): Modifier = shadow(
    elevation = elevation,
    shape = shape,
    clip = false,
    ambientColor = Color.Black.copy(alpha = 0.08f),
    spotColor = Color.Black.copy(alpha = 0.14f)
)
