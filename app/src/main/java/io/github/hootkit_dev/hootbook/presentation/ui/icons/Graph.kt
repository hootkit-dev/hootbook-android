package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Graph: ImageVector
    get() {
        if (_Graph != null) {
            return _Graph!!
        }
        _Graph = ImageVector.Builder(
            name = "Graph",
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
                    moveTo(21.67f, 6.949f)
                    curveTo(21.03f, 4.779f, 19.22f, 2.969f, 17.05f, 2.329f)
                    curveTo(15.4f, 1.849f, 14.26f, 1.889f, 13.47f, 2.479f)
                    curveTo(12.52f, 3.189f, 12.41f, 4.469f, 12.41f, 5.379f)
                    verticalLineTo(7.869f)
                    curveTo(12.41f, 10.329f, 13.53f, 11.579f, 15.73f, 11.579f)
                    horizontalLineTo(18.6f)
                    curveTo(19.5f, 11.579f, 20.79f, 11.469f, 21.5f, 10.519f)
                    curveTo(22.11f, 9.739f, 22.16f, 8.599f, 21.67f, 6.949f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(18.91f, 13.359f)
                    curveTo(18.65f, 13.059f, 18.27f, 12.889f, 17.88f, 12.889f)
                    horizontalLineTo(14.3f)
                    curveTo(12.54f, 12.889f, 11.11f, 11.459f, 11.11f, 9.699f)
                    verticalLineTo(6.119f)
                    curveTo(11.11f, 5.729f, 10.94f, 5.349f, 10.64f, 5.089f)
                    curveTo(10.35f, 4.829f, 9.95f, 4.709f, 9.57f, 4.759f)
                    curveTo(7.22f, 5.059f, 5.06f, 6.349f, 3.65f, 8.289f)
                    curveTo(2.23f, 10.239f, 1.71f, 12.619f, 2.16f, 14.999f)
                    curveTo(2.81f, 18.439f, 5.56f, 21.189f, 9.01f, 21.839f)
                    curveTo(9.56f, 21.949f, 10.11f, 21.999f, 10.66f, 21.999f)
                    curveTo(12.47f, 21.999f, 14.22f, 21.439f, 15.71f, 20.349f)
                    curveTo(17.65f, 18.939f, 18.94f, 16.779f, 19.24f, 14.429f)
                    curveTo(19.29f, 14.039f, 19.17f, 13.649f, 18.91f, 13.359f)
                    close()
                }
            }
        }.build()

        return _Graph!!
    }

@Suppress("ObjectPropertyName")
private var _Graph: ImageVector? = null
