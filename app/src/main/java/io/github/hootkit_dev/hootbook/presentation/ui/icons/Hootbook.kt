package io.github.hootkit_dev.hootbook.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

val Iconax.Hootbook: ImageVector
    get() {
        if (_Hootbook != null) {
            return _Hootbook!!
        }
        _Hootbook = ImageVector.Builder(
            name = "Hootbook",
            defaultWidth = 192.dp,
            defaultHeight = 192.dp,
            viewportWidth = 192f,
            viewportHeight = 192f
        ).apply {
            addPath(
                pathData = addPathNodes(HOOTBOOK_MARK),
                pathFillType = PathFillType.EvenOdd,
                fill = SolidColor(Color.White),
            )
        }.build()

        return _Hootbook!!
    }

@Suppress("ObjectPropertyName")
private var _Hootbook: ImageVector? = null

/** The Hootbook mark as one even-odd path: owl (eyes and beak cut out, pupils filled) on an open book. */
internal const val HOOTBOOK_MARK =
    "M54,28C61,38 69,45 78,49C90,45 102,45 114,49C123,45 131,38 138,28C148,46 152,66 150,92C148,120 128,138 96,138C64,138 44,120 42,92C40,66 44,46 54,28ZM54,84a20,20 0 1,0 40,0a20,20 0 1,0 -40,0ZM98,84a20,20 0 1,0 40,0a20,20 0 1,0 -40,0ZM89,100L103,100L96,112ZM67,86a9,9 0 1,0 18,0a9,9 0 1,0 -18,0ZM107,86a9,9 0 1,0 18,0a9,9 0 1,0 -18,0ZM38,150C58,140 80,140 96,150C112,140 134,140 154,150L154,166C134,156 112,156 96,166C80,156 58,156 38,166Z"
