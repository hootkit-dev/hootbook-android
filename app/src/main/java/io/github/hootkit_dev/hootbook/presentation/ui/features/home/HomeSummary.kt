@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.home

import io.github.hootkit_dev.hootbook.presentation.data.ConvertedMoney

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.AccountCategory
import io.github.hootkit_dev.hootbook.presentation.ui.theme.moneyColors
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation as CurrencyFormatter
import io.github.hootkit_dev.hootbook.presentation.format.*
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.category
import io.github.hootkit_dev.hootbook.presentation.ui.components.TransactionItem
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Tabular figures, so amounts in a column line up
private fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

private val CardShape = RoundedCornerShape(24.dp)

@Composable
private fun locale(): Locale = LocalConfiguration.current.locales[0]

/** "9月1日" / "Sep 1", with the year once it is not this one. */
private fun shortDate(date: LocalDate, locale: Locale): String {
    val zh = locale.language == "zh"
    val pattern = when {
        date.year == LocalDate.now().year -> if (zh) "M月d日" else "MMM d"
        else -> if (zh) "yyyy年M月d日" else "MMM d, yyyy"
    }
    return dev.hootkit.core.LocalDate(date.year, date.monthValue.toUByte(), date.dayOfMonth.toUByte()).coreDateText(DateTimeFormatter.ofPattern(pattern, locale))
}

/** An amount with its currency symbol a size smaller than the figure. */
@Composable
private fun amountWithSmallSymbol(amount: BigDecimal?, currency: String, symbolStyle: TextStyle, customSymbols: Map<String, String>) = buildAnnotatedString {
    val formatted = CurrencyFormatter.format(amount, currency, customSymbols)
    val symbol = customSymbols[currency] ?: CurrencyFormatter.getCurrencySymbol(currency)
    val at = formatted.indexOf(symbol)
    if (symbol.isEmpty() || at < 0) {
        append(formatted)
    } else {
        append(formatted.substring(0, at))
        withStyle(SpanStyle(fontSize = symbolStyle.fontSize, fontWeight = FontWeight.Normal)) { append(symbol) }
        append(formatted.substring(at + symbol.length))
    }
}

/** Net worth opens every account; [breakdownShown] reveals account categories beneath it. */
@Composable
internal fun NetWorthSection(
    netWorth: BigDecimal?,
    currency: String,
    complete: Boolean,
    customSymbols: Map<String, String>,
    canChangeCurrency: Boolean,
    onCurrencyClick: () -> Unit,
    onOpenAccounts: () -> Unit,
    breakdownShown: Boolean,
    onToggleBreakdown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = locale()
    val colors = moneyColors
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onOpenAccounts)
                    .padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.net_worth_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (canChangeCurrency) {
                TextButton(onClick = onCurrencyClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(currency, style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp))
                }
            }
            TextButton(onClick = onToggleBreakdown, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(stringResource(R.string.home_by_category), style = MaterialTheme.typography.labelLarge)
                Icon(if (breakdownShown) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp))
            }
        }
        Text(
            text = amountWithSmallSymbol(netWorth, currency, MaterialTheme.typography.headlineSmall, customSymbols),
            style = MaterialTheme.typography.displaySmall.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp).clickable(onClick = onOpenAccounts)
        )
        if (!complete) Text(stringResource(R.string.core_complete_incomplete), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * This month: the net (saved or overspent, said in words) as the largest figure, then income
 * and spending as two bars on one scale, each with a tick at last month's figure.
 */
@Composable
internal fun MonthCard(
    summary: LedgerSummary,
    lastSummary: LedgerSummary?,
    display: Map<String, ConvertedMoney>,
    customSymbols: Map<String, String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = locale()
    val colors = moneyColors
    val today = LocalDate.now()
    val monthName = today.format(DateTimeFormatter.ofPattern(if (locale.language == "zh") "M月" else "MMMM", locale))
    val daysLeft = today.lengthOfMonth() - today.dayOfMonth
    val converted = display.filterKeys { it != "monthly" }
    val showConverted = converted.isNotEmpty() && converted.values.all { it.complete }
    val currency = if (showConverted) converted.values.first().currency else summary.currency
    val lastIncome = (if (showConverted) display["lastIncome"]?.amount else null) ?: lastSummary?.income ?: BigDecimal.ZERO
    val lastExpenses = (if (showConverted) display["lastExpense"]?.amount else null) ?: lastSummary?.expense ?: BigDecimal.ZERO
    val inc = ((if (showConverted) display["income"]?.amount else null) ?: summary.income).abs()
    val exp = ((if (showConverted) display["expense"]?.amount else null) ?: summary.expense).abs()
    val net = (if (showConverted) display["net"]?.amount else null) ?: summary.net
    val scale = listOf(inc, exp, lastIncome.abs(), lastExpenses.abs()).maxOf { it }.takeIf { it.signum() > 0 }
    val empty = inc.signum() == 0 && exp.signum() == 0
    fun money(v: BigDecimal) = CurrencyFormatter.format(v, currency, customSymbols)

    Surface(onClick = onClick, shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_month_title, monthName), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.home_days_left, daysLeft), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(
                    when {
                        empty -> R.string.home_month_empty
                        net.signum() > 0 -> R.string.home_net_income
                        net.signum() < 0 -> R.string.home_net_spending
                        else -> R.string.home_month_even
                    }
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                amountWithSmallSymbol(net.abs(), currency, MaterialTheme.typography.titleLarge, customSymbols),
                style = MaterialTheme.typography.headlineMedium.tabular(),
                color = when {
                    empty || net.signum() == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                    net.signum() > 0 -> colors.income
                    else -> colors.expense
                }
            )
            if (!summary.complete || lastSummary?.complete == false || converted.values.any { !it.complete }) Text(stringResource(R.string.core_complete_incomplete), style = MaterialTheme.typography.bodySmall)
            if (!empty) {
                Spacer(Modifier.height(12.dp))
                MoneyBar(
                    icon = Icons.Rounded.SouthWest, label = stringResource(R.string.home_in), amount = money(inc),
                    fraction = scale?.let { inc.toFloat() / it.toFloat() } ?: 0f,
                    lastFraction = scale?.let { lastIncome.abs().toFloat() / it.toFloat() },
                    color = colors.income, container = colors.incomeContainer, onContainer = colors.onIncomeContainer
                )
                Spacer(Modifier.height(10.dp))
                MoneyBar(
                    icon = Icons.Rounded.NorthEast, label = stringResource(R.string.home_out), amount = money(exp),
                    fraction = scale?.let { exp.toFloat() / it.toFloat() } ?: 0f,
                    lastFraction = scale?.let { lastExpenses.abs().toFloat() / it.toFloat() },
                    color = colors.expense, container = colors.expenseContainer, onContainer = colors.onExpenseContainer
                )
            }
            if (lastIncome.signum() != 0 || lastExpenses.signum() != 0) {
                HorizontalDivider(Modifier.padding(top = 14.dp, bottom = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                val lastNet = (if (showConverted) display["lastNet"]?.amount else null) ?: lastSummary!!.net
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The same tick as on the bars, as the note's legend
                    Box(Modifier.size(width = 2.dp, height = 12.dp).background(MaterialTheme.colorScheme.onSurfaceVariant))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            R.string.home_last_month_note,
                            money(lastIncome.abs()), money(lastExpenses.abs()),
                            if (lastNet.signum() >= 0) stringResource(R.string.home_last_net_income, money(lastNet))
                            else stringResource(R.string.home_last_net_spending, money(lastNet.abs()))
                        ),
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MoneyBar(
    icon: ImageVector,
    label: String,
    amount: String,
    fraction: Float,
    lastFraction: Float?,
    color: Color,
    container: Color,
    onContainer: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(container), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = onContainer)
        }
        Column(Modifier.weight(1f)) {
            Row {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(amount, style = MaterialTheme.typography.titleSmall.tabular())
            }
            Spacer(Modifier.height(6.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
                lastFraction?.takeIf { it > 0f }?.let { last ->
                    Box(
                        Modifier
                            .offset(x = (maxWidth - 2.dp) * last.coerceIn(0f, 1f))
                            .size(width = 2.dp, height = 16.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        }
    }
}

private fun AccountCategory.icon(): ImageVector = when (this) {
    AccountCategory.WALLETS -> Icons.Rounded.AccountBalanceWallet
    AccountCategory.BANKS -> Icons.Rounded.AccountBalance
    AccountCategory.CREDIT_CARDS -> Icons.Rounded.CreditCard
    AccountCategory.INVESTMENTS -> Icons.AutoMirrored.Rounded.ShowChart
}

/**
 * Original segmented account categories; Core provides counts, available credit and the
 * nearest payment due. Empty investment accounts offer to connect.
 */
@Composable
internal fun AccountCategoryList(
    overview: AccountsOverview?,
    onOpen: (AccountCategory) -> Unit,
    // The nearest card payment due, said on the credit card row
    modifier: Modifier = Modifier
) {
    val locale = locale()
    val grouped = overview?.accounts.orEmpty().groupBy { it.category() }
    val shown = if (overview == null) emptyList() else
        (grouped + (AccountCategory.INVESTMENTS to grouped[AccountCategory.INVESTMENTS].orEmpty())).entries.sortedBy { it.key.ordinal }
    val nextCardDue = overview?.nearestDue?.dueDate
    val colors = moneyColors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        shown.forEachIndexed { index, item ->
            val top = if (index == 0) 24.dp else 6.dp
            val bottom = if (index == shown.lastIndex) 24.dp else 6.dp
            val investments = item.key == AccountCategory.INVESTMENTS
            Surface(
                onClick = { onOpen(item.key) },
                shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(if (investments) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            item.key.icon(), null, Modifier.size(20.dp),
                            tint = if (investments) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(item.key.titleRes), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            when {
                                item.value.isEmpty() -> stringResource(R.string.home_connect_hint)
                                item.key == AccountCategory.CREDIT_CARDS && nextCardDue != null ->
                                    stringResource(R.string.home_cards_due, item.value.size, nextCardDue.coreDateText(DateTimeFormatter.ofPattern(if (locale.language == "zh") "M月d日" else "MMM d", locale)))
                                else -> stringResource(R.string.overview_count, item.value.size)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.key == AccountCategory.CREDIT_CARDS) overview?.creditAvailable?.let { available ->
                        Text(stringResource(R.string.available_credit_label,
                            CurrencyFormatter.formatCurrency(available.amount, available.currency)),
                            style = MaterialTheme.typography.titleMedium.tabular())
                    }
                    if (item.value.isEmpty()) OutlinedButton(onClick = { onOpen(item.key) }, contentPadding = PaddingValues(horizontal = 16.dp), modifier = Modifier.height(32.dp)) {
                        Text(stringResource(R.string.home_connect))
                    } else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * The latest transactions grouped by day (今天 / 昨天 / 10月15日, each said once), each row an
 * icon, the merchant, "category　account" in plain text, and the amount: spending in the text
 * color, income green with "+", transfers muted with where the money went.
 */
@Composable
internal fun RecentTransactionsCard(
    transactions: List<Transaction>,
    categories: Map<String, Category>,
    subcategories: Map<String, Subcategory>,
    accounts: Map<String, Account>,
    conversions: Map<String, ConvertedMoney>,
    customSymbols: Map<String, String>,
    mainCurrency: String,
    isLoading: Boolean,
    onTransactionClick: (Transaction) -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = locale()
    val today = stringResource(R.string.today_lbl)
    val yesterday = stringResource(R.string.yesterday_lbl)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_recent),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onViewAll) { Text(stringResource(R.string.home_all)) }
        }
        Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            when {
                isLoading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    io.github.hootkit_dev.hootbook.presentation.ui.components.LoadingCircle(Modifier.size(48.dp))
                }
                transactions.isEmpty() -> Text(
                    stringResource(R.string.home_recent_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(20.dp)
                )
                else -> Column(Modifier.padding(bottom = 8.dp)) {
                    val now = LocalDate.now()
                    transactions.groupBy { java.time.LocalDate.of(it.dateTime.year, it.dateTime.month.toInt(), it.dateTime.day.toInt()) }.forEach { (day, rows) ->
                        Text(
                            when (day) {
                                now -> today
                                now.minusDays(1) -> yesterday
                                else -> shortDate(day, locale)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
                        )
                        rows.forEach { transaction ->
                            val account = accounts[transaction.accountId]
                            val category = categories[transaction.categoryId]
                            val subcategory = subcategories[transaction.subcategoryId]
                            val subtitle = if (transaction.kind == TransactionKind.TRANSFER) listOfNotNull(account?.name, accounts[transaction.toAccountId]?.name).joinToString(" → ")
                                else listOfNotNull(subcategory?.displayName ?: category?.displayName, account?.name).joinToString("　")
                            TransactionItem(
                                transaction = transaction, categoryEntity = category, subcategoryEntity = subcategory,
                                accountIconName = account?.icon, accountColorHex = account?.color,
                                showDate = false, subtitleOverride = subtitle, useHomeStyle = true, customSymbols = customSymbols,
                                amountColorOverride = when (transaction.kind) {
                                    TransactionKind.INCOME, TransactionKind.LOAN_IN -> moneyColors.income
                                    TransactionKind.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                amountOverride = (when (transaction.kind) {
                                    TransactionKind.INCOME, TransactionKind.LOAN_IN -> "+"
                                    TransactionKind.TRANSFER -> ""
                                    else -> "-"
                                }) + CurrencyFormatter.format(transaction.amount, transaction.currency, customSymbols),
                                convertedAmount = conversions[transaction.id]?.amount, mainCurrency = mainCurrency,
                                onClick = { onTransactionClick(transaction) },
                            )
                        }
                    }
                }
            }
        }
    }
}
