package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Status: ImageVector
    get() {
        if (_Status != null) {
            return _Status!!
        }
        _Status = ImageVector.Builder(
            name = "Status",
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
                    moveTo(9f, 14.221f)
                    horizontalLineTo(3.92f)
                    curveTo(3.31f, 14.221f, 2.75f, 14.531f, 2.43f, 15.051f)
                    curveTo(2.11f, 15.561f, 2.08f, 16.171f, 2.34f, 16.711f)
                    curveTo(3.57f, 19.231f, 5.79f, 21.211f, 8.43f, 22.141f)
                    curveTo(8.61f, 22.201f, 8.81f, 22.241f, 9f, 22.241f)
                    curveTo(9.35f, 22.241f, 9.7f, 22.131f, 10f, 21.921f)
                    curveTo(10.47f, 21.591f, 10.75f, 21.051f, 10.75f, 20.481f)
                    lineTo(10.76f, 15.981f)
                    curveTo(10.76f, 15.511f, 10.58f, 15.071f, 10.25f, 14.741f)
                    curveTo(9.91f, 14.411f, 9.47f, 14.221f, 9f, 14.221f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(22.48f, 9.6f)
                    curveTo(21.36f, 4.68f, 17.05f, 1.25f, 12f, 1.25f)
                    curveTo(6.95f, 1.25f, 2.64f, 4.68f, 1.52f, 9.6f)
                    curveTo(1.4f, 10.12f, 1.52f, 10.65f, 1.86f, 11.07f)
                    curveTo(2.2f, 11.49f, 2.7f, 11.73f, 3.24f, 11.73f)
                    horizontalLineTo(20.77f)
                    curveTo(21.31f, 11.73f, 21.81f, 11.49f, 22.15f, 11.07f)
                    curveTo(22.48f, 10.65f, 22.6f, 10.11f, 22.48f, 9.6f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(20.06f, 14.27f)
                    lineTo(15f, 14.26f)
                    curveTo(14.53f, 14.26f, 14.09f, 14.44f, 13.76f, 14.77f)
                    curveTo(13.43f, 15.1f, 13.25f, 15.54f, 13.25f, 16.01f)
                    lineTo(13.26f, 20.49f)
                    curveTo(13.26f, 21.06f, 13.54f, 21.6f, 14.01f, 21.93f)
                    curveTo(14.31f, 22.14f, 14.66f, 22.25f, 15.01f, 22.25f)
                    curveTo(15.2f, 22.25f, 15.39f, 22.22f, 15.57f, 22.15f)
                    curveTo(18.19f, 21.23f, 20.41f, 19.26f, 21.64f, 16.77f)
                    curveTo(21.9f, 16.24f, 21.87f, 15.62f, 21.56f, 15.12f)
                    curveTo(21.23f, 14.58f, 20.67f, 14.27f, 20.06f, 14.27f)
                    close()
                }
            }
        }.build()

        return _Status!!
    }

@Suppress("ObjectPropertyName")
private var _Status: ImageVector? = null
