package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.NotificationBing: ImageVector
    get() {
        if (_NotificationBing != null) {
            return _NotificationBing!!
        }
        _NotificationBing = ImageVector.Builder(
            name = "NotificationBing",
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
                    moveTo(20.19f, 14.061f)
                    lineTo(19.06f, 12.181f)
                    curveTo(18.81f, 11.771f, 18.59f, 10.981f, 18.59f, 10.501f)
                    verticalLineTo(8.631f)
                    curveTo(18.59f, 5.001f, 15.64f, 2.051f, 12.02f, 2.051f)
                    curveTo(8.39f, 2.061f, 5.44f, 5.001f, 5.44f, 8.631f)
                    verticalLineTo(10.491f)
                    curveTo(5.44f, 10.971f, 5.22f, 11.761f, 4.98f, 12.171f)
                    lineTo(3.85f, 14.051f)
                    curveTo(3.42f, 14.781f, 3.32f, 15.611f, 3.59f, 16.331f)
                    curveTo(3.86f, 17.061f, 4.47f, 17.641f, 5.27f, 17.901f)
                    curveTo(6.35f, 18.261f, 7.44f, 18.521f, 8.55f, 18.711f)
                    curveTo(8.66f, 18.731f, 8.77f, 18.741f, 8.88f, 18.761f)
                    curveTo(9.02f, 18.781f, 9.17f, 18.801f, 9.32f, 18.821f)
                    curveTo(9.58f, 18.861f, 9.84f, 18.891f, 10.11f, 18.911f)
                    curveTo(10.74f, 18.971f, 11.38f, 19.001f, 12.02f, 19.001f)
                    curveTo(12.65f, 19.001f, 13.28f, 18.971f, 13.9f, 18.911f)
                    curveTo(14.13f, 18.891f, 14.36f, 18.871f, 14.58f, 18.841f)
                    curveTo(14.76f, 18.821f, 14.94f, 18.801f, 15.12f, 18.771f)
                    curveTo(15.23f, 18.761f, 15.34f, 18.741f, 15.45f, 18.721f)
                    curveTo(16.57f, 18.541f, 17.68f, 18.261f, 18.76f, 17.901f)
                    curveTo(19.53f, 17.641f, 20.12f, 17.061f, 20.4f, 16.321f)
                    curveTo(20.68f, 15.571f, 20.6f, 14.751f, 20.19f, 14.061f)
                    close()
                    moveTo(12.75f, 10.001f)
                    curveTo(12.75f, 10.421f, 12.41f, 10.761f, 11.99f, 10.761f)
                    curveTo(11.57f, 10.761f, 11.23f, 10.421f, 11.23f, 10.001f)
                    verticalLineTo(6.901f)
                    curveTo(11.23f, 6.481f, 11.57f, 6.141f, 11.99f, 6.141f)
                    curveTo(12.41f, 6.141f, 12.75f, 6.481f, 12.75f, 6.901f)
                    verticalLineTo(10.001f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(14.83f, 20.01f)
                    curveTo(14.41f, 21.17f, 13.3f, 22f, 12f, 22f)
                    curveTo(11.21f, 22f, 10.43f, 21.68f, 9.88f, 21.11f)
                    curveTo(9.56f, 20.81f, 9.32f, 20.41f, 9.18f, 20f)
                    curveTo(9.31f, 20.02f, 9.44f, 20.03f, 9.58f, 20.05f)
                    curveTo(9.81f, 20.08f, 10.05f, 20.11f, 10.29f, 20.13f)
                    curveTo(10.86f, 20.18f, 11.44f, 20.21f, 12.02f, 20.21f)
                    curveTo(12.59f, 20.21f, 13.16f, 20.18f, 13.72f, 20.13f)
                    curveTo(13.93f, 20.11f, 14.14f, 20.1f, 14.34f, 20.07f)
                    curveTo(14.5f, 20.05f, 14.66f, 20.03f, 14.83f, 20.01f)
                    close()
                }
            }
        }.build()

        return _NotificationBing!!
    }

@Suppress("ObjectPropertyName")
private var _NotificationBing: ImageVector? = null
