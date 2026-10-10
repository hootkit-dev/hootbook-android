package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Edit2: ImageVector
    get() {
        if (_Edit2 != null) {
            return _Edit2!!
        }
        _Edit2 = ImageVector.Builder(
            name = "Edit2",
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
                    moveTo(21f, 22f)
                    horizontalLineTo(3f)
                    curveTo(2.59f, 22f, 2.25f, 21.66f, 2.25f, 21.25f)
                    curveTo(2.25f, 20.84f, 2.59f, 20.5f, 3f, 20.5f)
                    horizontalLineTo(21f)
                    curveTo(21.41f, 20.5f, 21.75f, 20.84f, 21.75f, 21.25f)
                    curveTo(21.75f, 21.66f, 21.41f, 22f, 21f, 22f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(19.02f, 3.48f)
                    curveTo(17.08f, 1.54f, 15.18f, 1.49f, 13.19f, 3.48f)
                    lineTo(11.98f, 4.69f)
                    curveTo(11.88f, 4.79f, 11.84f, 4.95f, 11.88f, 5.09f)
                    curveTo(12.64f, 7.74f, 14.76f, 9.86f, 17.41f, 10.62f)
                    curveTo(17.45f, 10.63f, 17.49f, 10.64f, 17.53f, 10.64f)
                    curveTo(17.64f, 10.64f, 17.74f, 10.6f, 17.82f, 10.52f)
                    lineTo(19.02f, 9.31f)
                    curveTo(20.01f, 8.33f, 20.49f, 7.38f, 20.49f, 6.42f)
                    curveTo(20.5f, 5.43f, 20.02f, 4.47f, 19.02f, 3.48f)
                    close()
                }
                path(fill = SolidColor(Color.White)) {
                    moveTo(15.61f, 11.531f)
                    curveTo(15.32f, 11.391f, 15.04f, 11.251f, 14.77f, 11.091f)
                    curveTo(14.55f, 10.961f, 14.34f, 10.821f, 14.13f, 10.671f)
                    curveTo(13.96f, 10.561f, 13.76f, 10.401f, 13.57f, 10.241f)
                    curveTo(13.55f, 10.231f, 13.48f, 10.171f, 13.4f, 10.091f)
                    curveTo(13.07f, 9.811f, 12.7f, 9.451f, 12.37f, 9.051f)
                    curveTo(12.34f, 9.031f, 12.29f, 8.961f, 12.22f, 8.871f)
                    curveTo(12.12f, 8.751f, 11.95f, 8.551f, 11.8f, 8.321f)
                    curveTo(11.68f, 8.171f, 11.54f, 7.951f, 11.41f, 7.731f)
                    curveTo(11.25f, 7.461f, 11.11f, 7.191f, 10.97f, 6.911f)
                    curveTo(10.949f, 6.865f, 10.928f, 6.82f, 10.908f, 6.775f)
                    curveTo(10.761f, 6.442f, 10.326f, 6.345f, 10.068f, 6.602f)
                    lineTo(4.34f, 12.331f)
                    curveTo(4.21f, 12.461f, 4.09f, 12.711f, 4.06f, 12.881f)
                    lineTo(3.52f, 16.711f)
                    curveTo(3.42f, 17.391f, 3.61f, 18.031f, 4.03f, 18.461f)
                    curveTo(4.39f, 18.811f, 4.89f, 19.001f, 5.43f, 19.001f)
                    curveTo(5.55f, 19.001f, 5.67f, 18.991f, 5.79f, 18.971f)
                    lineTo(9.63f, 18.431f)
                    curveTo(9.81f, 18.401f, 10.06f, 18.281f, 10.18f, 18.151f)
                    lineTo(15.901f, 12.429f)
                    curveTo(16.161f, 12.17f, 16.063f, 11.724f, 15.725f, 11.58f)
                    curveTo(15.687f, 11.564f, 15.649f, 11.548f, 15.61f, 11.531f)
                    close()
                }
            }
        }.build()

        return _Edit2!!
    }

@Suppress("ObjectPropertyName")
private var _Edit2: ImageVector? = null
