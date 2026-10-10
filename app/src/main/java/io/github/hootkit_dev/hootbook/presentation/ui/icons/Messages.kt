package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Messages: ImageVector
    get() {
        if (_Messages != null) {
            return _Messages!!
        }
        _Messages = ImageVector.Builder(
            name = "Messages",
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
                    moveTo(15.59f, 12.4f)
                    verticalLineTo(16.47f)
                    curveTo(15.59f, 16.83f, 15.55f, 17.17f, 15.46f, 17.48f)
                    curveTo(15.09f, 18.95f, 13.87f, 19.87f, 12.19f, 19.87f)
                    horizontalLineTo(9.47f)
                    lineTo(6.45f, 21.88f)
                    curveTo(6f, 22.19f, 5.4f, 21.86f, 5.4f, 21.32f)
                    verticalLineTo(19.87f)
                    curveTo(4.38f, 19.87f, 3.53f, 19.53f, 2.94f, 18.94f)
                    curveTo(2.34f, 18.34f, 2f, 17.49f, 2f, 16.47f)
                    verticalLineTo(12.4f)
                    curveTo(2f, 10.5f, 3.18f, 9.19f, 5f, 9.02f)
                    curveTo(5.13f, 9.01f, 5.26f, 9f, 5.4f, 9f)
                    horizontalLineTo(12.19f)
                    curveTo(14.23f, 9f, 15.59f, 10.36f, 15.59f, 12.4f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(17.75f, 15.6f)
                    curveTo(19.02f, 15.6f, 20.09f, 15.18f, 20.83f, 14.43f)
                    curveTo(21.58f, 13.69f, 22f, 12.62f, 22f, 11.35f)
                    verticalLineTo(6.25f)
                    curveTo(22f, 3.9f, 20.1f, 2f, 17.75f, 2f)
                    horizontalLineTo(9.25f)
                    curveTo(6.9f, 2f, 5f, 3.9f, 5f, 6.25f)
                    verticalLineTo(7f)
                    curveTo(5f, 7.28f, 5.22f, 7.5f, 5.5f, 7.5f)
                    horizontalLineTo(12.19f)
                    curveTo(14.9f, 7.5f, 17.09f, 9.69f, 17.09f, 12.4f)
                    verticalLineTo(15.1f)
                    curveTo(17.09f, 15.38f, 17.31f, 15.6f, 17.59f, 15.6f)
                    horizontalLineTo(17.75f)
                    close()
                }
            }
        }.build()

        return _Messages!!
    }

@Suppress("ObjectPropertyName")
private var _Messages: ImageVector? = null
