package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.RefreshArrow01: ImageVector
    get() {
        if (_RefreshArrow01 != null) {
            return _RefreshArrow01!!
        }
        _RefreshArrow01 = ImageVector.Builder(
            name = "RefreshArrow01",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(22f, 12f)
                curveTo(22f, 17.52f, 17.52f, 22f, 12f, 22f)
                curveTo(6.48f, 22f, 3.11f, 16.44f, 3.11f, 16.44f)
                moveTo(3.11f, 16.44f)
                horizontalLineTo(7.63f)
                moveTo(3.11f, 16.44f)
                verticalLineTo(21.44f)
                moveTo(2f, 12f)
                curveTo(2f, 6.48f, 6.44f, 2f, 12f, 2f)
                curveTo(18.67f, 2f, 22f, 7.56f, 22f, 7.56f)
                moveTo(22f, 7.56f)
                verticalLineTo(2.56f)
                moveTo(22f, 7.56f)
                horizontalLineTo(17.56f)
            }
        }.build()

        return _RefreshArrow01!!
    }

@Suppress("ObjectPropertyName")
private var _RefreshArrow01: ImageVector? = null
