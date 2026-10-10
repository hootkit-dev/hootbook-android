package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Danger: ImageVector
    get() {
        if (_Danger != null) {
            return _Danger!!
        }
        _Danger = ImageVector.Builder(
            name = "Danger",
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
                    moveTo(21.76f, 15.92f)
                    lineTo(15.36f, 4.4f)
                    curveTo(14.5f, 2.85f, 13.31f, 2f, 12f, 2f)
                    curveTo(10.69f, 2f, 9.5f, 2.85f, 8.64f, 4.4f)
                    lineTo(2.24f, 15.92f)
                    curveTo(1.43f, 17.39f, 1.34f, 18.8f, 1.99f, 19.91f)
                    curveTo(2.64f, 21.02f, 3.92f, 21.63f, 5.6f, 21.63f)
                    horizontalLineTo(18.4f)
                    curveTo(20.08f, 21.63f, 21.36f, 21.02f, 22.01f, 19.91f)
                    curveTo(22.66f, 18.8f, 22.57f, 17.38f, 21.76f, 15.92f)
                    close()
                    moveTo(11.25f, 9f)
                    curveTo(11.25f, 8.59f, 11.59f, 8.25f, 12f, 8.25f)
                    curveTo(12.41f, 8.25f, 12.75f, 8.59f, 12.75f, 9f)
                    verticalLineTo(14f)
                    curveTo(12.75f, 14.41f, 12.41f, 14.75f, 12f, 14.75f)
                    curveTo(11.59f, 14.75f, 11.25f, 14.41f, 11.25f, 14f)
                    verticalLineTo(9f)
                    close()
                    moveTo(12.71f, 17.71f)
                    curveTo(12.66f, 17.75f, 12.61f, 17.79f, 12.56f, 17.83f)
                    curveTo(12.5f, 17.87f, 12.44f, 17.9f, 12.38f, 17.92f)
                    curveTo(12.32f, 17.95f, 12.26f, 17.97f, 12.19f, 17.98f)
                    curveTo(12.13f, 17.99f, 12.06f, 18f, 12f, 18f)
                    curveTo(11.94f, 18f, 11.87f, 17.99f, 11.8f, 17.98f)
                    curveTo(11.74f, 17.97f, 11.68f, 17.95f, 11.62f, 17.92f)
                    curveTo(11.56f, 17.9f, 11.5f, 17.87f, 11.44f, 17.83f)
                    curveTo(11.39f, 17.79f, 11.34f, 17.75f, 11.29f, 17.71f)
                    curveTo(11.11f, 17.52f, 11f, 17.26f, 11f, 17f)
                    curveTo(11f, 16.74f, 11.11f, 16.48f, 11.29f, 16.29f)
                    curveTo(11.34f, 16.25f, 11.39f, 16.21f, 11.44f, 16.17f)
                    curveTo(11.5f, 16.13f, 11.56f, 16.1f, 11.62f, 16.08f)
                    curveTo(11.68f, 16.05f, 11.74f, 16.03f, 11.8f, 16.02f)
                    curveTo(11.93f, 15.99f, 12.07f, 15.99f, 12.19f, 16.02f)
                    curveTo(12.26f, 16.03f, 12.32f, 16.05f, 12.38f, 16.08f)
                    curveTo(12.44f, 16.1f, 12.5f, 16.13f, 12.56f, 16.17f)
                    curveTo(12.61f, 16.21f, 12.66f, 16.25f, 12.71f, 16.29f)
                    curveTo(12.89f, 16.48f, 13f, 16.74f, 13f, 17f)
                    curveTo(13f, 17.26f, 12.89f, 17.52f, 12.71f, 17.71f)
                    close()
                }
            }
        }.build()

        return _Danger!!
    }

@Suppress("ObjectPropertyName")
private var _Danger: ImageVector? = null
