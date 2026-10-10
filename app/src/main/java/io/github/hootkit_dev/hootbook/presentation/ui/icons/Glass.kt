package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Glass: ImageVector
    get() {
        if (_Glass != null) {
            return _Glass!!
        }
        _Glass = ImageVector.Builder(
            name = "Glass",
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
                    moveTo(15.84f, 22f)
                    horizontalLineTo(8.16f)
                    curveTo(3.97f, 22f, 3.14f, 19.47f, 4.5f, 16.39f)
                    lineTo(5.94f, 13.12f)
                    curveTo(5.94f, 13.12f, 9f, 13f, 12f, 14f)
                    curveTo(15f, 15f, 17.83f, 13.11f, 17.83f, 13.11f)
                    lineTo(18.02f, 12.99f)
                    lineTo(19.51f, 16.4f)
                    curveTo(20.85f, 19.48f, 19.97f, 22f, 15.84f, 22f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(15.44f, 6.74f)
                    horizontalLineTo(15.28f)
                    lineTo(17.41f, 11.6f)
                    lineTo(17f, 11.86f)
                    curveTo(16.98f, 11.87f, 14.72f, 13.32f, 12.47f, 12.58f)
                    curveTo(10.12f, 11.79f, 7.76f, 11.65f, 6.6f, 11.63f)
                    lineTo(8.74f, 6.74f)
                    horizontalLineTo(8.44f)
                    curveTo(7.79f, 6.74f, 7.19f, 6.48f, 6.76f, 6.05f)
                    curveTo(6.33f, 5.62f, 6.07f, 5.02f, 6.07f, 4.37f)
                    curveTo(6.07f, 3.07f, 7.13f, 2f, 8.44f, 2f)
                    horizontalLineTo(15.55f)
                    curveTo(16.21f, 2f, 16.8f, 2.27f, 17.23f, 2.7f)
                    curveTo(17.79f, 3.26f, 18.08f, 4.08f, 17.86f, 4.95f)
                    curveTo(17.6f, 6.03f, 16.56f, 6.74f, 15.44f, 6.74f)
                    close()
                }
            }
        }.build()

        return _Glass!!
    }

@Suppress("ObjectPropertyName")
private var _Glass: ImageVector? = null
