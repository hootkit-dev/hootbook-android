package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Search: ImageVector
    get() {
        if (_Search != null) {
            return _Search!!
        }
        _Search = ImageVector.Builder(
            name = "Search",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(11.5f, 21.75f)
                curveTo(5.85f, 21.75f, 1.25f, 17.15f, 1.25f, 11.5f)
                curveTo(1.25f, 5.85f, 5.85f, 1.25f, 11.5f, 1.25f)
                curveTo(17.15f, 1.25f, 21.75f, 5.85f, 21.75f, 11.5f)
                curveTo(21.75f, 17.15f, 17.15f, 21.75f, 11.5f, 21.75f)
                close()
                moveTo(11.5f, 2.75f)
                curveTo(6.67f, 2.75f, 2.75f, 6.68f, 2.75f, 11.5f)
                curveTo(2.75f, 16.32f, 6.67f, 20.25f, 11.5f, 20.25f)
                curveTo(16.33f, 20.25f, 20.25f, 16.32f, 20.25f, 11.5f)
                curveTo(20.25f, 6.68f, 16.33f, 2.75f, 11.5f, 2.75f)
                close()
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(22f, 22.75f)
                curveTo(21.81f, 22.75f, 21.62f, 22.68f, 21.47f, 22.53f)
                lineTo(19.47f, 20.53f)
                curveTo(19.18f, 20.24f, 19.18f, 19.76f, 19.47f, 19.47f)
                curveTo(19.76f, 19.18f, 20.24f, 19.18f, 20.53f, 19.47f)
                lineTo(22.53f, 21.47f)
                curveTo(22.82f, 21.76f, 22.82f, 22.24f, 22.53f, 22.53f)
                curveTo(22.38f, 22.68f, 22.19f, 22.75f, 22f, 22.75f)
                close()
            }
        }.build()

        return _Search!!
    }

@Suppress("ObjectPropertyName")
private var _Search: ImageVector? = null
