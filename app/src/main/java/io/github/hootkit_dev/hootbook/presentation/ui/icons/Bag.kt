package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Bag: ImageVector
    get() {
        if (_Bag != null) {
            return _Bag!!
        }
        _Bag = ImageVector.Builder(
            name = "Bag",
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
                    moveTo(19.24f, 5.581f)
                    horizontalLineTo(18.84f)
                    lineTo(15.46f, 2.201f)
                    curveTo(15.19f, 1.931f, 14.75f, 1.931f, 14.47f, 2.201f)
                    curveTo(14.2f, 2.471f, 14.2f, 2.911f, 14.47f, 3.191f)
                    lineTo(16.86f, 5.581f)
                    horizontalLineTo(7.14f)
                    lineTo(9.53f, 3.191f)
                    curveTo(9.8f, 2.921f, 9.8f, 2.481f, 9.53f, 2.201f)
                    curveTo(9.26f, 1.931f, 8.82f, 1.931f, 8.54f, 2.201f)
                    lineTo(5.17f, 5.581f)
                    horizontalLineTo(4.77f)
                    curveTo(3.87f, 5.581f, 2f, 5.581f, 2f, 8.141f)
                    curveTo(2f, 9.111f, 2.2f, 9.751f, 2.62f, 10.17f)
                    curveTo(2.86f, 10.42f, 3.15f, 10.55f, 3.46f, 10.62f)
                    curveTo(3.75f, 10.691f, 4.06f, 10.7f, 4.36f, 10.7f)
                    horizontalLineTo(19.64f)
                    curveTo(19.95f, 10.7f, 20.24f, 10.681f, 20.52f, 10.62f)
                    curveTo(21.36f, 10.42f, 22f, 9.821f, 22f, 8.141f)
                    curveTo(22f, 5.581f, 20.13f, 5.581f, 19.24f, 5.581f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(19.05f, 12f)
                    horizontalLineTo(4.87f)
                    curveTo(4.25f, 12f, 3.78f, 12.55f, 3.88f, 13.16f)
                    lineTo(4.72f, 18.3f)
                    curveTo(5f, 20.02f, 5.75f, 22f, 9.08f, 22f)
                    horizontalLineTo(14.69f)
                    curveTo(18.06f, 22f, 18.66f, 20.31f, 19.02f, 18.42f)
                    lineTo(20.03f, 13.19f)
                    curveTo(20.15f, 12.57f, 19.68f, 12f, 19.05f, 12f)
                    close()
                    moveTo(10.61f, 18.45f)
                    curveTo(10.61f, 18.84f, 10.3f, 19.15f, 9.92f, 19.15f)
                    curveTo(9.53f, 19.15f, 9.22f, 18.84f, 9.22f, 18.45f)
                    verticalLineTo(15.15f)
                    curveTo(9.22f, 14.77f, 9.53f, 14.45f, 9.92f, 14.45f)
                    curveTo(10.3f, 14.45f, 10.61f, 14.77f, 10.61f, 15.15f)
                    verticalLineTo(18.45f)
                    close()
                    moveTo(14.89f, 18.45f)
                    curveTo(14.89f, 18.84f, 14.58f, 19.15f, 14.19f, 19.15f)
                    curveTo(13.81f, 19.15f, 13.49f, 18.84f, 13.49f, 18.45f)
                    verticalLineTo(15.15f)
                    curveTo(13.49f, 14.77f, 13.81f, 14.45f, 14.19f, 14.45f)
                    curveTo(14.58f, 14.45f, 14.89f, 14.77f, 14.89f, 15.15f)
                    verticalLineTo(18.45f)
                    close()
                }
            }
        }.build()

        return _Bag!!
    }

@Suppress("ObjectPropertyName")
private var _Bag: ImageVector? = null
