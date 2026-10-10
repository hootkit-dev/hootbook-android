package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Sync: ImageVector
    get() {
        if (_Sync != null) {
            return _Sync!!
        }
        _Sync = ImageVector.Builder(
            name = "Sync",
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
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(31.18f, 118.3f)
                arcTo(68.58f, 68.58f, 318.16f, isMoreThanHalf = false, isPositiveArc = true, 126.55f, 32.92f)
            }
            path(
                fill = SolidColor(Color(0xFF5E5E5E)),
                fillAlpha = 0f,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 19.2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(58.21f, 154.8f)
                arcTo(68.58f, 68.58f, 321.32f, isMoreThanHalf = false, isPositiveArc = false, 163.14f, 81.05f)
            }
            path(
                fill = SolidColor(Color(0xFF5E5E5E)),
                fillAlpha = 0f,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 19.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(65.52f, 141.95f)
                lineTo(41.95f, 146.48f)
                lineTo(50.41f, 169.29f)
            }
            path(
                fill = SolidColor(Color(0xFF5E5E5E)),
                fillAlpha = 0f,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 19.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(118.93f, 47.31f)
                lineTo(142.69f, 50.72f)
                lineTo(142.14f, 26.39f)
            }
        }.build()

        return _Sync!!
    }

@Suppress("ObjectPropertyName")
private var _Sync: ImageVector? = null
