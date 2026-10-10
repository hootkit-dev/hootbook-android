package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Send: ImageVector
    get() {
        if (_Send != null) {
            return _Send!!
        }
        _Send = ImageVector.Builder(
            name = "Send",
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
                    moveTo(18.07f, 8.511f)
                    lineTo(9.51f, 4.231f)
                    curveTo(3.76f, 1.351f, 1.4f, 3.711f, 4.28f, 9.461f)
                    lineTo(5.15f, 11.201f)
                    curveTo(5.4f, 11.711f, 5.4f, 12.301f, 5.15f, 12.811f)
                    lineTo(4.28f, 14.541f)
                    curveTo(1.4f, 20.291f, 3.75f, 22.651f, 9.51f, 19.771f)
                    lineTo(18.07f, 15.491f)
                    curveTo(21.91f, 13.571f, 21.91f, 10.431f, 18.07f, 8.511f)
                    close()
                    moveTo(14.84f, 12.751f)
                    horizontalLineTo(9.44f)
                    curveTo(9.03f, 12.751f, 8.69f, 12.411f, 8.69f, 12.001f)
                    curveTo(8.69f, 11.591f, 9.03f, 11.251f, 9.44f, 11.251f)
                    horizontalLineTo(14.84f)
                    curveTo(15.25f, 11.251f, 15.59f, 11.591f, 15.59f, 12.001f)
                    curveTo(15.59f, 12.411f, 15.25f, 12.751f, 14.84f, 12.751f)
                    close()
                }
            }
        }.build()

        return _Send!!
    }

@Suppress("ObjectPropertyName")
private var _Send: ImageVector? = null
