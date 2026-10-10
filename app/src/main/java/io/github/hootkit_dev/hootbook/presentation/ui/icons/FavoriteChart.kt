package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.FavoriteChart: ImageVector
    get() {
        if (_FavoriteChart != null) {
            return _FavoriteChart!!
        }
        _FavoriteChart = ImageVector.Builder(
            name = "FavoriteChart",
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
                    moveTo(19.12f, 14.939f)
                    lineTo(19.44f, 15.59f)
                    curveTo(19.6f, 15.91f, 20.01f, 16.209f, 20.35f, 16.27f)
                    lineTo(20.78f, 16.34f)
                    curveTo(22.09f, 16.559f, 22.39f, 17.52f, 21.46f, 18.459f)
                    lineTo(21.06f, 18.86f)
                    curveTo(20.79f, 19.129f, 20.65f, 19.649f, 20.73f, 20.029f)
                    lineTo(20.78f, 20.27f)
                    curveTo(21.14f, 21.85f, 20.3f, 22.459f, 18.93f, 21.629f)
                    lineTo(18.64f, 21.449f)
                    curveTo(18.29f, 21.24f, 17.71f, 21.24f, 17.36f, 21.449f)
                    lineTo(17.07f, 21.629f)
                    curveTo(15.69f, 22.459f, 14.86f, 21.85f, 15.22f, 20.27f)
                    lineTo(15.27f, 20.029f)
                    curveTo(15.35f, 19.66f, 15.21f, 19.129f, 14.94f, 18.86f)
                    lineTo(14.54f, 18.459f)
                    curveTo(13.61f, 17.51f, 13.91f, 16.559f, 15.22f, 16.34f)
                    lineTo(15.65f, 16.27f)
                    curveTo(16f, 16.209f, 16.4f, 15.91f, 16.56f, 15.59f)
                    lineTo(16.88f, 14.939f)
                    curveTo(17.5f, 13.689f, 18.5f, 13.689f, 19.12f, 14.939f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(16.19f, 2f)
                    horizontalLineTo(7.81f)
                    curveTo(4.17f, 2f, 2f, 4.17f, 2f, 7.81f)
                    verticalLineTo(16.19f)
                    curveTo(2f, 19.83f, 4.17f, 22f, 7.81f, 22f)
                    horizontalLineTo(13.09f)
                    curveTo(13.44f, 22f, 13.69f, 21.64f, 13.65f, 21.29f)
                    curveTo(13.61f, 20.91f, 13.63f, 20.46f, 13.75f, 19.94f)
                    curveTo(13.77f, 19.87f, 13.75f, 19.79f, 13.69f, 19.73f)
                    lineTo(13.47f, 19.51f)
                    curveTo(12.62f, 18.65f, 12.31f, 17.61f, 12.61f, 16.66f)
                    curveTo(12.92f, 15.72f, 13.78f, 15.06f, 14.97f, 14.86f)
                    lineTo(15.27f, 14.81f)
                    lineTo(15.54f, 14.27f)
                    curveTo(16.09f, 13.15f, 16.99f, 12.5f, 18f, 12.5f)
                    curveTo(19.01f, 12.5f, 19.91f, 13.15f, 20.46f, 14.27f)
                    lineTo(20.61f, 14.58f)
                    curveTo(20.68f, 14.73f, 20.82f, 14.83f, 20.98f, 14.86f)
                    curveTo(21.07f, 14.88f, 21.16f, 14.9f, 21.25f, 14.92f)
                    curveTo(21.6f, 15.01f, 22f, 14.73f, 22f, 14.36f)
                    verticalLineTo(7.81f)
                    curveTo(22f, 4.17f, 19.83f, 2f, 16.19f, 2f)
                    close()
                    moveTo(16.26f, 8.96f)
                    lineTo(13.95f, 11.94f)
                    curveTo(13.66f, 12.31f, 13.25f, 12.55f, 12.78f, 12.6f)
                    curveTo(12.32f, 12.66f, 11.85f, 12.53f, 11.49f, 12.24f)
                    lineTo(9.66f, 10.82f)
                    curveTo(9.59f, 10.76f, 9.51f, 10.76f, 9.47f, 10.77f)
                    curveTo(9.43f, 10.77f, 9.36f, 10.79f, 9.3f, 10.87f)
                    lineTo(6.92f, 13.96f)
                    curveTo(6.77f, 14.15f, 6.55f, 14.25f, 6.32f, 14.25f)
                    curveTo(6.16f, 14.25f, 6f, 14.2f, 5.86f, 14.09f)
                    curveTo(5.53f, 13.84f, 5.47f, 13.37f, 5.72f, 13.04f)
                    lineTo(8.1f, 9.95f)
                    curveTo(8.39f, 9.58f, 8.8f, 9.34f, 9.27f, 9.28f)
                    curveTo(9.74f, 9.22f, 10.2f, 9.35f, 10.57f, 9.64f)
                    lineTo(12.4f, 11.08f)
                    curveTo(12.47f, 11.14f, 12.54f, 11.13f, 12.59f, 11.13f)
                    curveTo(12.63f, 11.13f, 12.7f, 11.11f, 12.76f, 11.03f)
                    lineTo(15.07f, 8.05f)
                    curveTo(15.32f, 7.72f, 15.79f, 7.66f, 16.12f, 7.92f)
                    curveTo(16.46f, 8.17f, 16.51f, 8.64f, 16.26f, 8.96f)
                    close()
                }
            }
        }.build()

        return _FavoriteChart!!
    }

@Suppress("ObjectPropertyName")
private var _FavoriteChart: ImageVector? = null
