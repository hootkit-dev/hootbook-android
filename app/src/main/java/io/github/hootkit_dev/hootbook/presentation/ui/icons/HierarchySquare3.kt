package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.HierarchySquare3: ImageVector
    get() {
        if (_HierarchySquare3 != null) {
            return _HierarchySquare3!!
        }
        _HierarchySquare3 = ImageVector.Builder(
            name = "HierarchySquare3",
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
                    moveTo(16.19f, 2f)
                    horizontalLineTo(7.81f)
                    curveTo(4.17f, 2f, 2f, 4.17f, 2f, 7.81f)
                    verticalLineTo(16.18f)
                    curveTo(2f, 19.83f, 4.17f, 22f, 7.81f, 22f)
                    horizontalLineTo(16.18f)
                    curveTo(19.82f, 22f, 21.99f, 19.83f, 21.99f, 16.19f)
                    verticalLineTo(7.81f)
                    curveTo(22f, 4.17f, 19.83f, 2f, 16.19f, 2f)
                    close()
                    moveTo(16.41f, 9.83f)
                    curveTo(15.8f, 11.57f, 14.16f, 12.74f, 12.32f, 12.74f)
                    curveTo(12.31f, 12.74f, 12.31f, 12.74f, 12.3f, 12.74f)
                    lineTo(10.24f, 12.73f)
                    curveTo(10.24f, 12.73f, 10.24f, 12.73f, 10.23f, 12.73f)
                    curveTo(9.47f, 12.73f, 8.81f, 13.24f, 8.61f, 13.97f)
                    curveTo(9.5f, 14.25f, 10.15f, 15.08f, 10.15f, 16.06f)
                    curveTo(10.15f, 17.27f, 9.16f, 18.26f, 7.95f, 18.26f)
                    curveTo(6.74f, 18.26f, 5.75f, 17.27f, 5.75f, 16.06f)
                    curveTo(5.75f, 15.17f, 6.29f, 14.4f, 7.05f, 14.06f)
                    verticalLineTo(9.7f)
                    curveTo(6.29f, 9.4f, 5.75f, 8.66f, 5.75f, 7.8f)
                    curveTo(5.75f, 6.67f, 6.67f, 5.75f, 7.8f, 5.75f)
                    curveTo(8.93f, 5.75f, 9.85f, 6.67f, 9.85f, 7.8f)
                    curveTo(9.85f, 8.67f, 9.31f, 9.4f, 8.55f, 9.7f)
                    verticalLineTo(11.72f)
                    curveTo(9.04f, 11.41f, 9.62f, 11.23f, 10.23f, 11.23f)
                    horizontalLineTo(10.24f)
                    lineTo(12.3f, 11.24f)
                    curveTo(13.48f, 11.28f, 14.53f, 10.52f, 14.95f, 9.42f)
                    curveTo(14.46f, 9.04f, 14.14f, 8.46f, 14.14f, 7.8f)
                    curveTo(14.14f, 6.67f, 15.06f, 5.75f, 16.19f, 5.75f)
                    curveTo(17.32f, 5.75f, 18.24f, 6.67f, 18.24f, 7.8f)
                    curveTo(18.25f, 8.86f, 17.44f, 9.72f, 16.41f, 9.83f)
                    close()
                }
            }
        }.build()

        return _HierarchySquare3!!
    }

@Suppress("ObjectPropertyName")
private var _HierarchySquare3: ImageVector? = null
