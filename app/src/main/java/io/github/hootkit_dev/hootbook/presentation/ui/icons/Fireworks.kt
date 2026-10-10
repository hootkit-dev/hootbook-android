package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Fireworks: ImageVector
    get() {
        if (_Fireworks != null) {
            return _Fireworks!!
        }
        _Fireworks = ImageVector.Builder(
            name = "Fireworks",
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
                    moveTo(12f, 22.75f)
                    curveTo(11.59f, 22.75f, 11.25f, 22.41f, 11.25f, 22f)
                    verticalLineTo(20.89f)
                    curveTo(11.25f, 20.48f, 11.59f, 20.14f, 12f, 20.14f)
                    curveTo(12.41f, 20.14f, 12.75f, 20.48f, 12.75f, 20.89f)
                    verticalLineTo(22f)
                    curveTo(12.75f, 22.41f, 12.41f, 22.75f, 12f, 22.75f)
                    close()
                    moveTo(19.11f, 19.86f)
                    curveTo(18.92f, 19.86f, 18.73f, 19.79f, 18.58f, 19.64f)
                    lineTo(15.91f, 16.97f)
                    curveTo(15.62f, 16.68f, 15.62f, 16.2f, 15.91f, 15.91f)
                    curveTo(16.2f, 15.62f, 16.68f, 15.62f, 16.97f, 15.91f)
                    lineTo(19.64f, 18.58f)
                    curveTo(19.93f, 18.87f, 19.93f, 19.35f, 19.64f, 19.64f)
                    curveTo(19.49f, 19.79f, 19.3f, 19.86f, 19.11f, 19.86f)
                    close()
                    moveTo(4.89f, 19.86f)
                    curveTo(4.7f, 19.86f, 4.51f, 19.79f, 4.36f, 19.64f)
                    curveTo(4.07f, 19.35f, 4.07f, 18.87f, 4.36f, 18.58f)
                    lineTo(7.03f, 15.91f)
                    curveTo(7.32f, 15.62f, 7.8f, 15.62f, 8.09f, 15.91f)
                    curveTo(8.38f, 16.2f, 8.38f, 16.68f, 8.09f, 16.97f)
                    lineTo(5.42f, 19.64f)
                    curveTo(5.27f, 19.79f, 5.08f, 19.86f, 4.89f, 19.86f)
                    close()
                    moveTo(22f, 12.75f)
                    horizontalLineTo(20.89f)
                    curveTo(20.48f, 12.75f, 20.14f, 12.41f, 20.14f, 12f)
                    curveTo(20.14f, 11.59f, 20.48f, 11.25f, 20.89f, 11.25f)
                    horizontalLineTo(22f)
                    curveTo(22.41f, 11.25f, 22.75f, 11.59f, 22.75f, 12f)
                    curveTo(22.75f, 12.41f, 22.41f, 12.75f, 22f, 12.75f)
                    close()
                    moveTo(3.11f, 12.75f)
                    horizontalLineTo(2f)
                    curveTo(1.59f, 12.75f, 1.25f, 12.41f, 1.25f, 12f)
                    curveTo(1.25f, 11.59f, 1.59f, 11.25f, 2f, 11.25f)
                    horizontalLineTo(3.11f)
                    curveTo(3.52f, 11.25f, 3.86f, 11.59f, 3.86f, 12f)
                    curveTo(3.86f, 12.41f, 3.52f, 12.75f, 3.11f, 12.75f)
                    close()
                    moveTo(16.44f, 8.31f)
                    curveTo(16.25f, 8.31f, 16.06f, 8.24f, 15.91f, 8.09f)
                    curveTo(15.62f, 7.8f, 15.62f, 7.32f, 15.91f, 7.03f)
                    lineTo(18.58f, 4.36f)
                    curveTo(18.87f, 4.07f, 19.35f, 4.07f, 19.64f, 4.36f)
                    curveTo(19.93f, 4.65f, 19.93f, 5.13f, 19.64f, 5.42f)
                    lineTo(16.97f, 8.09f)
                    curveTo(16.82f, 8.24f, 16.63f, 8.31f, 16.44f, 8.31f)
                    close()
                    moveTo(7.56f, 8.31f)
                    curveTo(7.37f, 8.31f, 7.18f, 8.24f, 7.03f, 8.09f)
                    lineTo(4.36f, 5.42f)
                    curveTo(4.07f, 5.13f, 4.07f, 4.65f, 4.36f, 4.36f)
                    curveTo(4.65f, 4.07f, 5.13f, 4.07f, 5.42f, 4.36f)
                    lineTo(8.09f, 7.03f)
                    curveTo(8.38f, 7.32f, 8.38f, 7.8f, 8.09f, 8.09f)
                    curveTo(7.94f, 8.24f, 7.75f, 8.31f, 7.56f, 8.31f)
                    close()
                    moveTo(12f, 3.86f)
                    curveTo(11.59f, 3.86f, 11.25f, 3.52f, 11.25f, 3.11f)
                    verticalLineTo(2f)
                    curveTo(11.25f, 1.59f, 11.59f, 1.25f, 12f, 1.25f)
                    curveTo(12.41f, 1.25f, 12.75f, 1.59f, 12.75f, 2f)
                    verticalLineTo(3.11f)
                    curveTo(12.75f, 3.52f, 12.41f, 3.86f, 12f, 3.86f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(16.971f, 12.28f)
                    curveTo(14.691f, 12.94f, 12.931f, 14.7f, 12.271f, 16.98f)
                    curveTo(12.191f, 17.27f, 11.8f, 17.27f, 11.71f, 16.98f)
                    curveTo(11.05f, 14.7f, 9.29f, 12.94f, 7.01f, 12.28f)
                    curveTo(6.72f, 12.2f, 6.72f, 11.81f, 7.01f, 11.72f)
                    curveTo(9.29f, 11.06f, 11.05f, 9.3f, 11.71f, 7.02f)
                    curveTo(11.79f, 6.73f, 12.181f, 6.73f, 12.271f, 7.02f)
                    curveTo(12.931f, 9.3f, 14.691f, 11.06f, 16.971f, 11.72f)
                    curveTo(17.26f, 11.8f, 17.26f, 12.19f, 16.971f, 12.28f)
                    close()
                }
            }
        }.build()

        return _Fireworks!!
    }

@Suppress("ObjectPropertyName")
private var _Fireworks: ImageVector? = null
