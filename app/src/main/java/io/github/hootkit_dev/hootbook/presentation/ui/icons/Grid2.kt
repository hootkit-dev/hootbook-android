package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Grid2: ImageVector
    get() {
        if (_Grid2 != null) {
            return _Grid2!!
        }
        _Grid2 = ImageVector.Builder(
            name = "Grid2",
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
                    moveTo(11.25f, 12.75f)
                    verticalLineTo(22f)
                    horizontalLineTo(7.81f)
                    curveTo(4.17f, 22f, 2f, 19.83f, 2f, 16.19f)
                    verticalLineTo(12.75f)
                    horizontalLineTo(11.25f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(22f, 7.81f)
                    verticalLineTo(11.25f)
                    horizontalLineTo(12.75f)
                    verticalLineTo(2f)
                    horizontalLineTo(16.19f)
                    curveTo(19.83f, 2f, 22f, 4.17f, 22f, 7.81f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(11.25f, 2f)
                    verticalLineTo(11.25f)
                    horizontalLineTo(2f)
                    verticalLineTo(7.81f)
                    curveTo(2f, 4.17f, 4.17f, 2f, 7.81f, 2f)
                    horizontalLineTo(11.25f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(22f, 12.75f)
                    verticalLineTo(16.19f)
                    curveTo(22f, 19.83f, 19.83f, 22f, 16.19f, 22f)
                    horizontalLineTo(12.75f)
                    verticalLineTo(12.75f)
                    horizontalLineTo(22f)
                    close()
                }
            }
        }.build()

        return _Grid2!!
    }

@Suppress("ObjectPropertyName")
private var _Grid2: ImageVector? = null
