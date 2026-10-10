package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Path: ImageVector
    get() {
        if (_Path != null) {
            return _Path!!
        }
        _Path = ImageVector.Builder(
            name = "Path",
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
                    moveTo(20.02f, 10.7f)
                    lineTo(17.9f, 12.04f)
                    curveTo(17.5f, 12.29f, 16.99f, 12.23f, 16.66f, 11.9f)
                    lineTo(12.13f, 7.37f)
                    curveTo(11.8f, 7.04f, 11.74f, 6.53f, 11.99f, 6.13f)
                    lineTo(13.33f, 4.01f)
                    curveTo(14.15f, 2.72f, 15.79f, 2.66f, 17f, 3.86f)
                    lineTo(20.18f, 7.04f)
                    curveTo(21.3f, 8.17f, 21.23f, 9.93f, 20.02f, 10.7f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(14.71f, 19.95f)
                    lineTo(5.99f, 20.97f)
                    curveTo(5.912f, 20.98f, 5.836f, 20.987f, 5.76f, 20.992f)
                    curveTo(5.075f, 21.032f, 4.905f, 20.225f, 5.39f, 19.739f)
                    lineTo(7.98f, 17.15f)
                    curveTo(8.3f, 16.84f, 8.3f, 16.35f, 7.98f, 16.04f)
                    curveTo(7.68f, 15.73f, 7.19f, 15.73f, 6.87f, 16.04f)
                    lineTo(4.28f, 18.629f)
                    curveTo(3.795f, 19.115f, 2.995f, 18.944f, 3.031f, 18.259f)
                    curveTo(3.035f, 18.187f, 3.041f, 18.114f, 3.05f, 18.04f)
                    lineTo(4.08f, 9.32f)
                    curveTo(4.34f, 7.14f, 5.14f, 6.42f, 7.44f, 6.56f)
                    lineTo(8.94f, 6.65f)
                    curveTo(9.43f, 6.68f, 9.89f, 6.89f, 10.24f, 7.24f)
                    lineTo(16.79f, 13.79f)
                    curveTo(17.14f, 14.14f, 17.35f, 14.6f, 17.37f, 15.09f)
                    lineTo(17.46f, 16.59f)
                    curveTo(17.69f, 18.9f, 16.9f, 19.7f, 14.71f, 19.95f)
                    close()
                }
            }
        }.build()

        return _Path!!
    }

@Suppress("ObjectPropertyName")
private var _Path: ImageVector? = null
