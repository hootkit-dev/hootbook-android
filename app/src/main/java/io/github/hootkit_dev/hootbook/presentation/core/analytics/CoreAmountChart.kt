package io.github.hootkit_dev.hootbook.presentation.core.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import java.math.BigDecimal

/** Coordinates only: money is supplied by Core, never smoothed, converted or financially aggregated. */
@Composable
fun CoreAmountChart(amounts:List<BigDecimal>,modifier:Modifier=Modifier){
    if(amounts.isEmpty())return
    val color=MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(160.dp)){
        val lo=amounts.minOf{it.toFloat()};val hi=amounts.maxOf{it.toFloat()};val span=(hi-lo).takeIf{it>0}?:1f
        val points=amounts.mapIndexed{index,value->Offset(if(amounts.size==1)size.width/2 else index.toFloat()/(amounts.size-1)*size.width,size.height-(value.toFloat()-lo)/span*size.height)}
        points.zipWithNext().forEach{(a,b)->drawLine(color,a,b,strokeWidth=3.dp.toPx())};points.forEach{drawCircle(color,3.dp.toPx(),it)}
    }
}
