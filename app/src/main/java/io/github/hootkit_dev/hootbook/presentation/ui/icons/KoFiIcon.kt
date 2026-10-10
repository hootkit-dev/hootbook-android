package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.KoFiIcon: ImageVector
    get() {
        if (_KoFiIcon != null) {
            return _KoFiIcon!!
        }
        _KoFiIcon = ImageVector.Builder(
            name = "KoFiIcon",
            defaultWidth = 504.36.dp,
            defaultHeight = 504.36.dp,
            viewportWidth = 504.36f,
            viewportHeight = 504.36f
        ).apply {
            path(fill = SolidColor(Color(0xFF00B9FE))) {
                moveTo(252.18f, 252.18f)
                moveToRelative(-252.18f, 0f)
                arcToRelative(252.18f, 252.18f, 0f, isMoreThanHalf = true, isPositiveArc = true, 504.36f, 0f)
                arcToRelative(252.18f, 252.18f, 0f, isMoreThanHalf = true, isPositiveArc = true, -504.36f, 0f)
            }
            path(
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.14f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(380.19f, 276.5f)
                arcTo(196.26f, 196.26f, 0f, isMoreThanHalf = false, isPositiveArc = true, 352f, 277.78f)
                lineTo(352f, 185.62f)
                horizontalLineToRelative(19.2f)
                arcToRelative(38.37f, 38.37f, 0f, isMoreThanHalf = false, isPositiveArc = true, 32f, 15.36f)
                arcToRelative(45.65f, 45.65f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10.24f, 29.44f)
                arcTo(42.87f, 42.87f, 0f, isMoreThanHalf = false, isPositiveArc = true, 380.19f, 276.5f)
                close()
                moveTo(459.56f, 212.5f)
                arcToRelative(83.86f, 83.86f, 0f, isMoreThanHalf = false, isPositiveArc = false, -37.13f, -57.61f)
                arcTo(98.23f, 98.23f, 0f, isMoreThanHalf = false, isPositiveArc = false, 366.11f, 137f)
                lineTo(84.49f, 137f)
                arcToRelative(16.37f, 16.37f, 0f, isMoreThanHalf = false, isPositiveArc = false, -14.08f, 15.36f)
                verticalLineToRelative(3.84f)
                reflectiveCurveToRelative(-1.28f, 124.17f, 1.28f, 192f)
                arcToRelative(42.11f, 42.11f, 0f, isMoreThanHalf = false, isPositiveArc = false, 42.24f, 39.68f)
                reflectiveCurveToRelative(129.29f, 0f, 190.73f, -1.28f)
                horizontalLineToRelative(9f)
                curveToRelative(35.84f, -9f, 38.4f, -42.24f, 38.4f, -60.16f)
                curveTo(422.43f, 329f, 472.36f, 279.06f, 459.56f, 212.5f)
                close()
            }
            path(fill = SolidColor(Color(0xFFFF5E5B))) {
                moveTo(208.66f, 334.11f)
                curveToRelative(3.84f, 1.28f, 5.12f, 0f, 5.12f, 0f)
                reflectiveCurveToRelative(44.8f, -41f, 65.28f, -65.29f)
                curveToRelative(17.92f, -21.76f, 19.2f, -56.32f, -11.52f, -70.4f)
                reflectiveCurveToRelative(-56.32f, 15.36f, -56.32f, 15.36f)
                arcToRelative(50.44f, 50.44f, 0f, isMoreThanHalf = false, isPositiveArc = false, -70.41f, -7.68f)
                lineToRelative(-1.28f, 1.28f)
                curveToRelative(-15.36f, 16.64f, -10.24f, 44.8f, 1.28f, 60.16f)
                arcToRelative(771.87f, 771.87f, 0f, isMoreThanHalf = false, isPositiveArc = false, 65.29f, 64f)
                close()
            }
            path(
                fill = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFFFF4EA3),
                        1f to Color(0xFFFF5E5B)
                    ),
                    start = Offset(167.86f, 99.71f),
                    end = Offset(220.98f, 262.79f)
                )
            ) {
                moveTo(211.22f, 335.39f)
                arcToRelative(4.75f, 4.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, 3.84f, -1.28f)
                reflectiveCurveToRelative(44.8f, -41f, 65.28f, -65.29f)
                curveToRelative(17.92f, -21.76f, 19.2f, -56.32f, -11.52f, -70.4f)
                reflectiveCurveToRelative(-56.32f, 15.36f, -56.32f, 15.36f)
                arcToRelative(50.44f, 50.44f, 0f, isMoreThanHalf = false, isPositiveArc = false, -70.41f, -7.68f)
                lineToRelative(-1.28f, 1.28f)
                curveToRelative(-15.36f, 16.64f, -10.24f, 44.8f, 1.28f, 60.16f)
                arcToRelative(799.58f, 799.58f, 0f, isMoreThanHalf = false, isPositiveArc = false, 66.57f, 65.29f)
                curveTo(208.66f, 335.39f, 209.94f, 335.39f, 211.22f, 335.39f)
                close()
            }
        }.build()

        return _KoFiIcon!!
    }

@Suppress("ObjectPropertyName")
private var _KoFiIcon: ImageVector? = null
