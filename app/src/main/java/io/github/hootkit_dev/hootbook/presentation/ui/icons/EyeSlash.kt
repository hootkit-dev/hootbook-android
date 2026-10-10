package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.EyeSlash: ImageVector
    get() {
        if (_EyeSlash != null) {
            return _EyeSlash!!
        }
        _EyeSlash = ImageVector.Builder(
            name = "EyeSlash",
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
                    moveTo(21.27f, 9.18f)
                    curveTo(20.98f, 8.72f, 20.67f, 8.29f, 20.35f, 7.89f)
                    curveTo(19.98f, 7.42f, 19.28f, 7.38f, 18.86f, 7.8f)
                    lineTo(15.86f, 10.8f)
                    curveTo(16.08f, 11.46f, 16.12f, 12.22f, 15.92f, 13.01f)
                    curveTo(15.57f, 14.42f, 14.43f, 15.56f, 13.02f, 15.91f)
                    curveTo(12.23f, 16.11f, 11.47f, 16.07f, 10.81f, 15.85f)
                    curveTo(10.81f, 15.85f, 9.38f, 17.28f, 8.35f, 18.31f)
                    curveTo(7.85f, 18.81f, 8.01f, 19.69f, 8.68f, 19.95f)
                    curveTo(9.75f, 20.36f, 10.86f, 20.57f, 12f, 20.57f)
                    curveTo(13.78f, 20.57f, 15.51f, 20.05f, 17.09f, 19.08f)
                    curveTo(18.7f, 18.08f, 20.15f, 16.61f, 21.32f, 14.74f)
                    curveTo(22.27f, 13.23f, 22.22f, 10.69f, 21.27f, 9.18f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(14.02f, 9.981f)
                    lineTo(9.98f, 14.021f)
                    curveTo(9.47f, 13.501f, 9.14f, 12.781f, 9.14f, 12.001f)
                    curveTo(9.14f, 10.431f, 10.42f, 9.141f, 12f, 9.141f)
                    curveTo(12.78f, 9.141f, 13.5f, 9.471f, 14.02f, 9.981f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(18.25f, 5.75f)
                    lineTo(14.86f, 9.14f)
                    curveTo(14.13f, 8.4f, 13.12f, 7.96f, 12f, 7.96f)
                    curveTo(9.76f, 7.96f, 7.96f, 9.77f, 7.96f, 12f)
                    curveTo(7.96f, 13.12f, 8.41f, 14.13f, 9.14f, 14.86f)
                    lineTo(5.76f, 18.25f)
                    horizontalLineTo(5.75f)
                    curveTo(4.64f, 17.35f, 3.62f, 16.2f, 2.75f, 14.84f)
                    curveTo(1.75f, 13.27f, 1.75f, 10.72f, 2.75f, 9.15f)
                    curveTo(3.91f, 7.33f, 5.33f, 5.9f, 6.91f, 4.92f)
                    curveTo(8.49f, 3.96f, 10.22f, 3.43f, 12f, 3.43f)
                    curveTo(14.23f, 3.43f, 16.39f, 4.25f, 18.25f, 5.75f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(14.86f, 12f)
                    curveTo(14.86f, 13.57f, 13.58f, 14.86f, 12f, 14.86f)
                    curveTo(11.94f, 14.86f, 11.89f, 14.86f, 11.83f, 14.84f)
                    lineTo(14.84f, 11.83f)
                    curveTo(14.86f, 11.89f, 14.86f, 11.94f, 14.86f, 12f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(21.77f, 2.231f)
                    curveTo(21.47f, 1.931f, 20.98f, 1.931f, 20.68f, 2.231f)
                    lineTo(2.23f, 20.691f)
                    curveTo(1.93f, 20.991f, 1.93f, 21.481f, 2.23f, 21.781f)
                    curveTo(2.38f, 21.921f, 2.57f, 22.001f, 2.77f, 22.001f)
                    curveTo(2.97f, 22.001f, 3.16f, 21.921f, 3.31f, 21.771f)
                    lineTo(21.77f, 3.311f)
                    curveTo(22.08f, 3.011f, 22.08f, 2.531f, 21.77f, 2.231f)
                    close()
                }
            }
        }.build()

        return _EyeSlash!!
    }

@Suppress("ObjectPropertyName")
private var _EyeSlash: ImageVector? = null
