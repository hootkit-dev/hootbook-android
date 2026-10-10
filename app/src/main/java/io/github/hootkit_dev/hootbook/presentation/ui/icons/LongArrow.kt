package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.LongArrow: ImageVector
    get() {
        if (_LongArrow != null) {
            return _LongArrow!!
        }
        _LongArrow = ImageVector.Builder(
            name = "LongArrow",
            defaultWidth = 192.dp,
            defaultHeight = 192.dp,
            viewportWidth = 192f,
            viewportHeight = 192f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 0f,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 26.880001f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(24f, 96f)
                lineTo(100f, 128f)
                lineTo(168f, 96f)
            }
        }.build()

        return _LongArrow!!
    }

@Suppress("ObjectPropertyName")
private var _LongArrow: ImageVector? = null
