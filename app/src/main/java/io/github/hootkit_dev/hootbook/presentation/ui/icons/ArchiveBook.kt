package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.ArchiveBook: ImageVector
    get() {
        if (_ArchiveBook != null) {
            return _ArchiveBook!!
        }
        _ArchiveBook = ImageVector.Builder(
            name = "ArchiveBook",
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
                    moveTo(14.93f, 2.5f)
                    verticalLineTo(8.4f)
                    curveTo(14.93f, 8.84f, 14.41f, 9.06f, 14.09f, 8.77f)
                    lineTo(12.34f, 7.16f)
                    curveTo(12.15f, 6.98f, 11.85f, 6.98f, 11.66f, 7.16f)
                    lineTo(9.91f, 8.76f)
                    curveTo(9.59f, 9.06f, 9.07f, 8.83f, 9.07f, 8.4f)
                    verticalLineTo(2.5f)
                    curveTo(9.07f, 2.22f, 9.29f, 2f, 9.57f, 2f)
                    horizontalLineTo(14.43f)
                    curveTo(14.71f, 2f, 14.93f, 2.22f, 14.93f, 2.5f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(16.98f, 2.061f)
                    curveTo(16.69f, 2.021f, 16.43f, 2.271f, 16.43f, 2.561f)
                    verticalLineTo(8.581f)
                    curveTo(16.43f, 9.341f, 15.98f, 10.031f, 15.28f, 10.341f)
                    curveTo(14.58f, 10.641f, 13.77f, 10.511f, 13.21f, 9.991f)
                    lineTo(12.34f, 9.191f)
                    curveTo(12.15f, 9.011f, 11.86f, 9.011f, 11.66f, 9.191f)
                    lineTo(10.79f, 9.991f)
                    curveTo(10.43f, 10.331f, 9.96f, 10.501f, 9.49f, 10.501f)
                    curveTo(9.23f, 10.501f, 8.97f, 10.451f, 8.72f, 10.341f)
                    curveTo(8.02f, 10.031f, 7.57f, 9.341f, 7.57f, 8.581f)
                    verticalLineTo(2.561f)
                    curveTo(7.57f, 2.271f, 7.31f, 2.021f, 7.02f, 2.061f)
                    curveTo(4.22f, 2.411f, 3f, 4.301f, 3f, 7.001f)
                    verticalLineTo(17.001f)
                    curveTo(3f, 20.001f, 4.5f, 22.001f, 8f, 22.001f)
                    horizontalLineTo(16f)
                    curveTo(19.5f, 22.001f, 21f, 20.001f, 21f, 17.001f)
                    verticalLineTo(7.001f)
                    curveTo(21f, 4.301f, 19.78f, 2.411f, 16.98f, 2.061f)
                    close()
                    moveTo(17.5f, 18.751f)
                    horizontalLineTo(9f)
                    curveTo(8.59f, 18.751f, 8.25f, 18.411f, 8.25f, 18.001f)
                    curveTo(8.25f, 17.591f, 8.59f, 17.251f, 9f, 17.251f)
                    horizontalLineTo(17.5f)
                    curveTo(17.91f, 17.251f, 18.25f, 17.591f, 18.25f, 18.001f)
                    curveTo(18.25f, 18.411f, 17.91f, 18.751f, 17.5f, 18.751f)
                    close()
                    moveTo(17.5f, 14.751f)
                    horizontalLineTo(13.25f)
                    curveTo(12.84f, 14.751f, 12.5f, 14.411f, 12.5f, 14.001f)
                    curveTo(12.5f, 13.591f, 12.84f, 13.251f, 13.25f, 13.251f)
                    horizontalLineTo(17.5f)
                    curveTo(17.91f, 13.251f, 18.25f, 13.591f, 18.25f, 14.001f)
                    curveTo(18.25f, 14.411f, 17.91f, 14.751f, 17.5f, 14.751f)
                    close()
                }
            }
        }.build()

        return _ArchiveBook!!
    }

@Suppress("ObjectPropertyName")
private var _ArchiveBook: ImageVector? = null
