package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.ui.res.stringResource

import io.github.hootkit_dev.hootbook.R

import io.github.hootkit_dev.hootbook.utils.DateFormats

import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation
import java.math.BigDecimal
import dev.hootkit.core.BalanceDay
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText

@Composable
fun BalanceChart(
    primaryCurrency: String,
    balanceHistory: List<BalanceDay>,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    height: Int = 200
) {
    if (balanceHistory.isEmpty()) return

    // Keep Core calendar samples in chronological order.
    val sortedHistory = remember(balanceHistory) {
        balanceHistory.sortedBy { it.date.coreDateText() }
    }

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(balanceHistory) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1500, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f))
        )
    }
    
    // Scale the canvas to Core values without changing any balance samples.
    val maxBalance = sortedHistory.maxOf { it.balance }
    val minBalance = sortedHistory.minOf { it.balance }
    val balanceRange = maxBalance - minBalance
    
    // Add some padding to the range
    val paddedMin = minBalance - (balanceRange * BigDecimal("0.1"))
    val paddedMax = maxBalance + (balanceRange * BigDecimal("0.1"))
    val paddedRange = paddedMax - paddedMin
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp),
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column{
            // Y-axis labels (max and min balance)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = CoreMoneyPresentation.formatCurrency(paddedMax, primaryCurrency),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.balance_trend),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // Chart Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xs)
                    .weight(1f)
            ) {
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    
                    // Draw horizontal grid lines
                    val gridLines = 4
                    for (i in 0..gridLines) {
                        val y = canvasHeight * (i.toFloat() / gridLines)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(canvasWidth, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    }

                    // Draw vertical grid lines
                    val verticalGridLines = 5
                    for (i in 0..verticalGridLines) {
                        val x = canvasWidth * (i.toFloat() / verticalGridLines)
                        drawLine(
                            color = gridColor,
                            start = Offset(x, 0f),
                            end = Offset(x, canvasHeight),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    }
                    
                    if (sortedHistory.size < 2) {
                        // Just draw a single point if we only have one data point
                        val point = sortedHistory.first()
                        val fraction = if (paddedRange.signum() == 0) 0.5f else (point.balance - paddedMin).toFloat() / paddedRange.toFloat()
                        val y = canvasHeight * (1f - fraction)
                        drawCircle(
                            color = lineColor,
                            radius = 4.dp.toPx(),
                            center = Offset(canvasWidth / 2, y)
                        )
                    } else {
                        // Draw the line chart
                        val path = Path()
                        val points = mutableListOf<Offset>()

                        sortedHistory.forEachIndexed { index, point ->
                            val x = canvasWidth * (index.toFloat() / (sortedHistory.size - 1).coerceAtLeast(1))
                            val fraction = if (paddedRange.signum() == 0) 0.5f else (point.balance - paddedMin).toFloat() / paddedRange.toFloat()
                            val y = canvasHeight * (1f - fraction)

                            val offset = Offset(x, y)
                            points.add(offset)

                            if (index == 0) {
                                path.moveTo(x, y)
                            } else {
                                path.lineTo(x, y)
                            }
                        }
                        
                        // Draw the line and fill with animation
                        clipRect(right = canvasWidth * animationProgress.value) {
                            // Draw gradient fill under the line
                            val fillPath = Path().apply {
                                addPath(path)
                                lineTo(canvasWidth, canvasHeight)
                                lineTo(0f, canvasHeight)
                                close()
                            }

                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        lineColor.copy(alpha = 0.3f),
                                        lineColor.copy(alpha = 0.05f)
                                    ),
                                    startY = 0f,
                                    endY = canvasHeight
                                )
                            )

                            // Draw the line
                            drawPath(
                                path = path,
                                color = lineColor,
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        // Draw points
                        points.forEachIndexed { index, point ->
                            val pointProgress = (animationProgress.value * points.size - index).coerceIn(0f, 1f)
                            if (pointProgress > 0f) {
                                drawCircle(
                                    color = lineColor,
                                    radius = 4.dp.toPx() * pointProgress,
                                    center = point
                                )
                                drawCircle(
                                    color = backgroundColor,
                                    radius = 4.dp.toPx() * pointProgress,
                                    center = point,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }
            
            // X-axis labels (dates) - oldest on left, newest on right
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs ),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (sortedHistory.isNotEmpty()) {
                    // First (oldest) date on the left
                    Text(
                        text = sortedHistory.first().date.coreDateText(
                            DateFormats.monthDayFormatter()
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (sortedHistory.size > 1) {
                        // Last (newest) date on the right
                        Text(
                            text = sortedHistory.last().date.coreDateText(
                                DateFormats.monthDayFormatter()
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            // Min balance label
            Text(
                text = CoreMoneyPresentation.formatCurrency(paddedMin, primaryCurrency),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
        }
    }
}
