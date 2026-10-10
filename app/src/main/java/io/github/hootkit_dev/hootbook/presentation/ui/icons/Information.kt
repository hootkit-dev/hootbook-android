package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Information: ImageVector
    get() {
        if (_Information != null) {
            return _Information!!
        }
        _Information = ImageVector.Builder(
            name = "Information",
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
                    moveTo(21.56f, 10.74f)
                    lineTo(20.21f, 9.16f)
                    curveTo(19.96f, 8.86f, 19.75f, 8.3f, 19.75f, 7.9f)
                    verticalLineTo(6.2f)
                    curveTo(19.75f, 5.14f, 18.88f, 4.27f, 17.82f, 4.27f)
                    horizontalLineTo(16.12f)
                    curveTo(15.72f, 4.27f, 15.15f, 4.06f, 14.85f, 3.81f)
                    lineTo(13.27f, 2.46f)
                    curveTo(12.58f, 1.87f, 11.45f, 1.87f, 10.76f, 2.46f)
                    lineTo(9.16f, 3.81f)
                    curveTo(8.86f, 4.06f, 8.3f, 4.27f, 7.9f, 4.27f)
                    horizontalLineTo(6.17f)
                    curveTo(5.11f, 4.27f, 4.24f, 5.14f, 4.24f, 6.2f)
                    verticalLineTo(7.9f)
                    curveTo(4.24f, 8.29f, 4.04f, 8.85f, 3.79f, 9.15f)
                    lineTo(2.44f, 10.74f)
                    curveTo(1.86f, 11.44f, 1.86f, 12.56f, 2.44f, 13.24f)
                    lineTo(3.79f, 14.83f)
                    curveTo(4.04f, 15.12f, 4.24f, 15.69f, 4.24f, 16.08f)
                    verticalLineTo(17.79f)
                    curveTo(4.24f, 18.85f, 5.11f, 19.72f, 6.17f, 19.72f)
                    horizontalLineTo(7.91f)
                    curveTo(8.3f, 19.72f, 8.87f, 19.93f, 9.17f, 20.18f)
                    lineTo(10.75f, 21.53f)
                    curveTo(11.44f, 22.12f, 12.57f, 22.12f, 13.26f, 21.53f)
                    lineTo(14.84f, 20.18f)
                    curveTo(15.14f, 19.93f, 15.7f, 19.72f, 16.1f, 19.72f)
                    horizontalLineTo(17.8f)
                    curveTo(18.86f, 19.72f, 19.73f, 18.85f, 19.73f, 17.79f)
                    verticalLineTo(16.09f)
                    curveTo(19.73f, 15.69f, 19.94f, 15.13f, 20.19f, 14.83f)
                    lineTo(21.54f, 13.25f)
                    curveTo(22.15f, 12.57f, 22.15f, 11.44f, 21.56f, 10.74f)
                    close()
                    moveTo(11.25f, 8.13f)
                    curveTo(11.25f, 7.72f, 11.59f, 7.38f, 12f, 7.38f)
                    curveTo(12.41f, 7.38f, 12.75f, 7.72f, 12.75f, 8.13f)
                    verticalLineTo(12.96f)
                    curveTo(12.75f, 13.37f, 12.41f, 13.71f, 12f, 13.71f)
                    curveTo(11.59f, 13.71f, 11.25f, 13.37f, 11.25f, 12.96f)
                    verticalLineTo(8.13f)
                    close()
                    moveTo(12f, 16.87f)
                    curveTo(11.45f, 16.87f, 11f, 16.42f, 11f, 15.87f)
                    curveTo(11f, 15.32f, 11.44f, 14.87f, 12f, 14.87f)
                    curveTo(12.55f, 14.87f, 13f, 15.32f, 13f, 15.87f)
                    curveTo(13f, 16.42f, 12.56f, 16.87f, 12f, 16.87f)
                    close()
                }
            }
        }.build()

        return _Information!!
    }

@Suppress("ObjectPropertyName")
private var _Information: ImageVector? = null
