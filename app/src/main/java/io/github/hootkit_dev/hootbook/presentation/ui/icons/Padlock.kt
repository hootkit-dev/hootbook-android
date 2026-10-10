package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Padlock: ImageVector
    get() {
        if (_Padlock != null) {
            return _Padlock!!
        }
        _Padlock = ImageVector.Builder(
            name = "Padlock",
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
                    moveTo(12f, 17.35f)
                    curveTo(12.9f, 17.35f, 13.63f, 16.62f, 13.63f, 15.72f)
                    curveTo(13.63f, 14.82f, 12.9f, 14.09f, 12f, 14.09f)
                    curveTo(11.1f, 14.09f, 10.37f, 14.82f, 10.37f, 15.72f)
                    curveTo(10.37f, 16.62f, 11.1f, 17.35f, 12f, 17.35f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(18.28f, 9.53f)
                    verticalLineTo(8.28f)
                    curveTo(18.28f, 5.58f, 17.63f, 2f, 12f, 2f)
                    curveTo(6.37f, 2f, 5.72f, 5.58f, 5.72f, 8.28f)
                    verticalLineTo(9.53f)
                    curveTo(2.92f, 9.88f, 2f, 11.3f, 2f, 14.79f)
                    verticalLineTo(16.65f)
                    curveTo(2f, 20.75f, 3.25f, 22f, 7.35f, 22f)
                    horizontalLineTo(16.65f)
                    curveTo(20.75f, 22f, 22f, 20.75f, 22f, 16.65f)
                    verticalLineTo(14.79f)
                    curveTo(22f, 11.3f, 21.08f, 9.88f, 18.28f, 9.53f)
                    close()
                    moveTo(12f, 18.74f)
                    curveTo(10.33f, 18.74f, 8.98f, 17.38f, 8.98f, 15.72f)
                    curveTo(8.98f, 14.05f, 10.34f, 12.7f, 12f, 12.7f)
                    curveTo(13.66f, 12.7f, 15.02f, 14.06f, 15.02f, 15.72f)
                    curveTo(15.02f, 17.39f, 13.67f, 18.74f, 12f, 18.74f)
                    close()
                    moveTo(7.35f, 9.44f)
                    curveTo(7.27f, 9.44f, 7.2f, 9.44f, 7.12f, 9.44f)
                    verticalLineTo(8.28f)
                    curveTo(7.12f, 5.35f, 7.95f, 3.4f, 12f, 3.4f)
                    curveTo(16.05f, 3.4f, 16.88f, 5.35f, 16.88f, 8.28f)
                    verticalLineTo(9.45f)
                    curveTo(16.8f, 9.45f, 16.73f, 9.45f, 16.65f, 9.45f)
                    horizontalLineTo(7.35f)
                    verticalLineTo(9.44f)
                    close()
                }
            }
        }.build()

        return _Padlock!!
    }

@Suppress("ObjectPropertyName")
private var _Padlock: ImageVector? = null
