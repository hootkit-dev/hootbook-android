package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Paperclip2: ImageVector
    get() {
        if (_Paperclip2 != null) {
            return _Paperclip2!!
        }
        _Paperclip2 = ImageVector.Builder(
            name = "Paperclip2",
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
                    moveTo(12.33f, 21.34f)
                    curveTo(11.24f, 21.34f, 10.15f, 20.93f, 9.32f, 20.1f)
                    curveTo(7.66f, 18.44f, 7.66f, 15.75f, 9.32f, 14.09f)
                    lineTo(11.8f, 11.62f)
                    curveTo(12.09f, 11.33f, 12.57f, 11.33f, 12.86f, 11.62f)
                    curveTo(13.15f, 11.91f, 13.15f, 12.39f, 12.86f, 12.68f)
                    lineTo(10.38f, 15.15f)
                    curveTo(9.31f, 16.22f, 9.31f, 17.97f, 10.38f, 19.04f)
                    curveTo(11.45f, 20.11f, 13.2f, 20.11f, 14.27f, 19.04f)
                    lineTo(18.16f, 15.15f)
                    curveTo(19.34f, 13.97f, 19.99f, 12.4f, 19.99f, 10.73f)
                    curveTo(19.99f, 9.06f, 19.34f, 7.49f, 18.16f, 6.31f)
                    curveTo(15.72f, 3.87f, 11.76f, 3.87f, 9.32f, 6.31f)
                    lineTo(5.08f, 10.55f)
                    curveTo(4.09f, 11.54f, 3.54f, 12.86f, 3.54f, 14.26f)
                    curveTo(3.54f, 15.66f, 4.09f, 16.98f, 5.08f, 17.97f)
                    curveTo(5.37f, 18.26f, 5.37f, 18.74f, 5.08f, 19.03f)
                    curveTo(4.79f, 19.32f, 4.31f, 19.32f, 4.02f, 19.03f)
                    curveTo(2.75f, 17.75f, 2.04f, 16.06f, 2.04f, 14.26f)
                    curveTo(2.04f, 12.46f, 2.74f, 10.76f, 4.02f, 9.49f)
                    lineTo(8.26f, 5.25f)
                    curveTo(11.28f, 2.23f, 16.2f, 2.23f, 19.22f, 5.25f)
                    curveTo(20.68f, 6.71f, 21.49f, 8.66f, 21.49f, 10.73f)
                    curveTo(21.49f, 12.8f, 20.68f, 14.75f, 19.22f, 16.21f)
                    lineTo(15.33f, 20.1f)
                    curveTo(14.5f, 20.93f, 13.42f, 21.34f, 12.33f, 21.34f)
                    close()
                }
            }
        }.build()

        return _Paperclip2!!
    }

@Suppress("ObjectPropertyName")
private var _Paperclip2: ImageVector? = null
