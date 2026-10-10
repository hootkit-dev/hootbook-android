package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Clock: ImageVector
    get() {
        if (_Clock != null) {
            return _Clock!!
        }
        _Clock = ImageVector.Builder(
            name = "Clock",
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
                    moveTo(12f, 2f)
                    curveTo(6.49f, 2f, 2f, 6.49f, 2f, 12f)
                    curveTo(2f, 17.51f, 6.49f, 22f, 12f, 22f)
                    curveTo(17.51f, 22f, 22f, 17.51f, 22f, 12f)
                    curveTo(22f, 6.49f, 17.51f, 2f, 12f, 2f)
                    close()
                    moveTo(16.35f, 15.57f)
                    curveTo(16.21f, 15.81f, 15.96f, 15.94f, 15.7f, 15.94f)
                    curveTo(15.57f, 15.94f, 15.44f, 15.91f, 15.32f, 15.83f)
                    lineTo(12.22f, 13.98f)
                    curveTo(11.45f, 13.52f, 10.88f, 12.51f, 10.88f, 11.62f)
                    verticalLineTo(7.52f)
                    curveTo(10.88f, 7.11f, 11.22f, 6.77f, 11.63f, 6.77f)
                    curveTo(12.04f, 6.77f, 12.38f, 7.11f, 12.38f, 7.52f)
                    verticalLineTo(11.62f)
                    curveTo(12.38f, 11.98f, 12.68f, 12.51f, 12.99f, 12.69f)
                    lineTo(16.09f, 14.54f)
                    curveTo(16.45f, 14.75f, 16.57f, 15.21f, 16.35f, 15.57f)
                    close()
                }
            }
        }.build()

        return _Clock!!
    }

@Suppress("ObjectPropertyName")
private var _Clock: ImageVector? = null
