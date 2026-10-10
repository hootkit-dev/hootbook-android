package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.MessageProgramming: ImageVector
    get() {
        if (_MessageProgramming != null) {
            return _MessageProgramming!!
        }
        _MessageProgramming = ImageVector.Builder(
            name = "MessageProgramming",
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
                    moveTo(16f, 1.971f)
                    horizontalLineTo(8f)
                    curveTo(4f, 1.971f, 2f, 3.971f, 2f, 7.971f)
                    verticalLineTo(12.971f)
                    curveTo(2f, 17.971f, 4f, 18.971f, 8f, 18.971f)
                    horizontalLineTo(8.5f)
                    curveTo(8.78f, 18.971f, 9.14f, 19.151f, 9.3f, 19.371f)
                    lineTo(10.8f, 21.371f)
                    curveTo(11.46f, 22.251f, 12.54f, 22.251f, 13.2f, 21.371f)
                    lineTo(14.7f, 19.371f)
                    curveTo(14.89f, 19.121f, 15.19f, 18.971f, 15.5f, 18.971f)
                    horizontalLineTo(16f)
                    curveTo(20f, 18.971f, 22f, 16.971f, 22f, 12.971f)
                    verticalLineTo(7.971f)
                    curveTo(22f, 3.971f, 20f, 1.971f, 16f, 1.971f)
                    close()
                    moveTo(8.53f, 12.171f)
                    curveTo(8.82f, 12.461f, 8.82f, 12.941f, 8.53f, 13.231f)
                    curveTo(8.38f, 13.381f, 8.19f, 13.451f, 8f, 13.451f)
                    curveTo(7.81f, 13.451f, 7.62f, 13.381f, 7.47f, 13.231f)
                    lineTo(5.47f, 11.231f)
                    curveTo(5.18f, 10.941f, 5.18f, 10.461f, 5.47f, 10.171f)
                    lineTo(7.47f, 8.171f)
                    curveTo(7.76f, 7.881f, 8.24f, 7.881f, 8.53f, 8.171f)
                    curveTo(8.82f, 8.461f, 8.82f, 8.941f, 8.53f, 9.231f)
                    lineTo(7.06f, 10.701f)
                    lineTo(8.53f, 12.171f)
                    close()
                    moveTo(13.69f, 8.661f)
                    lineTo(11.69f, 13.331f)
                    curveTo(11.57f, 13.611f, 11.29f, 13.781f, 11f, 13.781f)
                    curveTo(10.9f, 13.781f, 10.8f, 13.761f, 10.7f, 13.721f)
                    curveTo(10.32f, 13.561f, 10.14f, 13.121f, 10.31f, 12.731f)
                    lineTo(12.31f, 8.061f)
                    curveTo(12.47f, 7.681f, 12.91f, 7.501f, 13.3f, 7.671f)
                    curveTo(13.68f, 7.841f, 13.85f, 8.281f, 13.69f, 8.661f)
                    close()
                    moveTo(18.53f, 11.231f)
                    lineTo(16.53f, 13.231f)
                    curveTo(16.38f, 13.381f, 16.19f, 13.451f, 16f, 13.451f)
                    curveTo(15.81f, 13.451f, 15.62f, 13.381f, 15.47f, 13.231f)
                    curveTo(15.18f, 12.941f, 15.18f, 12.461f, 15.47f, 12.171f)
                    lineTo(16.94f, 10.701f)
                    lineTo(15.47f, 9.231f)
                    curveTo(15.18f, 8.941f, 15.18f, 8.461f, 15.47f, 8.171f)
                    curveTo(15.76f, 7.881f, 16.24f, 7.881f, 16.53f, 8.171f)
                    lineTo(18.53f, 10.171f)
                    curveTo(18.82f, 10.461f, 18.82f, 10.941f, 18.53f, 11.231f)
                    close()
                }
            }
        }.build()

        return _MessageProgramming!!
    }

@Suppress("ObjectPropertyName")
private var _MessageProgramming: ImageVector? = null
