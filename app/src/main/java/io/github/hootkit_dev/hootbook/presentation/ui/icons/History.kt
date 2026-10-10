package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.History: ImageVector
    get() {
        if (_History != null) {
            return _History!!
        }
        _History = ImageVector.Builder(
            name = "History",
            defaultWidth = 192.dp,
            defaultHeight = 192.dp,
            viewportWidth = 192f,
            viewportHeight = 192f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF5E5E5E)),
                fillAlpha = 0f,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 19.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(53.29f, 64.93f)
                lineTo(32.19f, 76.38f)
                lineTo(24.26f, 53.37f)
            }
            path(
                fill = SolidColor(Color(0xFF5E5E5E)),
                fillAlpha = 0f,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 19.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(50.99f, 149.54f)
                arcTo(71.74f, 71.74f, 17.82f, isMoreThanHalf = true, isPositiveArc = false, 38.24f, 57.44f)
            }
            path(
                fill = SolidColor(Color(0xFF5E5E5E)),
                fillAlpha = 0f,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 19.199997f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(92f, 72f)
                lineTo(92f, 104f)
                lineTo(116f, 120f)
            }
        }.build()

        return _History!!
    }

@Suppress("ObjectPropertyName")
private var _History: ImageVector? = null
