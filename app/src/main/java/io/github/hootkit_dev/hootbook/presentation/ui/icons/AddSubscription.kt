package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.AddSubscription: ImageVector
    get() {
        if (_AddSubscription != null) {
            return _AddSubscription!!
        }
        _AddSubscription = ImageVector.Builder(
            name = "AddSubscription",
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
                    moveTo(21.85f, 10.25f)
                    curveTo(21.07f, 5.8f, 17.24f, 2.35f, 12.73f, 2.03f)
                    curveTo(6.63f, 1.59f, 1.59f, 6.64f, 2.03f, 12.73f)
                    curveTo(2.35f, 17.24f, 5.8f, 21.06f, 10.25f, 21.84f)
                    curveTo(11.4f, 22.04f, 12.52f, 22.04f, 13.59f, 21.86f)
                    curveTo(13.9f, 21.81f, 14.08f, 21.47f, 13.96f, 21.19f)
                    curveTo(13.66f, 20.51f, 13.5f, 19.76f, 13.5f, 18.99f)
                    curveTo(13.5f, 17.3f, 14.25f, 15.74f, 15.57f, 14.7f)
                    curveTo(16.54f, 13.92f, 17.76f, 13.49f, 19f, 13.49f)
                    curveTo(19.78f, 13.49f, 20.52f, 13.65f, 21.19f, 13.95f)
                    curveTo(21.48f, 14.08f, 21.81f, 13.89f, 21.87f, 13.58f)
                    curveTo(22.05f, 12.52f, 22.05f, 11.4f, 21.85f, 10.25f)
                    close()
                    moveTo(14.5f, 13.57f)
                    lineTo(13.3f, 14.26f)
                    lineTo(12.1f, 14.95f)
                    curveTo(10.61f, 15.81f, 9.39f, 15.11f, 9.39f, 13.38f)
                    verticalLineTo(12f)
                    verticalLineTo(10.61f)
                    curveTo(9.39f, 8.89f, 10.61f, 8.18f, 12.1f, 9.04f)
                    lineTo(13.3f, 9.73f)
                    lineTo(14.5f, 10.42f)
                    curveTo(15.99f, 11.3f, 15.99f, 12.7f, 14.5f, 13.57f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(19f, 15f)
                    curveTo(18.06f, 15f, 17.19f, 15.33f, 16.5f, 15.88f)
                    curveTo(15.58f, 16.61f, 15f, 17.74f, 15f, 19f)
                    curveTo(15f, 19.75f, 15.21f, 20.46f, 15.58f, 21.06f)
                    curveTo(16.27f, 22.22f, 17.54f, 23f, 19f, 23f)
                    curveTo(20.01f, 23f, 20.93f, 22.63f, 21.63f, 22f)
                    curveTo(21.94f, 21.74f, 22.21f, 21.42f, 22.42f, 21.06f)
                    curveTo(22.79f, 20.46f, 23f, 19.75f, 23f, 19f)
                    curveTo(23f, 16.79f, 21.21f, 15f, 19f, 15f)
                    close()
                    moveTo(20.5f, 19.73f)
                    horizontalLineTo(19.75f)
                    verticalLineTo(20.51f)
                    curveTo(19.75f, 20.92f, 19.41f, 21.26f, 19f, 21.26f)
                    curveTo(18.59f, 21.26f, 18.25f, 20.92f, 18.25f, 20.51f)
                    verticalLineTo(19.73f)
                    horizontalLineTo(17.5f)
                    curveTo(17.09f, 19.73f, 16.75f, 19.39f, 16.75f, 18.98f)
                    curveTo(16.75f, 18.57f, 17.09f, 18.23f, 17.5f, 18.23f)
                    horizontalLineTo(18.25f)
                    verticalLineTo(17.52f)
                    curveTo(18.25f, 17.11f, 18.59f, 16.77f, 19f, 16.77f)
                    curveTo(19.41f, 16.77f, 19.75f, 17.11f, 19.75f, 17.52f)
                    verticalLineTo(18.23f)
                    horizontalLineTo(20.5f)
                    curveTo(20.91f, 18.23f, 21.25f, 18.57f, 21.25f, 18.98f)
                    curveTo(21.25f, 19.39f, 20.91f, 19.73f, 20.5f, 19.73f)
                    close()
                }
            }
        }.build()

        return _AddSubscription!!
    }

@Suppress("ObjectPropertyName")
private var _AddSubscription: ImageVector? = null
