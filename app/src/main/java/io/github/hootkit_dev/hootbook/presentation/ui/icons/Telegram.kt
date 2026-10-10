package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Telegram: ImageVector
    get() {
        if (_Telegram != null) {
            return _Telegram!!
        }
        _Telegram = ImageVector.Builder(
            name = "Telegram",
            defaultWidth = 800.dp,
            defaultHeight = 800.dp,
            viewportWidth = 32f,
            viewportHeight = 32f
        ).apply {
            path(
                fill = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFF37BBFE),
                        1f to Color(0xFF007DBB)
                    ),
                    start = Offset(16f, 2f),
                    end = Offset(16f, 30f)
                )
            ) {
                moveTo(16f, 16f)
                moveToRelative(-14f, 0f)
                arcToRelative(14f, 14f, 0f, isMoreThanHalf = true, isPositiveArc = true, 28f, 0f)
                arcToRelative(14f, 14f, 0f, isMoreThanHalf = true, isPositiveArc = true, -28f, 0f)
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(22.987f, 10.209f)
                curveTo(23.111f, 9.403f, 22.345f, 8.768f, 21.629f, 9.082f)
                lineTo(7.365f, 15.345f)
                curveTo(6.851f, 15.57f, 6.889f, 16.348f, 7.421f, 16.518f)
                lineTo(10.363f, 17.455f)
                curveTo(10.925f, 17.633f, 11.533f, 17.541f, 12.023f, 17.202f)
                lineTo(18.655f, 12.62f)
                curveTo(18.855f, 12.482f, 19.073f, 12.767f, 18.902f, 12.943f)
                lineTo(14.128f, 17.865f)
                curveTo(13.665f, 18.342f, 13.757f, 19.151f, 14.314f, 19.5f)
                lineTo(19.659f, 22.852f)
                curveTo(20.258f, 23.228f, 21.03f, 22.851f, 21.142f, 22.126f)
                lineTo(22.987f, 10.209f)
                close()
            }
        }.build()

        return _Telegram!!
    }

@Suppress("ObjectPropertyName")
private var _Telegram: ImageVector? = null
