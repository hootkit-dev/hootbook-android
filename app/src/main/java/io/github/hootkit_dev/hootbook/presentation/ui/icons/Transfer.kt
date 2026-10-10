package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Transfer: ImageVector
    get() {
        if (_Transfer != null) {
            return _Transfer!!
        }
        _Transfer = ImageVector.Builder(
            name = "Transfer",
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
                    moveTo(15f, 22.75f)
                    curveTo(14.73f, 22.75f, 14.48f, 22.6f, 14.35f, 22.37f)
                    curveTo(14.22f, 22.13f, 14.22f, 21.85f, 14.36f, 21.61f)
                    lineTo(15.41f, 19.86f)
                    curveTo(15.62f, 19.5f, 16.09f, 19.39f, 16.44f, 19.6f)
                    curveTo(16.8f, 19.81f, 16.91f, 20.27f, 16.7f, 20.63f)
                    lineTo(16.43f, 21.08f)
                    curveTo(19.19f, 20.43f, 21.26f, 17.95f, 21.26f, 14.99f)
                    curveTo(21.26f, 14.58f, 21.6f, 14.24f, 22.01f, 14.24f)
                    curveTo(22.42f, 14.24f, 22.76f, 14.58f, 22.76f, 14.99f)
                    curveTo(22.75f, 19.27f, 19.27f, 22.75f, 15f, 22.75f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(2f, 9.75f)
                    curveTo(1.59f, 9.75f, 1.25f, 9.41f, 1.25f, 9f)
                    curveTo(1.25f, 4.73f, 4.73f, 1.25f, 9f, 1.25f)
                    curveTo(9.27f, 1.25f, 9.52f, 1.4f, 9.65f, 1.63f)
                    curveTo(9.78f, 1.87f, 9.78f, 2.15f, 9.64f, 2.39f)
                    lineTo(8.59f, 4.14f)
                    curveTo(8.38f, 4.49f, 7.92f, 4.61f, 7.56f, 4.39f)
                    curveTo(7.21f, 4.18f, 7.09f, 3.72f, 7.31f, 3.36f)
                    lineTo(7.58f, 2.91f)
                    curveTo(4.81f, 3.56f, 2.75f, 6.04f, 2.75f, 9f)
                    curveTo(2.75f, 9.41f, 2.41f, 9.75f, 2f, 9.75f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(14.8f, 12.629f)
                    verticalLineTo(15.569f)
                    curveTo(14.8f, 18.019f, 13.82f, 18.999f, 11.37f, 18.999f)
                    horizontalLineTo(8.43f)
                    curveTo(5.98f, 18.999f, 5f, 18.019f, 5f, 15.569f)
                    verticalLineTo(12.629f)
                    curveTo(5f, 10.179f, 5.98f, 9.199f, 8.43f, 9.199f)
                    horizontalLineTo(11.37f)
                    curveTo(13.82f, 9.199f, 14.8f, 10.179f, 14.8f, 12.629f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(15.57f, 5f)
                    horizontalLineTo(12.63f)
                    curveTo(10.22f, 5f, 9.24f, 5.96f, 9.21f, 8.32f)
                    horizontalLineTo(11.37f)
                    curveTo(14.31f, 8.32f, 15.67f, 9.69f, 15.67f, 12.62f)
                    verticalLineTo(14.78f)
                    curveTo(18.04f, 14.75f, 18.99f, 13.77f, 18.99f, 11.36f)
                    verticalLineTo(8.43f)
                    curveTo(19f, 5.98f, 18.02f, 5f, 15.57f, 5f)
                    close()
                }
            }
        }.build()

        return _Transfer!!
    }

@Suppress("ObjectPropertyName")
private var _Transfer: ImageVector? = null
