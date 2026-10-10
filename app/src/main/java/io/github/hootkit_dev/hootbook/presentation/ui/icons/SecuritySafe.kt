package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.SecuritySafe: ImageVector
    get() {
        if (_SecuritySafe != null) {
            return _SecuritySafe!!
        }
        _SecuritySafe = ImageVector.Builder(
            name = "SecuritySafe",
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
                    moveTo(20.91f, 11.12f)
                    verticalLineTo(6.73f)
                    curveTo(20.91f, 5.91f, 20.29f, 4.98f, 19.52f, 4.67f)
                    lineTo(13.95f, 2.39f)
                    curveTo(12.7f, 1.88f, 11.29f, 1.88f, 10.04f, 2.39f)
                    lineTo(4.47f, 4.67f)
                    curveTo(3.71f, 4.98f, 3.09f, 5.91f, 3.09f, 6.73f)
                    verticalLineTo(11.12f)
                    curveTo(3.09f, 16.01f, 6.64f, 20.59f, 11.49f, 21.93f)
                    curveTo(11.82f, 22.02f, 12.18f, 22.02f, 12.51f, 21.93f)
                    curveTo(17.36f, 20.59f, 20.91f, 16.01f, 20.91f, 11.12f)
                    close()
                    moveTo(12.75f, 12.87f)
                    verticalLineTo(15.5f)
                    curveTo(12.75f, 15.91f, 12.41f, 16.25f, 12f, 16.25f)
                    curveTo(11.59f, 16.25f, 11.25f, 15.91f, 11.25f, 15.5f)
                    verticalLineTo(12.87f)
                    curveTo(10.24f, 12.55f, 9.5f, 11.61f, 9.5f, 10.5f)
                    curveTo(9.5f, 9.12f, 10.62f, 8f, 12f, 8f)
                    curveTo(13.38f, 8f, 14.5f, 9.12f, 14.5f, 10.5f)
                    curveTo(14.5f, 11.62f, 13.76f, 12.55f, 12.75f, 12.87f)
                    close()
                }
            }
        }.build()

        return _SecuritySafe!!
    }

@Suppress("ObjectPropertyName")
private var _SecuritySafe: ImageVector? = null
