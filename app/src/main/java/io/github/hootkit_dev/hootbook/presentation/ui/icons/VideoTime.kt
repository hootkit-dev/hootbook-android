package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.VideoTime: ImageVector
    get() {
        if (_VideoTime != null) {
            return _VideoTime!!
        }
        _VideoTime = ImageVector.Builder(
            name = "VideoTime",
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
                    moveTo(21.98f, 15.65f)
                    curveTo(21.16f, 14.64f, 19.91f, 14f, 18.5f, 14f)
                    curveTo(17.44f, 14f, 16.46f, 14.37f, 15.69f, 14.99f)
                    curveTo(14.65f, 15.81f, 14f, 17.08f, 14f, 18.5f)
                    curveTo(14f, 19.91f, 14.64f, 21.16f, 15.65f, 21.98f)
                    curveTo(16.42f, 22.62f, 17.42f, 23f, 18.5f, 23f)
                    curveTo(19.64f, 23f, 20.67f, 22.57f, 21.47f, 21.88f)
                    curveTo(22.4f, 21.05f, 23f, 19.85f, 23f, 18.5f)
                    curveTo(23f, 17.42f, 22.62f, 16.42f, 21.98f, 15.65f)
                    close()
                    moveTo(19.53f, 18.78f)
                    curveTo(19.53f, 19.04f, 19.39f, 19.29f, 19.17f, 19.42f)
                    lineTo(17.76f, 20.26f)
                    curveTo(17.64f, 20.33f, 17.51f, 20.37f, 17.37f, 20.37f)
                    curveTo(17.12f, 20.37f, 16.87f, 20.24f, 16.73f, 20.01f)
                    curveTo(16.52f, 19.65f, 16.63f, 19.19f, 16.99f, 18.98f)
                    lineTo(18.03f, 18.36f)
                    verticalLineTo(17.1f)
                    curveTo(18.03f, 16.69f, 18.37f, 16.35f, 18.78f, 16.35f)
                    curveTo(19.19f, 16.35f, 19.53f, 16.69f, 19.53f, 17.1f)
                    verticalLineTo(18.78f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(14.73f, 5.86f)
                    verticalLineTo(2.5f)
                    curveTo(14.73f, 2.22f, 14.51f, 2f, 14.23f, 2f)
                    horizontalLineTo(9.77f)
                    curveTo(9.49f, 2f, 9.27f, 2.22f, 9.27f, 2.5f)
                    verticalLineTo(5.86f)
                    curveTo(9.27f, 6.14f, 9.49f, 6.36f, 9.77f, 6.36f)
                    horizontalLineTo(14.23f)
                    curveTo(14.51f, 6.36f, 14.73f, 6.14f, 14.73f, 5.86f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(7.25f, 2.02f)
                    curveTo(4.69f, 2.18f, 2.94f, 3.5f, 2.29f, 5.7f)
                    curveTo(2.19f, 6.03f, 2.43f, 6.36f, 2.77f, 6.36f)
                    horizontalLineTo(7.27f)
                    curveTo(7.55f, 6.36f, 7.77f, 6.14f, 7.77f, 5.86f)
                    verticalLineTo(2.52f)
                    curveTo(7.77f, 2.24f, 7.53f, 2f, 7.25f, 2.02f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(16.75f, 2.01f)
                    curveTo(19.31f, 2.17f, 21.06f, 3.49f, 21.71f, 5.69f)
                    curveTo(21.81f, 6.02f, 21.57f, 6.35f, 21.23f, 6.35f)
                    horizontalLineTo(16.73f)
                    curveTo(16.45f, 6.35f, 16.23f, 6.13f, 16.23f, 5.85f)
                    verticalLineTo(2.51f)
                    curveTo(16.23f, 2.23f, 16.47f, 1.99f, 16.75f, 2.01f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(22f, 8.36f)
                    verticalLineTo(12.74f)
                    curveTo(22f, 13.11f, 21.61f, 13.35f, 21.28f, 13.18f)
                    curveTo(20.44f, 12.74f, 19.48f, 12.5f, 18.5f, 12.5f)
                    curveTo(16.89f, 12.5f, 15.32f, 13.16f, 14.2f, 14.31f)
                    curveTo(13.1f, 15.43f, 12.5f, 16.92f, 12.5f, 18.5f)
                    curveTo(12.5f, 19.31f, 12.82f, 20.35f, 13.22f, 21.22f)
                    curveTo(13.38f, 21.57f, 13.14f, 22f, 12.75f, 22f)
                    horizontalLineTo(7.81f)
                    curveTo(4.6f, 22f, 2f, 19.4f, 2f, 16.19f)
                    verticalLineTo(8.36f)
                    curveTo(2f, 8.08f, 2.22f, 7.86f, 2.5f, 7.86f)
                    horizontalLineTo(21.5f)
                    curveTo(21.78f, 7.86f, 22f, 8.08f, 22f, 8.36f)
                    close()
                }
            }
        }.build()

        return _VideoTime!!
    }

@Suppress("ObjectPropertyName")
private var _VideoTime: ImageVector? = null
