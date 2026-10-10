package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.WalletMinus: ImageVector
    get() {
        if (_WalletMinus != null) {
            return _WalletMinus!!
        }
        _WalletMinus = ImageVector.Builder(
            name = "WalletMinus",
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
                    moveTo(11.94f, 2.21f)
                    lineTo(9.53f, 7.82f)
                    horizontalLineTo(7.12f)
                    curveTo(6.72f, 7.82f, 6.33f, 7.85f, 5.95f, 7.93f)
                    lineTo(6.95f, 5.53f)
                    lineTo(6.99f, 5.44f)
                    lineTo(7.05f, 5.28f)
                    curveTo(7.08f, 5.21f, 7.1f, 5.15f, 7.13f, 5.1f)
                    curveTo(8.29f, 2.41f, 9.59f, 1.57f, 11.94f, 2.21f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(18.73f, 8.09f)
                    lineTo(18.71f, 8.08f)
                    curveTo(18.11f, 7.91f, 17.5f, 7.82f, 16.88f, 7.82f)
                    horizontalLineTo(10.62f)
                    lineTo(12.87f, 2.59f)
                    lineTo(12.9f, 2.52f)
                    curveTo(13.04f, 2.57f, 13.19f, 2.64f, 13.34f, 2.69f)
                    lineTo(15.55f, 3.62f)
                    curveTo(16.78f, 4.13f, 17.64f, 4.66f, 18.17f, 5.3f)
                    curveTo(18.26f, 5.42f, 18.34f, 5.53f, 18.42f, 5.66f)
                    curveTo(18.51f, 5.8f, 18.58f, 5.94f, 18.62f, 6.09f)
                    curveTo(18.66f, 6.18f, 18.69f, 6.26f, 18.71f, 6.35f)
                    curveTo(18.86f, 6.86f, 18.87f, 7.44f, 18.73f, 8.09f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(18.29f, 9.52f)
                    curveTo(17.84f, 9.39f, 17.37f, 9.32f, 16.88f, 9.32f)
                    horizontalLineTo(7.12f)
                    curveTo(6.44f, 9.32f, 5.8f, 9.45f, 5.2f, 9.71f)
                    curveTo(3.46f, 10.46f, 2.24f, 12.19f, 2.24f, 14.2f)
                    verticalLineTo(16.15f)
                    curveTo(2.24f, 16.39f, 2.26f, 16.62f, 2.29f, 16.86f)
                    curveTo(2.51f, 20.04f, 4.21f, 21.74f, 7.39f, 21.95f)
                    curveTo(7.62f, 21.98f, 7.85f, 22f, 8.1f, 22f)
                    horizontalLineTo(15.9f)
                    curveTo(19.6f, 22f, 21.55f, 20.24f, 21.74f, 16.74f)
                    curveTo(21.75f, 16.55f, 21.76f, 16.35f, 21.76f, 16.15f)
                    verticalLineTo(14.2f)
                    curveTo(21.76f, 11.99f, 20.29f, 10.13f, 18.29f, 9.52f)
                    close()
                    moveTo(14.5f, 16.75f)
                    horizontalLineTo(9.5f)
                    curveTo(9.09f, 16.75f, 8.75f, 16.41f, 8.75f, 16f)
                    curveTo(8.75f, 15.59f, 9.09f, 15.25f, 9.5f, 15.25f)
                    horizontalLineTo(14.5f)
                    curveTo(14.91f, 15.25f, 15.25f, 15.59f, 15.25f, 16f)
                    curveTo(15.25f, 16.41f, 14.91f, 16.75f, 14.5f, 16.75f)
                    close()
                }
            }
        }.build()

        return _WalletMinus!!
    }

@Suppress("ObjectPropertyName")
private var _WalletMinus: ImageVector? = null
