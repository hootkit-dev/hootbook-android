package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Setting2: ImageVector
    get() {
        if (_Setting2 != null) {
            return _Setting2!!
        }
        _Setting2 = ImageVector.Builder(
            name = "Setting2",
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
                    moveTo(20.1f, 9.219f)
                    curveTo(18.29f, 9.219f, 17.55f, 7.939f, 18.45f, 6.369f)
                    curveTo(18.97f, 5.459f, 18.66f, 4.299f, 17.75f, 3.779f)
                    lineTo(16.02f, 2.789f)
                    curveTo(15.23f, 2.319f, 14.21f, 2.599f, 13.74f, 3.389f)
                    lineTo(13.63f, 3.579f)
                    curveTo(12.73f, 5.149f, 11.25f, 5.149f, 10.34f, 3.579f)
                    lineTo(10.23f, 3.389f)
                    curveTo(9.78f, 2.599f, 8.76f, 2.319f, 7.97f, 2.789f)
                    lineTo(6.24f, 3.779f)
                    curveTo(5.33f, 4.299f, 5.02f, 5.469f, 5.54f, 6.379f)
                    curveTo(6.45f, 7.939f, 5.71f, 9.219f, 3.9f, 9.219f)
                    curveTo(2.86f, 9.219f, 2f, 10.069f, 2f, 11.119f)
                    verticalLineTo(12.879f)
                    curveTo(2f, 13.919f, 2.85f, 14.779f, 3.9f, 14.779f)
                    curveTo(5.71f, 14.779f, 6.45f, 16.059f, 5.54f, 17.629f)
                    curveTo(5.02f, 18.539f, 5.33f, 19.699f, 6.24f, 20.219f)
                    lineTo(7.97f, 21.209f)
                    curveTo(8.76f, 21.679f, 9.78f, 21.399f, 10.25f, 20.609f)
                    lineTo(10.36f, 20.419f)
                    curveTo(11.26f, 18.849f, 12.74f, 18.849f, 13.65f, 20.419f)
                    lineTo(13.76f, 20.609f)
                    curveTo(14.23f, 21.399f, 15.25f, 21.679f, 16.04f, 21.209f)
                    lineTo(17.77f, 20.219f)
                    curveTo(18.68f, 19.699f, 18.99f, 18.529f, 18.47f, 17.629f)
                    curveTo(17.56f, 16.059f, 18.3f, 14.779f, 20.11f, 14.779f)
                    curveTo(21.15f, 14.779f, 22.01f, 13.929f, 22.01f, 12.879f)
                    verticalLineTo(11.119f)
                    curveTo(22f, 10.079f, 21.15f, 9.219f, 20.1f, 9.219f)
                    close()
                    moveTo(12f, 15.249f)
                    curveTo(10.21f, 15.249f, 8.75f, 13.789f, 8.75f, 11.999f)
                    curveTo(8.75f, 10.209f, 10.21f, 8.749f, 12f, 8.749f)
                    curveTo(13.79f, 8.749f, 15.25f, 10.209f, 15.25f, 11.999f)
                    curveTo(15.25f, 13.789f, 13.79f, 15.249f, 12f, 15.249f)
                    close()
                }
            }
        }.build()

        return _Setting2!!
    }

@Suppress("ObjectPropertyName")
private var _Setting2: ImageVector? = null
