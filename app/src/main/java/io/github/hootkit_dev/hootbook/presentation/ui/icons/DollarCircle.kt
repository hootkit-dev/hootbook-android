package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.DollarCircle: ImageVector
    get() {
        if (_DollarCircle != null) {
            return _DollarCircle!!
        }
        _DollarCircle = ImageVector.Builder(
            name = "DollarCircle",
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
                    moveTo(12.75f, 15.92f)
                    horizontalLineTo(13.4f)
                    curveTo(14.05f, 15.92f, 14.59f, 15.34f, 14.59f, 14.64f)
                    curveTo(14.59f, 13.77f, 14.28f, 13.6f, 13.77f, 13.42f)
                    lineTo(12.76f, 13.07f)
                    verticalLineTo(15.92f)
                    horizontalLineTo(12.75f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(11.97f, 1.9f)
                    curveTo(6.45f, 1.92f, 1.98f, 6.41f, 2f, 11.93f)
                    curveTo(2.02f, 17.45f, 6.51f, 21.92f, 12.03f, 21.9f)
                    curveTo(17.55f, 21.88f, 22.02f, 17.39f, 22f, 11.87f)
                    curveTo(21.98f, 6.35f, 17.49f, 1.89f, 11.97f, 1.9f)
                    close()
                    moveTo(14.26f, 12f)
                    curveTo(15.04f, 12.27f, 16.09f, 12.85f, 16.09f, 14.64f)
                    curveTo(16.09f, 16.18f, 14.88f, 17.42f, 13.4f, 17.42f)
                    horizontalLineTo(12.75f)
                    verticalLineTo(18f)
                    curveTo(12.75f, 18.41f, 12.41f, 18.75f, 12f, 18.75f)
                    curveTo(11.59f, 18.75f, 11.25f, 18.41f, 11.25f, 18f)
                    verticalLineTo(17.42f)
                    horizontalLineTo(10.89f)
                    curveTo(9.25f, 17.42f, 7.92f, 16.04f, 7.92f, 14.34f)
                    curveTo(7.92f, 13.93f, 8.26f, 13.59f, 8.67f, 13.59f)
                    curveTo(9.08f, 13.59f, 9.42f, 13.93f, 9.42f, 14.34f)
                    curveTo(9.42f, 15.21f, 10.08f, 15.92f, 10.89f, 15.92f)
                    horizontalLineTo(11.25f)
                    verticalLineTo(12.54f)
                    lineTo(9.74f, 12f)
                    curveTo(8.96f, 11.73f, 7.91f, 11.15f, 7.91f, 9.36f)
                    curveTo(7.91f, 7.82f, 9.12f, 6.58f, 10.6f, 6.58f)
                    horizontalLineTo(11.25f)
                    verticalLineTo(6f)
                    curveTo(11.25f, 5.59f, 11.59f, 5.25f, 12f, 5.25f)
                    curveTo(12.41f, 5.25f, 12.75f, 5.59f, 12.75f, 6f)
                    verticalLineTo(6.58f)
                    horizontalLineTo(13.11f)
                    curveTo(14.75f, 6.58f, 16.08f, 7.96f, 16.08f, 9.66f)
                    curveTo(16.08f, 10.07f, 15.74f, 10.41f, 15.33f, 10.41f)
                    curveTo(14.92f, 10.41f, 14.58f, 10.07f, 14.58f, 9.66f)
                    curveTo(14.58f, 8.79f, 13.92f, 8.08f, 13.11f, 8.08f)
                    horizontalLineTo(12.75f)
                    verticalLineTo(11.46f)
                    lineTo(14.26f, 12f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(9.42f, 9.37f)
                    curveTo(9.42f, 10.24f, 9.73f, 10.41f, 10.24f, 10.59f)
                    lineTo(11.25f, 10.94f)
                    verticalLineTo(8.08f)
                    horizontalLineTo(10.6f)
                    curveTo(9.95f, 8.08f, 9.42f, 8.66f, 9.42f, 9.37f)
                    close()
                }
            }
        }.build()

        return _DollarCircle!!
    }

@Suppress("ObjectPropertyName")
private var _DollarCircle: ImageVector? = null
