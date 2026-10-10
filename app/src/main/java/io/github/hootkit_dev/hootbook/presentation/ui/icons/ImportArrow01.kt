package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.ImportArrow01: ImageVector
    get() {
        if (_ImportArrow01 != null) {
            return _ImportArrow01!!
        }
        _ImportArrow01 = ImageVector.Builder(
            name = "ImportArrow01",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(16.8f, 9f)
                horizontalLineTo(12.75f)
                verticalLineTo(13.44f)
                lineTo(14.82f, 11.37f)
                curveTo(14.97f, 11.22f, 15.16f, 11.15f, 15.35f, 11.15f)
                curveTo(15.54f, 11.15f, 15.73f, 11.22f, 15.88f, 11.37f)
                curveTo(16.17f, 11.66f, 16.17f, 12.14f, 15.88f, 12.43f)
                lineTo(12.53f, 15.78f)
                curveTo(12.24f, 16.07f, 11.76f, 16.07f, 11.47f, 15.78f)
                lineTo(8.12f, 12.43f)
                curveTo(7.83f, 12.14f, 7.83f, 11.66f, 8.12f, 11.37f)
                curveTo(8.41f, 11.08f, 8.89f, 11.08f, 9.18f, 11.37f)
                lineTo(11.25f, 13.44f)
                verticalLineTo(9f)
                horizontalLineTo(7.2f)
                curveTo(4f, 9f, 2f, 11f, 2f, 14.2f)
                verticalLineTo(16.79f)
                curveTo(2f, 20f, 4f, 22f, 7.2f, 22f)
                horizontalLineTo(16.79f)
                curveTo(19.99f, 22f, 21.99f, 20f, 21.99f, 16.8f)
                verticalLineTo(14.2f)
                curveTo(22f, 11f, 20f, 9f, 16.8f, 9f)
                close()
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(12.75f, 2.75f)
                curveTo(12.75f, 2.34f, 12.41f, 2f, 12f, 2f)
                curveTo(11.59f, 2f, 11.25f, 2.34f, 11.25f, 2.75f)
                verticalLineTo(9f)
                horizontalLineTo(12.75f)
                verticalLineTo(2.75f)
                close()
            }
        }.build()

        return _ImportArrow01!!
    }

@Suppress("ObjectPropertyName")
private var _ImportArrow01: ImageVector? = null
