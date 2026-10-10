package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.VideoPlay: ImageVector
    get() {
        if (_VideoPlay != null) {
            return _VideoPlay!!
        }
        _VideoPlay = ImageVector.Builder(
            name = "VideoPlay",
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
                    moveTo(14.73f, 2f)
                    horizontalLineTo(9.27f)
                    verticalLineTo(6.36f)
                    horizontalLineTo(14.73f)
                    verticalLineTo(2f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(16.23f, 2f)
                    verticalLineTo(6.36f)
                    horizontalLineTo(21.87f)
                    curveTo(21.36f, 3.61f, 19.33f, 2.01f, 16.23f, 2f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(2f, 7.86f)
                    verticalLineTo(16.19f)
                    curveTo(2f, 19.83f, 4.17f, 22f, 7.81f, 22f)
                    horizontalLineTo(16.19f)
                    curveTo(19.83f, 22f, 22f, 19.83f, 22f, 16.19f)
                    verticalLineTo(7.86f)
                    horizontalLineTo(2f)
                    close()
                    moveTo(14.44f, 16.18f)
                    lineTo(12.36f, 17.38f)
                    curveTo(11.92f, 17.63f, 11.49f, 17.76f, 11.09f, 17.76f)
                    curveTo(10.79f, 17.76f, 10.52f, 17.69f, 10.27f, 17.55f)
                    curveTo(9.69f, 17.22f, 9.37f, 16.54f, 9.37f, 15.66f)
                    verticalLineTo(13.26f)
                    curveTo(9.37f, 12.38f, 9.69f, 11.7f, 10.27f, 11.37f)
                    curveTo(10.85f, 11.03f, 11.59f, 11.09f, 12.36f, 11.54f)
                    lineTo(14.44f, 12.74f)
                    curveTo(15.21f, 13.18f, 15.63f, 13.8f, 15.63f, 14.47f)
                    curveTo(15.63f, 15.14f, 15.2f, 15.73f, 14.44f, 16.18f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(7.77f, 2f)
                    curveTo(4.67f, 2.01f, 2.64f, 3.61f, 2.13f, 6.36f)
                    horizontalLineTo(7.77f)
                    verticalLineTo(2f)
                    close()
                }
            }
        }.build()

        return _VideoPlay!!
    }

@Suppress("ObjectPropertyName")
private var _VideoPlay: ImageVector? = null
