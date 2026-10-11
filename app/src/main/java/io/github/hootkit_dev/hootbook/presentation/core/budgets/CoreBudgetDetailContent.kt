@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.hootkit_dev.hootbook.presentation.core.budgets

import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListSection
import io.github.hootkit_dev.hootbook.presentation.ui.components.TooltipIconButton

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
import io.github.hootkit_dev.hootbook.utils.DateFormats
import java.time.LocalDate as JavaDate

/** Original BudgetDetailScreen divider, using Core's effective period rather than an Android period engine. */
@Composable internal fun CoreBudgetPeriodDivider(budget: Budget) {
    val start = JavaDate.parse(budget.current.start.budgetDateText())
    val end = JavaDate.parse(budget.current.end.budgetDateText())
    val text = DateFormats.range(start, end)
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        DashedLine(Modifier.weight(1f), MaterialTheme.colorScheme.onSurface.copy(alpha = .2f))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        DashedLine(Modifier.weight(1f), MaterialTheme.colorScheme.onSurface.copy(alpha = .2f))
    }
}

/** The budget's category limits: what each allows, what is spent and left; a row edits its limit. */
@Composable internal fun CoreBudgetLimitsSection(budget: Budget, categories: List<Category>, enabled: Boolean,
    onAdd: () -> Unit, onEdit: (Category) -> Unit) {
    val currency = budget.amount.currency
    ListSection(
        title = stringResource(R.string.core_budgets_limits),
        action = { TooltipIconButton(Icons.Rounded.Add, stringResource(R.string.add_limit), onAdd, enabled = enabled) },
    ) {
        if (budget.current.limits.isEmpty()) item(headline = { Text(stringResource(R.string.no_category_limits_desc), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant) })
        budget.current.limits.forEach { limit ->
            val category = categories.find { it.id == limit.categoryId }
            val color = runCatching { Color(android.graphics.Color.parseColor(category?.color)) }.getOrDefault(MaterialTheme.colorScheme.primary)
            item(
                leading = { Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                    CoreRecordIcon(category?.icon.orEmpty(), Modifier.size(24.dp)) } },
                headline = { Text(category?.displayName ?: limit.categoryId, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supporting = { Text(stringResource(R.string.core_budgets_limit_status, CoreMoneyPresentation.format(limit.spent, currency),
                    CoreMoneyPresentation.format(limit.remaining, currency)), color = if (limit.over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) },
                trailing = { Text(CoreMoneyPresentation.format(limit.amount, currency), style = MaterialTheme.typography.titleMedium) },
                onClick = if (enabled && category != null) ({ onEdit(category) }) else null,
            )
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
                Text(DateFormats.range(period.start.historyJavaDate(), period.end.historyJavaDate()), style = MaterialTheme.typography.titleMedium)
                Text("${CoreMoneyPresentation.format(period.spent, currency)} / ${CoreMoneyPresentation.format(period.amount, currency)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
