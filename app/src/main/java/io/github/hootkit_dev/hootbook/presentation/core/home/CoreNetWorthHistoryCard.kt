package io.github.hootkit_dev.hootbook.presentation.core.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.features.home.HomeMoneyPresentation
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

@Composable
fun CoreNetWorthHistoryCard(
    symbols: Map<String, String>,
    modifier: Modifier = Modifier,
    viewModel: CoreNetWorthHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    CoreNetWorthHistoryContent(state, symbols, viewModel::chooseRange, viewModel::retry, modifier)
}

@Composable
internal fun CoreNetWorthHistoryContent(
    state: CoreNetWorthHistoryUiState,
    symbols: Map<String, String>,
    onRange: (CoreHistoryRange) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var information by remember { mutableStateOf(false) }
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Surface(modifier.fillMaxWidth().testTag("home_net_worth_history"),
        shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.net_worth_history_title), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                state.data?.currency?.let { Text(it, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = { information = true }, modifier = Modifier.testTag("net_worth_history_info")) {
                    Icon(Icons.Rounded.Info, stringResource(R.string.net_worth_history_about))
                }
            }
            Text(stringResource(R.string.net_worth_history_estimate), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                CoreHistoryRange.entries.forEachIndexed { index, range ->
                    SegmentedButton(selected = state.range == range, onClick = { onRange(range) },
                        shape = SegmentedButtonDefaults.itemShape(index, CoreHistoryRange.entries.size), icon = {},
                        label = { Text(stringResource(range.labelResource()), maxLines = 1) },
                        modifier = Modifier.testTag("net_worth_history_range_${range.name}"))
                }
            }
            // This slot never disappears during loading, failed refreshes, or missing FX.
            Box(Modifier.fillMaxWidth().height((190 * fontScale).dp).testTag("net_worth_history_plot"), contentAlignment = Alignment.Center) {
                val data = state.data
                when {
                    data != null && data.complete && data.points.isNotEmpty() -> NativeHistoryPlot(data, symbols)
                    data != null && !data.complete -> Text(stringResource(R.string.net_worth_history_incomplete),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    !state.refreshing -> Text(stringResource(R.string.net_worth_history_empty), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.failed) Text(stringResource(if (state.data == null) R.string.net_worth_history_failed else R.string.net_worth_history_stale),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                else Spacer(Modifier.weight(1f))
                if (state.failed || state.data?.complete == false) TextButton(onClick = onRetry,
                    modifier = Modifier.testTag("net_worth_history_retry")) { Text(stringResource(R.string.net_worth_history_retry)) }
            }
        }
    }
    if (information) AlertDialog(onDismissRequest = { information = false },
        title = { Text(stringResource(R.string.net_worth_history_title)) },
        text = { Text(stringResource(R.string.net_worth_history_explanation)) },
        confirmButton = { TextButton(onClick = { information = false }) { Text(stringResource(android.R.string.ok)) } })
}

@Composable
private fun NativeHistoryPlot(data: CoreNetWorthHistoryResult, symbols: Map<String, String>) {
    val points = data.points
    var selected by remember(data) { mutableIntStateOf(points.lastIndex) }
    val locale = LocalConfiguration.current.locales[0]
    val dates = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val currentLabel = stringResource(R.string.net_worth_history_current)
    val active = points[selected.coerceIn(points.indices)]
    fun label(point: CoreNetWorthHistoryPoint) = if (point.current) currentLabel else point.date.format(dates)
    fun amount(value: BigDecimal) = HomeMoneyPresentation.format(value, data.currency, symbols, locale)
    val description = stringResource(R.string.net_worth_history_accessibility,
        label(points.first()), amount(points.first().amount), amount(points.last().amount))
    val minimum = points.minOf { it.amount }
    val maximum = points.maxOf { it.amount }
    val span = maximum - minimum
    val firstDay = points.first().date.toEpochDay()
    val daySpan = (points.last().date.toEpochDay() - firstDay).coerceAtLeast(1)
    // Float is used only for pixel positions, after all financial amounts are finalized as Decimal.
    val coordinates = remember(data) { points.map { point ->
        val x = if (points.size == 1) 0.5f else (point.date.toEpochDay() - firstDay).toFloat() / daySpan
        val y = if (span.signum() == 0) 0.5f else
            1f - (point.amount - minimum).divide(span, 8, RoundingMode.HALF_UP).toFloat()
        Offset(x, y)
    } }
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val highlight = MaterialTheme.colorScheme.secondary
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(label(active), style = MaterialTheme.typography.labelMedium)
            Text(amount(active.amount), style = MaterialTheme.typography.titleSmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (span.signum() == 0) "" else amount(maximum),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (points.size == 1) Text(stringResource(R.string.net_worth_history_no_past), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp)
            .semantics { contentDescription = description }
            .pointerInput(data) { detectTapGestures { location ->
                val fraction = (location.x / size.width).coerceIn(0f, 1f)
                selected = coordinates.indices.minByOrNull { abs(coordinates[it].x - fraction) } ?: points.lastIndex
            } }) {
            val margin = 5.dp.toPx()
            fun position(value: Offset) = Offset(margin + value.x * (size.width - 2 * margin),
                margin + value.y * (size.height - 2 * margin))
            repeat(3) { row ->
                val y = margin + row / 2f * (size.height - 2 * margin)
                drawLine(grid, Offset(margin, y), Offset(size.width - margin, y), strokeWidth = 1.dp.toPx())
            }
            val historical = points.indices.filter { !points[it].current }
            if (historical.isNotEmpty()) {
                val path = Path()
                historical.forEachIndexed { index, item ->
                    val point = position(coordinates[item])
                    if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
            if (points.size > 1 && points.last().current) drawLine(line,
                position(coordinates[points.lastIndex - 1]), position(coordinates.last()), strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
            drawCircle(line, radius = 4.dp.toPx(), center = position(coordinates.last()))
            drawCircle(highlight, radius = 4.dp.toPx(), center = position(coordinates[selected.coerceIn(points.indices)]))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(points.first().date.format(dates), style = MaterialTheme.typography.labelSmall)
            Text(currentLabel, style = MaterialTheme.typography.labelSmall)
        }
        Text(if (span.signum() == 0) "" else amount(minimum),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun CoreHistoryRange.labelResource() = when (this) {
    CoreHistoryRange.MONTH -> R.string.net_worth_history_month
    CoreHistoryRange.QUARTER -> R.string.net_worth_history_quarter
    CoreHistoryRange.HALF_YEAR -> R.string.net_worth_history_half_year
    CoreHistoryRange.YEAR -> R.string.net_worth_history_year
    CoreHistoryRange.ALL -> R.string.net_worth_history_all
}
