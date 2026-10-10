package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.BagTimer: ImageVector
    get() {
        if (_BagTimer != null) {
            return _BagTimer!!
        }
        _BagTimer = ImageVector.Builder(
            name = "BagTimer",
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
                    moveTo(19.96f, 8.96f)
                    curveTo(19.29f, 8.22f, 18.28f, 7.79f, 16.88f, 7.64f)
                    verticalLineTo(6.88f)
                    curveTo(16.88f, 5.51f, 16.3f, 4.19f, 15.28f, 3.27f)
                    curveTo(14.25f, 2.33f, 12.91f, 1.89f, 11.52f, 2.02f)
                    curveTo(9.13f, 2.25f, 7.12f, 4.56f, 7.12f, 7.06f)
                    verticalLineTo(7.64f)
                    curveTo(5.72f, 7.79f, 4.71f, 8.22f, 4.04f, 8.96f)
                    curveTo(3.07f, 10.04f, 3.1f, 11.48f, 3.21f, 12.48f)
                    lineTo(3.91f, 18.05f)
                    curveTo(4.12f, 20f, 4.91f, 22f, 9.21f, 22f)
                    horizontalLineTo(14.79f)
                    curveTo(19.09f, 22f, 19.88f, 20f, 20.09f, 18.06f)
                    lineTo(20.79f, 12.47f)
                    curveTo(20.9f, 11.48f, 20.93f, 10.04f, 19.96f, 8.96f)
                    close()
                    moveTo(11.66f, 3.41f)
                    curveTo(12.66f, 3.32f, 13.61f, 3.63f, 14.35f, 4.3f)
                    curveTo(15.08f, 4.96f, 15.49f, 5.9f, 15.49f, 6.88f)
                    verticalLineTo(7.58f)
                    horizontalLineTo(8.51f)
                    verticalLineTo(7.06f)
                    curveTo(8.51f, 5.28f, 9.98f, 3.57f, 11.66f, 3.41f)
                    close()
                    moveTo(12f, 18.58f)
                    curveTo(9.91f, 18.58f, 8.21f, 16.88f, 8.21f, 14.79f)
                    curveTo(8.21f, 12.7f, 9.91f, 11f, 12f, 11f)
                    curveTo(14.09f, 11f, 15.79f, 12.7f, 15.79f, 14.79f)
                    curveTo(15.79f, 16.88f, 14.09f, 18.58f, 12f, 18.58f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(11f, 16.58f)
                    curveTo(10.75f, 16.58f, 10.5f, 16.45f, 10.36f, 16.22f)
                    curveTo(10.15f, 15.87f, 10.26f, 15.4f, 10.62f, 15.19f)
                    lineTo(11.51f, 14.66f)
                    verticalLineTo(13.58f)
                    curveTo(11.51f, 13.17f, 11.85f, 12.83f, 12.26f, 12.83f)
                    curveTo(12.67f, 12.83f, 13f, 13.16f, 13f, 13.58f)
                    verticalLineTo(15.08f)
                    curveTo(13f, 15.34f, 12.86f, 15.59f, 12.64f, 15.72f)
                    lineTo(11.39f, 16.47f)
                    curveTo(11.27f, 16.54f, 11.13f, 16.58f, 11f, 16.58f)
                    close()
                }
            }
        }.build()

        return _BagTimer!!
    }

@Suppress("ObjectPropertyName")
private var _BagTimer: ImageVector? = null
