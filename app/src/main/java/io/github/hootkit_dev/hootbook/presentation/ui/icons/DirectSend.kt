package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.DirectSend: ImageVector
    get() {
        if (_DirectSend != null) {
            return _DirectSend!!
        }
        _DirectSend = ImageVector.Builder(
            name = "DirectSend",
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
                    moveTo(21.3f, 12.231f)
                    horizontalLineTo(17.82f)
                    curveTo(16.84f, 12.231f, 15.97f, 12.771f, 15.53f, 13.651f)
                    lineTo(14.69f, 15.311f)
                    curveTo(14.49f, 15.71f, 14.09f, 15.96f, 13.65f, 15.96f)
                    horizontalLineTo(10.37f)
                    curveTo(10.06f, 15.96f, 9.62f, 15.891f, 9.33f, 15.311f)
                    lineTo(8.49f, 13.66f)
                    curveTo(8.05f, 12.79f, 7.17f, 12.241f, 6.2f, 12.241f)
                    horizontalLineTo(2.7f)
                    curveTo(2.31f, 12.241f, 2f, 12.55f, 2f, 12.941f)
                    verticalLineTo(16.201f)
                    curveTo(2f, 19.83f, 4.18f, 22f, 7.82f, 22f)
                    horizontalLineTo(16.2f)
                    curveTo(19.63f, 22f, 21.74f, 20.121f, 22f, 16.781f)
                    verticalLineTo(12.931f)
                    curveTo(22f, 12.55f, 21.69f, 12.231f, 21.3f, 12.231f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(12.75f, 3.81f)
                    lineTo(13.47f, 4.53f)
                    curveTo(13.62f, 4.68f, 13.81f, 4.75f, 14f, 4.75f)
                    curveTo(14.19f, 4.75f, 14.38f, 4.68f, 14.53f, 4.53f)
                    curveTo(14.82f, 4.24f, 14.82f, 3.76f, 14.53f, 3.47f)
                    lineTo(12.53f, 1.47f)
                    curveTo(12.52f, 1.46f, 12.51f, 1.46f, 12.51f, 1.45f)
                    curveTo(12.45f, 1.39f, 12.37f, 1.34f, 12.29f, 1.3f)
                    curveTo(12.28f, 1.3f, 12.28f, 1.3f, 12.27f, 1.29f)
                    curveTo(12.19f, 1.26f, 12.11f, 1.25f, 12.03f, 1.24f)
                    curveTo(12f, 1.24f, 11.98f, 1.24f, 11.95f, 1.24f)
                    curveTo(11.89f, 1.24f, 11.83f, 1.26f, 11.77f, 1.28f)
                    curveTo(11.74f, 1.29f, 11.72f, 1.3f, 11.7f, 1.31f)
                    curveTo(11.62f, 1.35f, 11.54f, 1.39f, 11.48f, 1.46f)
                    lineTo(9.48f, 3.46f)
                    curveTo(9.19f, 3.75f, 9.19f, 4.23f, 9.48f, 4.52f)
                    curveTo(9.77f, 4.81f, 10.25f, 4.81f, 10.54f, 4.52f)
                    lineTo(11.26f, 3.8f)
                    verticalLineTo(5f)
                    horizontalLineTo(12.76f)
                    verticalLineTo(3.81f)
                    horizontalLineTo(12.75f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(22f, 10.81f)
                    verticalLineTo(10.85f)
                    curveTo(21.78f, 10.77f, 21.54f, 10.73f, 21.3f, 10.73f)
                    horizontalLineTo(17.82f)
                    curveTo(16.27f, 10.73f, 14.88f, 11.59f, 14.19f, 12.97f)
                    lineTo(13.44f, 14.45f)
                    horizontalLineTo(10.58f)
                    lineTo(9.83f, 12.98f)
                    curveTo(9.14f, 11.59f, 7.75f, 10.73f, 6.2f, 10.73f)
                    horizontalLineTo(2.7f)
                    curveTo(2.46f, 10.73f, 2.22f, 10.77f, 2f, 10.85f)
                    verticalLineTo(10.81f)
                    curveTo(2f, 7.17f, 4.17f, 5f, 7.81f, 5f)
                    horizontalLineTo(11.25f)
                    verticalLineTo(9f)
                    curveTo(11.25f, 9.41f, 11.59f, 9.75f, 12f, 9.75f)
                    curveTo(12.41f, 9.75f, 12.75f, 9.41f, 12.75f, 9f)
                    verticalLineTo(5f)
                    horizontalLineTo(16.19f)
                    curveTo(19.83f, 5f, 22f, 7.17f, 22f, 10.81f)
                    close()
                }
            }
        }.build()

        return _DirectSend!!
    }

@Suppress("ObjectPropertyName")
private var _DirectSend: ImageVector? = null
