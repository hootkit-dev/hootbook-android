package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Balance: ImageVector
    get() {
        if (_Balance != null) {
            return _Balance!!
        }
        _Balance = ImageVector.Builder(
            name = "Balance",
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
                moveTo(141.45f, 64.93f)
                lineTo(162.55f, 76.38f)
                lineTo(170.48f, 53.37f)
            }
            path(
                fill = SolidColor(Color(0xFF5E5E5E)),
                fillAlpha = 0f,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 19.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(143.75f, 149.54f)
                arcTo(71.74f, 71.74f, 342.18f, isMoreThanHalf = true, isPositiveArc = true, 156.5f, 57.44f)
            }
            path(
                fill = SolidColor(Color(0xFF5E5E5E)),
                fillAlpha = 0f,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 19.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(88f, 72f)
                lineTo(88f, 104f)
                lineTo(112f, 120f)
            }
        }.build()

        return _Balance!!
    }

@Suppress("ObjectPropertyName")
private var _Balance: ImageVector? = null
