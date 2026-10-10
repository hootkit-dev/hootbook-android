package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Wallet3: ImageVector
    get() {
        if (_Wallet3 != null) {
            return _Wallet3!!
        }
        _Wallet3 = ImageVector.Builder(
            name = "Wallet3",
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
                    moveTo(22f, 12.62f)
                    verticalLineTo(14.68f)
                    curveTo(22f, 15.24f, 21.54f, 15.7f, 20.97f, 15.7f)
                    horizontalLineTo(19.04f)
                    curveTo(17.96f, 15.7f, 16.97f, 14.91f, 16.88f, 13.83f)
                    curveTo(16.82f, 13.2f, 17.06f, 12.61f, 17.48f, 12.2f)
                    curveTo(17.85f, 11.82f, 18.36f, 11.6f, 18.92f, 11.6f)
                    horizontalLineTo(20.97f)
                    curveTo(21.54f, 11.6f, 22f, 12.06f, 22f, 12.62f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(15.38f, 13.96f)
                    curveTo(15.29f, 12.91f, 15.67f, 11.88f, 16.43f, 11.13f)
                    curveTo(17.07f, 10.48f, 17.96f, 10.1f, 18.92f, 10.1f)
                    horizontalLineTo(19.49f)
                    curveTo(19.77f, 10.1f, 20f, 9.87f, 19.96f, 9.59f)
                    curveTo(19.69f, 7.65f, 18.01f, 6.15f, 16f, 6.15f)
                    horizontalLineTo(6f)
                    curveTo(3.79f, 6.15f, 2f, 7.94f, 2f, 10.15f)
                    verticalLineTo(17.15f)
                    curveTo(2f, 19.36f, 3.79f, 21.15f, 6f, 21.15f)
                    horizontalLineTo(16f)
                    curveTo(18.02f, 21.15f, 19.69f, 19.65f, 19.96f, 17.71f)
                    curveTo(20f, 17.43f, 19.77f, 17.2f, 19.49f, 17.2f)
                    horizontalLineTo(19.04f)
                    curveTo(17.14f, 17.2f, 15.54f, 15.78f, 15.38f, 13.96f)
                    close()
                    moveTo(13f, 11.9f)
                    horizontalLineTo(7f)
                    curveTo(6.59f, 11.9f, 6.25f, 11.57f, 6.25f, 11.15f)
                    curveTo(6.25f, 10.73f, 6.59f, 10.4f, 7f, 10.4f)
                    horizontalLineTo(13f)
                    curveTo(13.41f, 10.4f, 13.75f, 10.74f, 13.75f, 11.15f)
                    curveTo(13.75f, 11.56f, 13.41f, 11.9f, 13f, 11.9f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(14.21f, 3.98f)
                    curveTo(14.47f, 4.25f, 14.24f, 4.65f, 13.86f, 4.65f)
                    horizontalLineTo(6.03f)
                    curveTo(4.94f, 4.65f, 3.92f, 4.97f, 3.07f, 5.52f)
                    curveTo(2.68f, 5.77f, 2.15f, 5.5f, 2.34f, 5.07f)
                    curveTo(2.9f, 3.76f, 4.21f, 2.85f, 5.72f, 2.85f)
                    horizontalLineTo(11.34f)
                    curveTo(12.5f, 2.85f, 13.53f, 3.26f, 14.21f, 3.98f)
                    close()
                }
            }
        }.build()

        return _Wallet3!!
    }

@Suppress("ObjectPropertyName")
private var _Wallet3: ImageVector? = null
