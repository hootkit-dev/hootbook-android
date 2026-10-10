package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Discord: ImageVector
    get() {
        if (_Discord != null) {
            return _Discord!!
        }
        _Discord = ImageVector.Builder(
            name = "Discord",
            defaultWidth = 126.64.dp,
            defaultHeight = 96.dp,
            viewportWidth = 126.64f,
            viewportHeight = 96f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(81.15f, 0f)
                curveToRelative(-1.24f, 2.2f, -2.35f, 4.47f, -3.36f, 6.79f)
                curveToRelative(-9.6f, -1.44f, -19.37f, -1.44f, -28.99f, 0f)
                curveToRelative(-0.99f, -2.32f, -2.12f, -4.6f, -3.36f, -6.79f)
                curveToRelative(-9.02f, 1.54f, -17.81f, 4.24f, -26.14f, 8.06f)
                curveTo(2.78f, 32.53f, -1.69f, 56.37f, 0.53f, 79.89f)
                curveToRelative(9.67f, 7.15f, 20.51f, 12.6f, 32.05f, 16.09f)
                curveToRelative(2.6f, -3.49f, 4.9f, -7.2f, 6.87f, -11.06f)
                curveToRelative(-3.74f, -1.39f, -7.35f, -3.13f, -10.81f, -5.15f)
                curveToRelative(0.91f, -0.66f, 1.79f, -1.34f, 2.65f, -2f)
                curveToRelative(20.28f, 9.55f, 43.77f, 9.55f, 64.08f, 0f)
                curveToRelative(0.86f, 0.71f, 1.74f, 1.39f, 2.65f, 2f)
                curveToRelative(-3.46f, 2.05f, -7.07f, 3.76f, -10.84f, 5.18f)
                curveToRelative(1.97f, 3.86f, 4.27f, 7.58f, 6.87f, 11.06f)
                curveToRelative(11.54f, -3.49f, 22.38f, -8.92f, 32.05f, -16.06f)
                curveToRelative(2.63f, -27.28f, -4.5f, -50.92f, -18.82f, -71.85f)
                curveTo(98.98f, 4.27f, 90.19f, 1.57f, 81.18f, 0.05f)
                lineToRelative(-0.03f, -0.05f)
                close()
                moveTo(42.28f, 65.41f)
                curveToRelative(-6.24f, 0f, -11.42f, -5.66f, -11.42f, -12.65f)
                reflectiveCurveToRelative(4.98f, -12.68f, 11.39f, -12.68f)
                reflectiveCurveToRelative(11.52f, 5.71f, 11.42f, 12.68f)
                curveToRelative(-0.1f, 6.97f, -5.03f, 12.65f, -11.39f, 12.65f)
                close()
                moveTo(84.36f, 65.41f)
                curveToRelative(-6.26f, 0f, -11.39f, -5.66f, -11.39f, -12.65f)
                reflectiveCurveToRelative(4.98f, -12.68f, 11.39f, -12.68f)
                reflectiveCurveToRelative(11.49f, 5.71f, 11.39f, 12.68f)
                curveToRelative(-0.1f, 6.97f, -5.03f, 12.65f, -11.39f, 12.65f)
                close()
            }
        }.build()

        return _Discord!!
    }

@Suppress("ObjectPropertyName")
private var _Discord: ImageVector? = null
