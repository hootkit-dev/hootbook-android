package io.github.hootkit_dev.hootbook.presentation.ui.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hootkit.core.AccountsOverview
import dev.hootkit.core.LedgerSummary
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreDisplayedMoney
import io.github.hootkit_dev.hootbook.presentation.ui.theme.moneyColors
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The Core overview already includes every pocket and converted account total. */
@Composable
internal fun NetWorthSection(
    overview: AccountsOverview,
    symbols: Map<String, String>,
    display: CoreDisplayedMoney?,
    onCurrencyClick: () -> Unit,
    onOpenAccounts: () -> Unit,
    breakdownShown: Boolean,
    onToggleBreakdown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().testTag("home_net_worth")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(MaterialTheme.shapes.small).clickable(onClick = onOpenAccounts)
                .padding(start = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.net_worth_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onCurrencyClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(overview.netWorth.currency, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp))
            }
            TextButton(onClick = onToggleBreakdown, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.testTag("home_account_categories")) {
                Text(stringResource(R.string.home_by_category), style = MaterialTheme.typography.labelLarge)
                Icon(if (breakdownShown) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp))
            }
        }
        Text(HomeMoneyPresentation.format(overview.netWorth.amount, overview.netWorth.currency, symbols),
            style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp).clickable(onClick = onOpenAccounts))
        if (!overview.netWorthComplete) Text(stringResource(R.string.core_complete_incomplete), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        display?.let {
            Text(if (it.complete) HomeMoneyPresentation.format(it.amount, it.currency, symbols, useCode = true)
                else stringResource(R.string.core_complete_display_missing, it.currency),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Current and previous monthly figures come straight from Core's filtered ledger summary. */
@Composable
internal fun MonthCard(summary: LedgerSummary, previous: LedgerSummary?, symbols: Map<String, String>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val today = LocalDate.now()
    val monthName = today.format(DateTimeFormatter.ofPattern(if (locale.language == "zh") "M月" else "MMMM", locale))
    val colors = moneyColors
    val empty = summary.income.signum() == 0 && summary.expense.signum() == 0
    // Chart scale only. Ledger amounts and net are never recomputed in the presentation layer.
    val scale = listOfNotNull(summary.income.abs(), summary.expense.abs(), previous?.income?.abs(), previous?.expense?.abs()).maxOrNull()?.toFloat()?.takeIf { it > 0f } ?: 1f
    fun money(value: BigDecimal) = HomeMoneyPresentation.format(value, summary.currency, symbols)
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth().testTag("home_month_summary")) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_month_title, monthName), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.home_days_left, today.lengthOfMonth() - today.dayOfMonth), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(when { empty -> R.string.home_month_empty; summary.net.signum() > 0 -> R.string.home_net_income; summary.net.signum() < 0 -> R.string.home_net_spending; else -> R.string.home_month_even }), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(money(summary.net.abs()), style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), color = when { empty || summary.net.signum() == 0 -> MaterialTheme.colorScheme.onSurfaceVariant; summary.net.signum() > 0 -> colors.income; else -> colors.expense })
            if (!summary.complete) Text(stringResource(R.string.core_complete_missing, summary.missingCurrencies.joinToString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (!empty) {
                Spacer(Modifier.height(12.dp))
                MoneyBar(Icons.Rounded.SouthWest, stringResource(R.string.home_in), money(summary.income), summary.income.abs().toFloat() / scale,
                    previous?.income?.abs()?.toFloat()?.div(scale), colors.income, colors.incomeContainer, colors.onIncomeContainer)
                Spacer(Modifier.height(10.dp))
                MoneyBar(Icons.Rounded.NorthEast, stringResource(R.string.home_out), money(summary.expense), summary.expense.abs().toFloat() / scale,
                    previous?.expense?.abs()?.toFloat()?.div(scale), colors.expense, colors.expenseContainer, colors.onExpenseContainer)
            }
            if (previous != null && (previous.income.signum() != 0 || previous.expense.signum() != 0)) {
                if (!empty) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(width = 2.dp, height = 12.dp).background(MaterialTheme.colorScheme.onSurfaceVariant))
                    Text(stringResource(R.string.home_previous_month_marker), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!previous.complete) Text(stringResource(R.string.core_complete_missing, previous.missingCurrencies.joinToString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun MoneyBar(icon: ImageVector, label: String, amount: String, fraction: Float, lastFraction: Float?, color: Color, container: Color, onContainer: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(container), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp), tint = onContainer) }
        Column(Modifier.weight(1f)) {
            Row { Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)); Text(amount, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum")) }
            Spacer(Modifier.height(6.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
                lastFraction?.takeIf { it > 0f }?.let { Box(Modifier.offset(x = (maxWidth - 2.dp) * it.coerceIn(0f, 1f)).size(width = 2.dp, height = 16.dp).background(MaterialTheme.colorScheme.onSurfaceVariant)) }
            }
        }
    }
}
