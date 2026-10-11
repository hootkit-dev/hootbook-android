@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.hootkit_dev.hootbook.presentation.core.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreRecordIcon
import io.github.hootkit_dev.hootbook.presentation.ui.components.DashedLine
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import io.github.hootkit_dev.hootbook.utils.DateFormats
import java.time.LocalDate as JavaDate

/** Original BudgetDetailScreen divider, using Core's effective period rather than an Android period engine. */
@Composable internal fun CoreBudgetPeriodDivider(budget: Budget) {
    val start = JavaDate.parse(budget.current.start.budgetDateText())
    val end = JavaDate.parse(budget.current.end.budgetDateText())
    val text = if (start.year == end.year) "${start.format(DateFormats.monthDayFormatter())} - ${end.format(DateFormats.monthDayFormatter())}, ${start.year}"
        else "${start.format(DateFormats.fullDateFormatter())} - ${end.format(DateFormats.fullDateFormatter())}"
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        DashedLine(Modifier.weight(1f), MaterialTheme.colorScheme.onSurface.copy(alpha = .2f))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        DashedLine(Modifier.weight(1f), MaterialTheme.colorScheme.onSurface.copy(alpha = .2f))
    }
}

/** Exact limit-card geometry from the original EditBudgetSheet, using Core limit statuses. */
@Composable internal fun CoreBudgetLimitsCard(budget: Budget, categories: List<Category>, enabled: Boolean,
    onAdd: () -> Unit, onEdit: (Category) -> Unit, onRemove: (String) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.category_limits), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                TextButton(onClick = onAdd, enabled = enabled) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.add_limit)) }
            }
            if (budget.current.limits.isEmpty()) Text(stringResource(R.string.no_category_limits_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            budget.current.limits.forEachIndexed { index, limit ->
                val category = categories.find { it.id == limit.categoryId }
                val color = runCatching { Color(android.graphics.Color.parseColor(category?.color)) }.getOrDefault(MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth().clickable(enabled = enabled && category != null) { category?.let(onEdit) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = .15f)), contentAlignment = Alignment.Center) { CoreRecordIcon(category?.icon.orEmpty(), Modifier.size(18.dp)) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(category?.displayName ?: limit.categoryId, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(CurrencyFormatter.formatCurrency(limit.amount, budget.amount.currency), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.core_budgets_limit_status, limit.spent.toPlainString(), limit.remaining.toPlainString()), style = MaterialTheme.typography.labelSmall, color = if (limit.over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onRemove(limit.categoryId) }, enabled = enabled) { Icon(Icons.Rounded.Delete, stringResource(R.string.core_budgets_limit_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (index != budget.current.limits.lastIndex) HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .1f))
            }
        }
    }
}

@Composable internal fun CoreBudgetTransactionsUnavailable() {
    Text(stringResource(R.string.transactions), Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Rounded.ReceiptLong, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.core_history_transaction_gap), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** Original rounded grouped period row and percentage ring; no derived monetary totals. */
@Composable internal fun CoreBudgetHistoryRow(period: BudgetPeriodSummary, currency: String, position: ListItemPosition, onClick: () -> Unit) {
    val fraction = if (period.amount.signum() <= 0) 0f else period.spent.divide(period.amount, java.math.MathContext.DECIMAL64).toFloat()
    val color = when { period.over -> MaterialTheme.colorScheme.error; fraction > .8f -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.primary }
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(bottom = 1.5.dp), shape = position.toShape(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${period.start.budgetDateText()} — ${period.end.budgetDateText()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${CurrencyFormatter.formatCurrency(period.spent, currency)} / ${CurrencyFormatter.formatCurrency(period.amount, currency)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!period.complete) Text(stringResource(R.string.core_budgets_history_incomplete), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.width(16.dp))
            Box(Modifier.size(56.dp).clip(CircleShape).background(color.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.size(56.dp), color = color, strokeWidth = 4.dp, trackColor = color.copy(alpha = .1f))
                Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
            }
        }
    }
}
