package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.MedalStar: ImageVector
    get() {
        if (_MedalStar != null) {
            return _MedalStar!!
        }
        _MedalStar = ImageVector.Builder(
            name = "MedalStar",
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
                    moveTo(21.25f, 18.47f)
                    lineTo(19.6f, 18.86f)
                    curveTo(19.23f, 18.95f, 18.94f, 19.23f, 18.86f, 19.6f)
                    lineTo(18.51f, 21.07f)
                    curveTo(18.32f, 21.87f, 17.3f, 22.12f, 16.77f, 21.49f)
                    lineTo(13.78f, 18.05f)
                    curveTo(13.54f, 17.77f, 13.67f, 17.33f, 14.03f, 17.24f)
                    curveTo(15.8f, 16.81f, 17.39f, 15.82f, 18.56f, 14.41f)
                    curveTo(18.75f, 14.18f, 19.09f, 14.15f, 19.3f, 14.36f)
                    lineTo(21.52f, 16.58f)
                    curveTo(22.28f, 17.34f, 22.01f, 18.29f, 21.25f, 18.47f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(2.7f, 18.47f)
                    lineTo(4.35f, 18.86f)
                    curveTo(4.72f, 18.95f, 5.01f, 19.23f, 5.09f, 19.6f)
                    lineTo(5.44f, 21.07f)
                    curveTo(5.63f, 21.87f, 6.65f, 22.12f, 7.18f, 21.49f)
                    lineTo(10.17f, 18.05f)
                    curveTo(10.41f, 17.77f, 10.28f, 17.33f, 9.92f, 17.24f)
                    curveTo(8.15f, 16.81f, 6.56f, 15.82f, 5.39f, 14.41f)
                    curveTo(5.2f, 14.18f, 4.86f, 14.15f, 4.65f, 14.36f)
                    lineTo(2.43f, 16.58f)
                    curveTo(1.67f, 17.34f, 1.94f, 18.29f, 2.7f, 18.47f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(12f, 2f)
                    curveTo(8.13f, 2f, 5f, 5.13f, 5f, 9f)
                    curveTo(5f, 10.45f, 5.43f, 11.78f, 6.17f, 12.89f)
                    curveTo(7.25f, 14.49f, 8.96f, 15.62f, 10.95f, 15.91f)
                    curveTo(11.29f, 15.97f, 11.64f, 16f, 12f, 16f)
                    curveTo(12.36f, 16f, 12.71f, 15.97f, 13.05f, 15.91f)
                    curveTo(15.04f, 15.62f, 16.75f, 14.49f, 17.83f, 12.89f)
                    curveTo(18.57f, 11.78f, 19f, 10.45f, 19f, 9f)
                    curveTo(19f, 5.13f, 15.87f, 2f, 12f, 2f)
                    close()
                    moveTo(15.06f, 8.78f)
                    lineTo(14.23f, 9.61f)
                    curveTo(14.09f, 9.75f, 14.01f, 10.02f, 14.06f, 10.22f)
                    lineTo(14.3f, 11.25f)
                    curveTo(14.49f, 12.06f, 14.06f, 12.38f, 13.34f, 11.95f)
                    lineTo(12.34f, 11.36f)
                    curveTo(12.16f, 11.25f, 11.86f, 11.25f, 11.68f, 11.36f)
                    lineTo(10.68f, 11.95f)
                    curveTo(9.96f, 12.37f, 9.53f, 12.06f, 9.72f, 11.25f)
                    lineTo(9.96f, 10.22f)
                    curveTo(10f, 10.03f, 9.93f, 9.75f, 9.79f, 9.61f)
                    lineTo(8.94f, 8.78f)
                    curveTo(8.45f, 8.29f, 8.61f, 7.8f, 9.29f, 7.69f)
                    lineTo(10.36f, 7.51f)
                    curveTo(10.54f, 7.48f, 10.75f, 7.32f, 10.83f, 7.16f)
                    lineTo(11.42f, 5.98f)
                    curveTo(11.74f, 5.34f, 12.26f, 5.34f, 12.58f, 5.98f)
                    lineTo(13.17f, 7.16f)
                    curveTo(13.25f, 7.32f, 13.46f, 7.48f, 13.65f, 7.51f)
                    lineTo(14.72f, 7.69f)
                    curveTo(15.39f, 7.8f, 15.55f, 8.29f, 15.06f, 8.78f)
                    close()
                }
            }
        }.build()

        return _MedalStar!!
    }

@Suppress("ObjectPropertyName")
private var _MedalStar: ImageVector? = null
