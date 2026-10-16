package io.github.hootkit_dev.hootbook.presentation.ui.features.budgets

import io.github.hootkit_dev.hootbook.utils.DateFormats

import io.github.hootkit_dev.hootbook.presentation.ui.theme.MotionDurations

import androidx.compose.animation.core.FastOutSlowInEasing

import androidx.compose.animation.core.tween

import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hootkit_dev.hootbook.presentation.ui.components.CategoryIcon
import io.github.hootkit_dev.hootbook.presentation.effects.BlurredAnimatedVisibility
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.common.icons.CategoryMapping
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R
import ir.ehsannarmani.compose_charts.ColumnChart
import ir.ehsannarmani.compose_charts.LineChart
import ir.ehsannarmani.compose_charts.PieChart
import ir.ehsannarmani.compose_charts.models.AnimationMode
import ir.ehsannarmani.compose_charts.models.BarProperties
import ir.ehsannarmani.compose_charts.models.Bars
import ir.ehsannarmani.compose_charts.models.DividerProperties
import ir.ehsannarmani.compose_charts.models.DotProperties
import ir.ehsannarmani.compose_charts.models.DrawStyle
import ir.ehsannarmani.compose_charts.models.GridProperties
import ir.ehsannarmani.compose_charts.models.HorizontalIndicatorProperties
import ir.ehsannarmani.compose_charts.models.LabelHelperProperties
import ir.ehsannarmani.compose_charts.models.LabelProperties
import ir.ehsannarmani.compose_charts.models.Line
import ir.ehsannarmani.compose_charts.models.LineProperties
import ir.ehsannarmani.compose_charts.models.Pie
import ir.ehsannarmani.compose_charts.models.StrokeStyle
import ir.ehsannarmani.compose_charts.models.ZeroLineProperties
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import kotlin.math.abs

import dev.hootkit.core.*
import androidx.core.graphics.toColorInt
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation

@Composable
fun BudgetHistoryChart(
    data: List<BudgetPeriodSummary>,
    currency: String,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return
    val themeColors = MaterialTheme.colorScheme
    val totalLabel = stringResource(R.string.total)

    val cashFlowData = remember(data) { data.map { it.spent.toDouble() } }
    val labels = remember(data) {
        val isYearly = data.size > 1 && data.all { it.start.javaBudgetDate().dayOfYear == 1 }
        val isMonthly = !isYearly && data.all { it.start.javaBudgetDate().dayOfMonth == 1 }
        val spansMultipleYears = if (data.isNotEmpty()) {
            data.first().start.javaBudgetDate().year != data.last().start.javaBudgetDate().year
        } else false

        data.map {
            val date = it.start.javaBudgetDate()
            when {
                isYearly -> date.format(DateTimeFormatter.ofPattern("yyyy"))
                isMonthly && spansMultipleYears -> date.format(DateFormats.shortYearMonthFormatter())
                isMonthly -> date.format(DateFormats.monthFormatter())
                else -> date.format(DateFormats.monthDayFormatter())
            }
        }
    }

    LineChart(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .padding(vertical = Spacing.md),
        data = listOf(
            Line(
                label = totalLabel,
                values = cashFlowData,
                color = SolidColor(themeColors.primary),
                firstGradientFillColor = themeColors.primary.copy(alpha = 0.3f),
                secondGradientFillColor = Color.Transparent,
                strokeAnimationSpec = tween(1500, easing = EaseInOutCubic),
                gradientAnimationDelay = 750,
                drawStyle = DrawStyle.Stroke(width = 2.dp),
                curvedEdges = true,
                dotProperties = DotProperties(
                    enabled = true,
                    color = SolidColor(themeColors.primary),
                    strokeWidth = 3.dp,
                    radius = 4.dp,
                    strokeColor = SolidColor(themeColors.surface)
                )
            )
        ),
        dividerProperties = DividerProperties(
            enabled = true,
            xAxisProperties = LineProperties(
                color = SolidColor(themeColors.onSurface.copy(alpha = 0f)),
                thickness = 0.dp
            ),
            yAxisProperties = LineProperties(
                color = SolidColor(themeColors.onSurface.copy(alpha = 0f)),
                thickness = 0.dp
            )
        ),
        indicatorProperties = HorizontalIndicatorProperties(
            enabled = true,
            textStyle = TextStyle.Default.copy(
                fontSize = 10.sp,
                color = themeColors.onSurfaceVariant.copy(0.6f),
                textAlign = TextAlign.Center
            ),
            contentBuilder = { value -> CoreMoneyPresentation.formatCurrency(value, currency) }
        ),
        labelHelperProperties = LabelHelperProperties(
            enabled = true,
            textStyle = TextStyle.Default.copy(
                fontSize = 10.sp,
                color = themeColors.onSurface,
                textAlign = TextAlign.End
            ),
        ),
        labelProperties = LabelProperties(
            enabled = true,
            textStyle = TextStyle.Default.copy(
                fontSize = 10.sp,
                color = themeColors.onSurface.copy(0.6f),
                textAlign = TextAlign.End
            ),
            labels = labels,
            padding = 16.dp,
            rotation = LabelProperties.Rotation(
                mode = LabelProperties.Rotation.Mode.Force,
                degree = -45f
            )
        ),
        zeroLineProperties = ZeroLineProperties(
            enabled = true,
            style = StrokeStyle.Dashed(),
            color = SolidColor(themeColors.onSurface.copy(alpha = 0.1f)),
        ),
        gridProperties = GridProperties(
            enabled = true,
            xAxisProperties = GridProperties.AxisProperties(
                enabled = true,
                style = StrokeStyle.Dashed(),
                color = SolidColor(themeColors.onSurface.copy(alpha = 0.1f))
            ),
            yAxisProperties = GridProperties.AxisProperties(
                enabled = true,
                style = StrokeStyle.Dashed(),
                color = SolidColor(themeColors.onSurface.copy(alpha = 0.1f))
            )
        ),
        animationMode = AnimationMode.Together(delayBuilder = { it * 200L }),
    )
}


@Composable
fun BudgetCategoryPieChart(
    categories: List<CategorySpend>,
    lookup: Map<String, Category>,
    currency: String,
    totalSpent: java.math.BigDecimal,
    selectedCategoryId: String? = null,
    onCategoryClick: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (categories.isEmpty()) return

    val total = totalSpent.toDouble()
    if (total == 0.0) return

    // Categories without a mapped color fall back to the scheme's neutral, not a fixed grey
    val fallbackColor = MaterialTheme.colorScheme.outline
    val pieData = remember(categories, lookup, fallbackColor, selectedCategoryId) {
        categories.map { category ->
            Pie(
                label = category.categoryId,
                data = category.spent.toDouble(),
                color = lookup[category.categoryId]?.color?.let { runCatching { Color(it.toColorInt()) }.getOrNull() } ?: fallbackColor,
                selectedColor = (lookup[category.categoryId]?.color?.let { runCatching { Color(it.toColorInt()) }.getOrNull() }
                    ?: fallbackColor).copy(alpha = 0.8f),
                selected = category.categoryId == selectedCategoryId,
                scaleAnimEnterSpec = tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing),
                colorAnimEnterSpec = tween(500)
            )
        }
    }

    var chartData by remember(pieData) { mutableStateOf(pieData) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            PieChart(
                modifier = Modifier.size(160.dp),
                data = chartData,
                onPieClick = { clickedPie ->
                    val nextCategoryId = clickedPie.label.takeIf { it != selectedCategoryId }
                    onCategoryClick(nextCategoryId)
                    chartData = chartData.map { pie ->
                        pie.copy(selected = pie.label == nextCategoryId)
                    }
                },
                selectedScale = 1.1f,
                scaleAnimEnterSpec = tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing),
                colorAnimEnterSpec = tween(500),
                style = Pie.Style.Stroke(width = 12.dp)
            )

            // Center Icon Overlay
            val selectedPie = chartData.find { it.selected }
            BlurredAnimatedVisibility(
                visible = selectedPie != null,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() +scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                if (selectedPie != null && selectedPie.label != null) {
                    CategoryIcon(
                        category = lookup[selectedPie.label]?.displayName.orEmpty(),
                        iconName = lookup[selectedPie.label]?.icon,
                        size = 32.dp,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(selectedPie.color.copy(alpha = 0.2f))
                            .padding(12.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = Spacing.md)
        ) {
            items(chartData.sortedByDescending { it.data }) { pie ->
                BudgetLegendItem(
                    label = lookup[pie.label]?.displayName.orEmpty(),
                    value = pie.data,
                    color = pie.color,
                    isSelected = pie.selected,
                    currency = currency,
                    totalAmount = total,
                    onClick = {
                        val nextCategoryId = pie.label.takeIf { it != selectedCategoryId }
                        onCategoryClick(nextCategoryId)
                        chartData = chartData.map { p ->
                            p.copy(selected = p.label == nextCategoryId)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun BudgetLegendItem(
    label: String,
    value: Double,
    color: Color,
    currency: String,
    isSelected: Boolean,
    totalAmount: Double,
    onClick: () -> Unit
) {
    val percentage = (value / totalAmount * 100).toInt()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else Color.Transparent)
            .padding(6.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = CoreMoneyPresentation.formatCurrency(value, currency),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.percentage_format, percentage),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
