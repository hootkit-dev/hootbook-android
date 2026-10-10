package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Gallery: ImageVector
    get() {
        if (_Gallery != null) {
            return _Gallery!!
        }
        _Gallery = ImageVector.Builder(
            name = "Gallery",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            group(
                clipPathData = PathData {
                    moveTo(0f, 0f)
                    horizontalLineToRelative(24f)
                    verticalLineToRelative(24f)
                    horizontalLineToRelative(-24f)
                    close()
                }
            ) {
                path(fill = SolidColor(Color.White)) {
                    moveTo(2.58f, 19.01f)
                    lineTo(2.56f, 19.03f)
                    curveTo(2.29f, 18.44f, 2.12f, 17.77f, 2.05f, 17.03f)
                    curveTo(2.12f, 17.76f, 2.31f, 18.42f, 2.58f, 19.01f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(9f, 10.38f)
                    curveTo(10.315f, 10.38f, 11.38f, 9.315f, 11.38f, 8f)
                    curveTo(11.38f, 6.686f, 10.315f, 5.62f, 9f, 5.62f)
                    curveTo(7.686f, 5.62f, 6.62f, 6.686f, 6.62f, 8f)
                    curveTo(6.62f, 9.315f, 7.686f, 10.38f, 9f, 10.38f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(16.19f, 2f)
                    horizontalLineTo(7.81f)
                    curveTo(4.17f, 2f, 2f, 4.17f, 2f, 7.81f)
                    verticalLineTo(16.19f)
                    curveTo(2f, 17.28f, 2.19f, 18.23f, 2.56f, 19.03f)
                    curveTo(3.42f, 20.93f, 5.26f, 22f, 7.81f, 22f)
                    horizontalLineTo(16.19f)
                    curveTo(19.83f, 22f, 22f, 19.83f, 22f, 16.19f)
                    verticalLineTo(13.9f)
                    verticalLineTo(7.81f)
                    curveTo(22f, 4.17f, 19.83f, 2f, 16.19f, 2f)
                    close()
                    moveTo(20.37f, 12.5f)
                    curveTo(19.59f, 11.83f, 18.33f, 11.83f, 17.55f, 12.5f)
                    lineTo(13.39f, 16.07f)
                    curveTo(12.61f, 16.74f, 11.35f, 16.74f, 10.57f, 16.07f)
                    lineTo(10.23f, 15.79f)
                    curveTo(9.52f, 15.17f, 8.39f, 15.11f, 7.59f, 15.65f)
                    lineTo(3.85f, 18.16f)
                    curveTo(3.63f, 17.6f, 3.5f, 16.95f, 3.5f, 16.19f)
                    verticalLineTo(7.81f)
                    curveTo(3.5f, 4.99f, 4.99f, 3.5f, 7.81f, 3.5f)
                    horizontalLineTo(16.19f)
                    curveTo(19.01f, 3.5f, 20.5f, 4.99f, 20.5f, 7.81f)
                    verticalLineTo(12.61f)
                    lineTo(20.37f, 12.5f)
                    close()
                }
            }
        }.build()

        return _Gallery!!
    }

@Suppress("ObjectPropertyName")
private var _Gallery: ImageVector? = null
