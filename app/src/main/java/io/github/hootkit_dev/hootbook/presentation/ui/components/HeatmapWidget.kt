package io.github.hootkit_dev.hootbook.presentation.ui.components

import io.github.hootkit_dev.hootbook.utils.DateFormats

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val cellSize = 14.dp
private val cellGap = 4.dp
private val cellStep = cellSize + cellGap

@OptIn(ExperimentalHazeApi::class)
@Composable
fun HeatmapWidget(
    modifier: Modifier = Modifier,
    data: Map<LocalDate, Int>,
    blurEffects: Boolean,
    hazeState: HazeState = remember { HazeState() }
) {
    val weeksToShow = 26 // Show last 6 months
    val today = LocalDate.now()
    val endDate = today
    // Start from the Monday of N weeks ago
    val startDate = endDate.minusWeeks((weeksToShow - 1).toLong()).with(DayOfWeek.MONDAY)
    
    val monthLabels = remember(startDate, endDate) {
        val allMonthStarts = mutableListOf<Pair<Int, String>>()
        var current = startDate
        var lastMonth = -1
        var weekIndex = 0
        
        while (current <= endDate) {
            if (current.monthValue != lastMonth) {
                val formatter = DateFormats.monthFormatter()
                allMonthStarts.add(weekIndex to current.format(formatter))
                lastMonth = current.monthValue
            }
            current = current.plusWeeks(1)
            weekIndex++
        }

        val filteredLabels = mutableListOf<Pair<Int, String>>()
        for (i in allMonthStarts.indices) {
            val (week, label) = allMonthStarts[i]
            
            // case for the first label: skip it if it's too close to the second one
            if (i == 0 && allMonthStarts.size > 1) {
                val nextWeek = allMonthStarts[1].first
                if (nextWeek - week < 4) continue
            }
            
            //ensure at least 4 weeks between labels
            if (filteredLabels.isEmpty()) {
                filteredLabels.add(week to label)
            } else {
                val lastAddedWeek = filteredLabels.last().first
                if (week - lastAddedWeek >= 4) {
                    filteredLabels.add(week to label)
                }
            }
        }
        filteredLabels
    }

    // Start at the end (latest weeks): the value is clamped to the scroll range on first
    // layout, so no extra frame scrolls there afterwards.
    val scrollState = rememberScrollState(Int.MAX_VALUE)

    // One level per cell (-1 future, 0 none, 1-4 more transactions), recomputed only when the
    // data changes rather than on every composition.
    val levels = remember(data, startDate, today) {
        IntArray(weeksToShow * 7) { index ->
            val date = startDate.plusDays(index.toLong())
            val count = data[date] ?: 0
            when {
                date > today -> -1
                count == 0 -> 0
                count == 1 -> 1
                count < 3 -> 2
                count < 5 -> 3
                else -> 4
            }
        }
    }

    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(Dimensions.Radius.lg))
            .then(
                if (blurEffects) Modifier.hazeEffect(
                    state = hazeState,
                    block = fun HazeEffectScope.() {
                        inputScale = HazeInputScale.Auto
                        style = HazeDefaults.style(
                            backgroundColor = Color.Transparent,
                            tint = HazeDefaults.tint(containerColor),
                            blurRadius = 20.dp,
                            noiseFactor = -1f,
                        )
                        blurredEdgeTreatment = BlurredEdgeTreatment.Unbounded
                    }
                ) else Modifier
            ),
        shape = RoundedCornerShape(Dimensions.Radius.lg),
        color = if (blurEffects) MaterialTheme.colorScheme.surfaceContainerLow.copy(0.5f)
            else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .padding(Dimensions.Padding.content)
                .horizontalScroll(scrollState)
        ) {
            // Heatmap grid, drawn in one Canvas: 182 cells as separate composables made the
            // home list stutter each time this item scrolled into view.
            val empty = MaterialTheme.colorScheme.surfaceContainerHigh
            val primary = MaterialTheme.colorScheme.primary
            val palette = remember(empty, primary) {
                listOf(empty, primary.copy(alpha = 0.25f), primary.copy(alpha = 0.5f), primary.copy(alpha = 0.75f), primary)
            }
            Canvas(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .size(width = cellStep * weeksToShow - cellGap, height = cellStep * 7 - cellGap)
            ) {
                val cell = cellSize.toPx()
                val step = cellStep.toPx()
                val corner = CornerRadius(4.dp.toPx())
                for (index in levels.indices) {
                    val level = levels[index]
                    drawRoundRect(
                        color = palette[if (level < 0) 0 else level],
                        topLeft = Offset((index / 7) * step, (index % 7) * step),
                        size = Size(cell, cell),
                        cornerRadius = corner
                    )
                }
            }

            // Labels (Months)
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                monthLabels.forEach { (weekIndex, label) ->
                    // Under its first week; a month starting in the last weeks ends at the grid's
                    // edge instead, so its label is not cut off
                    val atEnd = weekIndex >= weeksToShow - 2
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        softWrap = false,
                        modifier = if (atEnd) Modifier.align(androidx.compose.ui.Alignment.TopEnd)
                            else Modifier.offset(x = cellStep * weekIndex)
                    )
                }
            }
        }
    }
}
