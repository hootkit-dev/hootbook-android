package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Shop: ImageVector
    get() {
        if (_Shop != null) {
            return _Shop!!
        }
        _Shop = ImageVector.Builder(
            name = "Shop",
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
                    moveTo(22.36f, 8.27f)
                    lineTo(22.07f, 5.5f)
                    curveTo(21.65f, 2.48f, 20.28f, 1.25f, 17.35f, 1.25f)
                    horizontalLineTo(14.99f)
                    horizontalLineTo(13.51f)
                    horizontalLineTo(10.47f)
                    horizontalLineTo(8.99f)
                    horizontalLineTo(6.59f)
                    curveTo(3.65f, 1.25f, 2.29f, 2.48f, 1.86f, 5.53f)
                    lineTo(1.59f, 8.28f)
                    curveTo(1.49f, 9.35f, 1.78f, 10.39f, 2.41f, 11.2f)
                    curveTo(3.17f, 12.19f, 4.34f, 12.75f, 5.64f, 12.75f)
                    curveTo(6.9f, 12.75f, 8.11f, 12.12f, 8.87f, 11.11f)
                    curveTo(9.55f, 12.12f, 10.71f, 12.75f, 12f, 12.75f)
                    curveTo(13.29f, 12.75f, 14.42f, 12.15f, 15.11f, 11.15f)
                    curveTo(15.88f, 12.14f, 17.07f, 12.75f, 18.31f, 12.75f)
                    curveTo(19.64f, 12.75f, 20.84f, 12.16f, 21.59f, 11.12f)
                    curveTo(22.19f, 10.32f, 22.46f, 9.31f, 22.36f, 8.27f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(11.35f, 16.66f)
                    curveTo(10.08f, 16.79f, 9.12f, 17.87f, 9.12f, 19.15f)
                    verticalLineTo(21.89f)
                    curveTo(9.12f, 22.16f, 9.34f, 22.38f, 9.61f, 22.38f)
                    horizontalLineTo(14.38f)
                    curveTo(14.65f, 22.38f, 14.87f, 22.16f, 14.87f, 21.89f)
                    verticalLineTo(19.5f)
                    curveTo(14.88f, 17.41f, 13.65f, 16.42f, 11.35f, 16.66f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(21.37f, 14.4f)
                    verticalLineTo(17.38f)
                    curveTo(21.37f, 20.14f, 19.13f, 22.38f, 16.37f, 22.38f)
                    curveTo(16.1f, 22.38f, 15.88f, 22.16f, 15.88f, 21.89f)
                    verticalLineTo(19.5f)
                    curveTo(15.88f, 18.22f, 15.49f, 17.22f, 14.73f, 16.54f)
                    curveTo(14.06f, 15.93f, 13.15f, 15.63f, 12.02f, 15.63f)
                    curveTo(11.77f, 15.63f, 11.52f, 15.64f, 11.25f, 15.67f)
                    curveTo(9.47f, 15.85f, 8.12f, 17.35f, 8.12f, 19.15f)
                    verticalLineTo(21.89f)
                    curveTo(8.12f, 22.16f, 7.9f, 22.38f, 7.63f, 22.38f)
                    curveTo(4.87f, 22.38f, 2.63f, 20.14f, 2.63f, 17.38f)
                    verticalLineTo(14.42f)
                    curveTo(2.63f, 13.72f, 3.32f, 13.25f, 3.97f, 13.48f)
                    curveTo(4.24f, 13.57f, 4.51f, 13.64f, 4.79f, 13.68f)
                    curveTo(4.91f, 13.7f, 5.04f, 13.72f, 5.16f, 13.72f)
                    curveTo(5.32f, 13.74f, 5.48f, 13.75f, 5.64f, 13.75f)
                    curveTo(6.8f, 13.75f, 7.94f, 13.32f, 8.84f, 12.58f)
                    curveTo(9.7f, 13.32f, 10.82f, 13.75f, 12f, 13.75f)
                    curveTo(13.19f, 13.75f, 14.29f, 13.34f, 15.15f, 12.6f)
                    curveTo(16.05f, 13.33f, 17.17f, 13.75f, 18.31f, 13.75f)
                    curveTo(18.49f, 13.75f, 18.67f, 13.74f, 18.84f, 13.72f)
                    curveTo(18.96f, 13.71f, 19.07f, 13.7f, 19.18f, 13.68f)
                    curveTo(19.49f, 13.64f, 19.77f, 13.55f, 20.05f, 13.46f)
                    curveTo(20.7f, 13.24f, 21.37f, 13.72f, 21.37f, 14.4f)
                    close()
                }
            }
        }.build()

        return _Shop!!
    }

@Suppress("ObjectPropertyName")
private var _Shop: ImageVector? = null
