package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.MainComponent: ImageVector
    get() {
        if (_MainComponent != null) {
            return _MainComponent!!
        }
        _MainComponent = ImageVector.Builder(
            name = "MainComponent",
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
                    moveTo(16.51f, 5.01f)
                    lineTo(14.55f, 3.05f)
                    curveTo(13.15f, 1.65f, 10.85f, 1.65f, 9.45f, 3.05f)
                    lineTo(7.49f, 5.01f)
                    curveTo(7.1f, 5.4f, 7.1f, 6.04f, 7.49f, 6.43f)
                    lineTo(11.3f, 10.24f)
                    curveTo(11.69f, 10.63f, 12.32f, 10.63f, 12.71f, 10.24f)
                    lineTo(16.52f, 6.43f)
                    curveTo(16.9f, 6.04f, 16.9f, 5.4f, 16.51f, 5.01f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(5.01f, 7.49f)
                    lineTo(3.05f, 9.45f)
                    curveTo(1.65f, 10.85f, 1.65f, 13.15f, 3.05f, 14.55f)
                    lineTo(5.01f, 16.51f)
                    curveTo(5.4f, 16.9f, 6.03f, 16.9f, 6.42f, 16.51f)
                    lineTo(10.23f, 12.7f)
                    curveTo(10.62f, 12.31f, 10.62f, 11.68f, 10.23f, 11.29f)
                    lineTo(6.43f, 7.49f)
                    curveTo(6.04f, 7.1f, 5.4f, 7.1f, 5.01f, 7.49f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(20.95f, 9.45f)
                    lineTo(18.99f, 7.49f)
                    curveTo(18.6f, 7.1f, 17.97f, 7.1f, 17.58f, 7.49f)
                    lineTo(13.77f, 11.3f)
                    curveTo(13.38f, 11.69f, 13.38f, 12.32f, 13.77f, 12.71f)
                    lineTo(17.58f, 16.52f)
                    curveTo(17.97f, 16.91f, 18.6f, 16.91f, 18.99f, 16.52f)
                    lineTo(20.95f, 14.56f)
                    curveTo(22.35f, 13.15f, 22.35f, 10.85f, 20.95f, 9.45f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(7.49f, 18.989f)
                    lineTo(9.45f, 20.949f)
                    curveTo(10.85f, 22.349f, 13.15f, 22.349f, 14.55f, 20.949f)
                    lineTo(16.51f, 18.989f)
                    curveTo(16.9f, 18.599f, 16.9f, 17.969f, 16.51f, 17.579f)
                    lineTo(12.7f, 13.769f)
                    curveTo(12.31f, 13.379f, 11.68f, 13.379f, 11.29f, 13.769f)
                    lineTo(7.48f, 17.579f)
                    curveTo(7.1f, 17.959f, 7.1f, 18.599f, 7.49f, 18.989f)
                    close()
                }
            }
        }.build()

        return _MainComponent!!
    }

@Suppress("ObjectPropertyName")
private var _MainComponent: ImageVector? = null
