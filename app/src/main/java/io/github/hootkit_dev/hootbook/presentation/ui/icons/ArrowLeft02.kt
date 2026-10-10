package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.ArrowLeft02: ImageVector
    get() {
        if (_ArrowLeft02 != null) {
            return _ArrowLeft02!!
        }
        _ArrowLeft02 = ImageVector.Builder(
            name = "ArrowLeft02",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(15f, 20.67f)
                curveTo(14.81f, 20.67f, 14.62f, 20.6f, 14.47f, 20.449f)
                lineTo(7.95f, 13.929f)
                curveTo(6.89f, 12.87f, 6.89f, 11.13f, 7.95f, 10.069f)
                lineTo(14.47f, 3.55f)
                curveTo(14.76f, 3.26f, 15.24f, 3.26f, 15.53f, 3.55f)
                curveTo(15.82f, 3.84f, 15.82f, 4.32f, 15.53f, 4.61f)
                lineTo(9.01f, 11.13f)
                curveTo(8.53f, 11.609f, 8.53f, 12.389f, 9.01f, 12.87f)
                lineTo(15.53f, 19.389f)
                curveTo(15.82f, 19.68f, 15.82f, 20.16f, 15.53f, 20.449f)
                curveTo(15.38f, 20.59f, 15.19f, 20.67f, 15f, 20.67f)
                close()
            }
        }.build()

        return _ArrowLeft02!!
    }

@Suppress("ObjectPropertyName")
private var _ArrowLeft02: ImageVector? = null
