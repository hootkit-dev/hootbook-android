package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Whatsapp: ImageVector
    get() {
        if (_Whatsapp != null) {
            return _Whatsapp!!
        }
        _Whatsapp = ImageVector.Builder(
            name = "Whatsapp",
            defaultWidth = 800.dp,
            defaultHeight = 800.dp,
            viewportWidth = 32f,
            viewportHeight = 32f
        ).apply {
            path(
                fill = SolidColor(Color(0xFFBFC8D0)),
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(16f, 31f)
                curveTo(23.732f, 31f, 30f, 24.732f, 30f, 17f)
                curveTo(30f, 9.268f, 23.732f, 3f, 16f, 3f)
                curveTo(8.268f, 3f, 2f, 9.268f, 2f, 17f)
                curveTo(2f, 19.511f, 2.661f, 21.867f, 3.818f, 23.905f)
                lineTo(2f, 31f)
                lineTo(9.315f, 29.304f)
                curveTo(11.301f, 30.385f, 13.579f, 31f, 16f, 31f)
                close()
                moveTo(16f, 28.846f)
                curveTo(22.542f, 28.846f, 27.846f, 23.542f, 27.846f, 17f)
                curveTo(27.846f, 10.458f, 22.542f, 5.154f, 16f, 5.154f)
                curveTo(9.458f, 5.154f, 4.154f, 10.458f, 4.154f, 17f)
                curveTo(4.154f, 19.526f, 4.944f, 21.868f, 6.292f, 23.79f)
                lineTo(5.231f, 27.769f)
                lineTo(9.28f, 26.757f)
                curveTo(11.189f, 28.075f, 13.505f, 28.846f, 16f, 28.846f)
                close()
            }
            path(
                fill = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFF5BD066),
                        1f to Color(0xFF27B43E)
                    ),
                    start = Offset(26.5f, 7f),
                    end = Offset(4f, 28f)
                )
            ) {
                moveTo(28f, 16f)
                curveTo(28f, 22.627f, 22.627f, 28f, 16f, 28f)
                curveTo(13.472f, 28f, 11.127f, 27.218f, 9.193f, 25.884f)
                lineTo(5.091f, 26.909f)
                lineTo(6.166f, 22.878f)
                curveTo(4.801f, 20.931f, 4f, 18.559f, 4f, 16f)
                curveTo(4f, 9.373f, 9.373f, 4f, 16f, 4f)
                curveTo(22.627f, 4f, 28f, 9.373f, 28f, 16f)
                close()
            }
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(16f, 30f)
                curveTo(23.732f, 30f, 30f, 23.732f, 30f, 16f)
                curveTo(30f, 8.268f, 23.732f, 2f, 16f, 2f)
                curveTo(8.268f, 2f, 2f, 8.268f, 2f, 16f)
                curveTo(2f, 18.511f, 2.661f, 20.867f, 3.818f, 22.905f)
                lineTo(2f, 30f)
                lineTo(9.315f, 28.304f)
                curveTo(11.301f, 29.385f, 13.579f, 30f, 16f, 30f)
                close()
                moveTo(16f, 27.846f)
                curveTo(22.542f, 27.846f, 27.846f, 22.542f, 27.846f, 16f)
                curveTo(27.846f, 9.458f, 22.542f, 4.154f, 16f, 4.154f)
                curveTo(9.458f, 4.154f, 4.154f, 9.458f, 4.154f, 16f)
                curveTo(4.154f, 18.526f, 4.944f, 20.868f, 6.292f, 22.79f)
                lineTo(5.231f, 26.769f)
                lineTo(9.28f, 25.757f)
                curveTo(11.189f, 27.075f, 13.505f, 27.846f, 16f, 27.846f)
                close()
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(12.5f, 9.5f)
                curveTo(12.167f, 8.831f, 11.656f, 8.891f, 11.141f, 8.891f)
                curveTo(10.219f, 8.891f, 8.781f, 9.995f, 8.781f, 12.05f)
                curveTo(8.781f, 13.734f, 9.523f, 15.578f, 12.024f, 18.336f)
                curveTo(14.438f, 20.998f, 17.609f, 22.375f, 20.242f, 22.328f)
                curveTo(22.875f, 22.281f, 23.417f, 20.015f, 23.417f, 19.25f)
                curveTo(23.417f, 18.911f, 23.206f, 18.742f, 23.061f, 18.696f)
                curveTo(22.164f, 18.265f, 20.509f, 17.463f, 20.133f, 17.312f)
                curveTo(19.756f, 17.162f, 19.56f, 17.366f, 19.438f, 17.476f)
                curveTo(19.096f, 17.802f, 18.419f, 18.761f, 18.188f, 18.976f)
                curveTo(17.956f, 19.192f, 17.61f, 19.083f, 17.466f, 19.001f)
                curveTo(16.937f, 18.789f, 15.503f, 18.151f, 14.359f, 17.043f)
                curveTo(12.945f, 15.672f, 12.862f, 15.2f, 12.596f, 14.78f)
                curveTo(12.383f, 14.444f, 12.539f, 14.238f, 12.617f, 14.148f)
                curveTo(12.922f, 13.797f, 13.343f, 13.254f, 13.531f, 12.984f)
                curveTo(13.72f, 12.715f, 13.57f, 12.305f, 13.48f, 12.05f)
                curveTo(13.094f, 10.953f, 12.766f, 10.035f, 12.5f, 9.5f)
                close()
            }
        }.build()

        return _Whatsapp!!
    }

@Suppress("ObjectPropertyName")
private var _Whatsapp: ImageVector? = null
