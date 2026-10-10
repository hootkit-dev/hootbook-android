package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.GalleryExport: ImageVector
    get() {
        if (_GalleryExport != null) {
            return _GalleryExport!!
        }
        _GalleryExport = ImageVector.Builder(
            name = "GalleryExport",
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
                    moveTo(20.97f, 1f)
                    horizontalLineTo(18.03f)
                    curveTo(16.76f, 1f, 16f, 1.76f, 16f, 3.03f)
                    verticalLineTo(5.97f)
                    curveTo(16f, 7.24f, 16.76f, 8f, 18.03f, 8f)
                    horizontalLineTo(20.97f)
                    curveTo(22.24f, 8f, 23f, 7.24f, 23f, 5.97f)
                    verticalLineTo(3.03f)
                    curveTo(23f, 1.76f, 22.24f, 1f, 20.97f, 1f)
                    close()
                    moveTo(21.19f, 4.31f)
                    curveTo(21.07f, 4.43f, 20.91f, 4.49f, 20.75f, 4.49f)
                    curveTo(20.59f, 4.49f, 20.43f, 4.43f, 20.31f, 4.31f)
                    lineTo(20.13f, 4.13f)
                    verticalLineTo(6.37f)
                    curveTo(20.13f, 6.72f, 19.85f, 7f, 19.5f, 7f)
                    curveTo(19.15f, 7f, 18.87f, 6.72f, 18.87f, 6.37f)
                    verticalLineTo(4.13f)
                    lineTo(18.69f, 4.31f)
                    curveTo(18.45f, 4.55f, 18.05f, 4.55f, 17.81f, 4.31f)
                    curveTo(17.57f, 4.07f, 17.57f, 3.67f, 17.81f, 3.43f)
                    lineTo(19.06f, 2.18f)
                    curveTo(19.11f, 2.13f, 19.18f, 2.09f, 19.25f, 2.06f)
                    curveTo(19.27f, 2.05f, 19.29f, 2.05f, 19.31f, 2.04f)
                    curveTo(19.36f, 2.02f, 19.41f, 2.01f, 19.47f, 2.01f)
                    curveTo(19.49f, 2.01f, 19.51f, 2.01f, 19.53f, 2.01f)
                    curveTo(19.6f, 2.01f, 19.66f, 2.02f, 19.73f, 2.05f)
                    curveTo(19.74f, 2.05f, 19.74f, 2.05f, 19.75f, 2.05f)
                    curveTo(19.82f, 2.08f, 19.88f, 2.12f, 19.93f, 2.17f)
                    curveTo(19.94f, 2.18f, 19.94f, 2.18f, 19.95f, 2.18f)
                    lineTo(21.2f, 3.43f)
                    curveTo(21.44f, 3.67f, 21.44f, 4.07f, 21.19f, 4.31f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(9f, 10.38f)
                    curveTo(10.315f, 10.38f, 11.38f, 9.315f, 11.38f, 8f)
                    curveTo(11.38f, 6.686f, 10.315f, 5.62f, 9f, 5.62f)
                    curveTo(7.686f, 5.62f, 6.62f, 6.686f, 6.62f, 8f)
                    curveTo(6.62f, 9.315f, 7.686f, 10.38f, 9f, 10.38f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(20.97f, 8f)
                    horizontalLineTo(20.5f)
                    verticalLineTo(12.61f)
                    lineTo(20.37f, 12.5f)
                    curveTo(19.59f, 11.83f, 18.33f, 11.83f, 17.55f, 12.5f)
                    lineTo(13.39f, 16.07f)
                    curveTo(12.61f, 16.74f, 11.35f, 16.74f, 10.57f, 16.07f)
                    lineTo(10.23f, 15.79f)
                    curveTo(9.52f, 15.17f, 8.39f, 15.11f, 7.59f, 15.65f)
                    lineTo(3.85f, 18.16f)
                    curveTo(3.63f, 17.6f, 3.5f, 16.95f, 3.5f, 16.19f)
                    verticalLineTo(7.81f)
                    curveTo(3.5f, 4.99f, 4.99f, 3.5f, 7.81f, 3.5f)
                    horizontalLineTo(16f)
                    verticalLineTo(3.03f)
                    curveTo(16f, 2.63f, 16.07f, 2.29f, 16.23f, 2f)
                    horizontalLineTo(7.81f)
                    curveTo(4.17f, 2f, 2f, 4.17f, 2f, 7.81f)
                    verticalLineTo(16.19f)
                    curveTo(2f, 17.28f, 2.19f, 18.23f, 2.56f, 19.03f)
                    curveTo(3.42f, 20.93f, 5.26f, 22f, 7.81f, 22f)
                    horizontalLineTo(16.19f)
                    curveTo(19.83f, 22f, 22f, 19.83f, 22f, 16.19f)
                    verticalLineTo(7.77f)
                    curveTo(21.71f, 7.93f, 21.37f, 8f, 20.97f, 8f)
                    close()
                }
            }
        }.build()

        return _GalleryExport!!
    }

@Suppress("ObjectPropertyName")
private var _GalleryExport: ImageVector? = null
