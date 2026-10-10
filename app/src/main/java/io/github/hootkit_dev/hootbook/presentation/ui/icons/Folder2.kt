package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Folder2: ImageVector
    get() {
        if (_Folder2 != null) {
            return _Folder2!!
        }
        _Folder2 = ImageVector.Builder(
            name = "Folder2",
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
                    moveTo(19.435f, 4.034f)
                    curveTo(19.567f, 4.242f, 19.34f, 4.485f, 19.1f, 4.43f)
                    curveTo(18.63f, 4.29f, 18.11f, 4.22f, 17.58f, 4.22f)
                    horizontalLineTo(14.28f)
                    curveTo(14.123f, 4.22f, 13.974f, 4.146f, 13.88f, 4.02f)
                    lineTo(12.73f, 2.49f)
                    curveTo(12.589f, 2.29f, 12.722f, 2f, 12.967f, 2f)
                    horizontalLineTo(15.72f)
                    curveTo(17.281f, 2f, 18.656f, 2.811f, 19.435f, 4.034f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(20.14f, 6.54f)
                    curveTo(19.71f, 6.23f, 19.22f, 6f, 18.69f, 5.87f)
                    curveTo(18.33f, 5.77f, 17.96f, 5.72f, 17.58f, 5.72f)
                    horizontalLineTo(13.86f)
                    curveTo(13.28f, 5.72f, 13.24f, 5.67f, 12.93f, 5.26f)
                    lineTo(11.53f, 3.4f)
                    curveTo(10.88f, 2.53f, 10.37f, 2f, 8.74f, 2f)
                    horizontalLineTo(6.42f)
                    curveTo(3.98f, 2f, 2f, 3.98f, 2f, 6.42f)
                    verticalLineTo(17.58f)
                    curveTo(2f, 20.02f, 3.98f, 22f, 6.42f, 22f)
                    horizontalLineTo(17.58f)
                    curveTo(20.02f, 22f, 22f, 20.02f, 22f, 17.58f)
                    verticalLineTo(10.14f)
                    curveTo(22f, 8.65f, 21.27f, 7.34f, 20.14f, 6.54f)
                    close()
                    moveTo(14.39f, 16.34f)
                    horizontalLineTo(9.6f)
                    curveTo(9.21f, 16.34f, 8.91f, 16.03f, 8.91f, 15.64f)
                    curveTo(8.91f, 15.26f, 9.21f, 14.94f, 9.6f, 14.94f)
                    horizontalLineTo(14.39f)
                    curveTo(14.78f, 14.94f, 15.09f, 15.26f, 15.09f, 15.64f)
                    curveTo(15.09f, 16.03f, 14.78f, 16.34f, 14.39f, 16.34f)
                    close()
                }
            }
        }.build()

        return _Folder2!!
    }

@Suppress("ObjectPropertyName")
private var _Folder2: ImageVector? = null
