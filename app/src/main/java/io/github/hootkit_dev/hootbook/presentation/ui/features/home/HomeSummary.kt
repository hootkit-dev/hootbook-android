@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import io.github.hootkit_dev.hootbook.presentation.common.TransactionDecoration
import io.github.hootkit_dev.hootbook.presentation.ui.components.BalancePoint
import io.github.hootkit_dev.hootbook.presentation.ui.components.BrandIcon
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.AccountCategory
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.AccountOverviewItem
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.OverviewStatus
import io.github.hootkit_dev.hootbook.presentation.ui.theme.moneyColors
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import io.github.hootkit_dev.hootbook.utils.displayTitle
import io.github.hootkit_dev.hootbook.utils.formatAmount
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
    return date.format(DateTimeFormatter.ofPattern(pattern, locale))
}

/** An amount with its currency symbol a size smaller than the figure. */
@Composable
private fun amountWithSmallSymbol(amount: BigDecimal, currency: String, symbolStyle: TextStyle) = buildAnnotatedString {
    val formatted = CurrencyFormatter.formatCurrency(amount, currency)
    val symbol = CurrencyFormatter.getCurrencySymbol(currency)
    val at = formatted.indexOf(symbol)
    if (symbol.isEmpty() || at < 0) {
        append(formatted)
    } else {
        append(formatted.substring(0, at))
        withStyle(SpanStyle(fontSize = symbolStyle.fontSize, fontWeight = FontWeight.Normal)) { append(symbol) }
        append(formatted.substring(at + symbol.length))
    }
}

/**
 * Net worth, straight on the page: the figure (opens every account), how it changed over the
 * trend's span, and the trend. [breakdownShown] reveals the account categories under it.
 */
@Composable
internal fun NetWorthSection(
    netWorth: BigDecimal,
    currency: String,
    history: List<BalancePoint>,
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
            text = amountWithSmallSymbol(netWorth, currency, MaterialTheme.typography.headlineSmall),
            style = MaterialTheme.typography.displaySmall.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp).clickable(onClick = onOpenAccounts)
        )
        if (history.size >= 2) {
            val first = history.first()
            val last = history.last()
            val days = ChronoUnit.DAYS.between(first.timestamp.toLocalDate(), last.timestamp.toLocalDate())
            val delta = last.balance - first.balance
            val amount = CurrencyFormatter.formatCurrency(delta.abs(), currency)
            Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.home_last_days, days.toInt()) + " ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    when (delta.signum()) {
                        1 -> stringResource(R.string.home_change_up, amount)
                        -1 -> stringResource(R.string.home_change_down, amount)
                        else -> stringResource(R.string.home_change_none)
                    },
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    color = when (delta.signum()) {
                        1 -> colors.income
                        -1 -> colors.expense
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Spacer(Modifier.height(12.dp))
            Sparkline(history.map { it.balance }, Modifier.fillMaxWidth().height(72.dp))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(
                    shortDate(first.timestamp.toLocalDate(), locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(stringResource(R.string.today_lbl), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The trend as a line with a soft fill and a dot at today; no grid, no axis. */
@Composable
private fun Sparkline(values: List<BigDecimal>, modifier: Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    Canvas(modifier) {
        val max = values.maxOf { it }.toFloat()
        val min = values.minOf { it }.toFloat()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val inset = 6.dp.toPx()
        val w = size.width - inset
        val h = size.height - inset * 2
        fun point(i: Int) = Offset(
            x = i.toFloat() / (values.size - 1) * w,
            y = inset + h - (values[i].toFloat() - min) / range * h
        )
        val path = Path().apply {
            values.indices.forEach { i -> point(i).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(point(values.lastIndex).x, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.18f), Color.Transparent)))
        drawPath(path, line, style = Stroke(width = 2.dp.toPx()))
        val end = point(values.lastIndex)
        drawCircle(surface, radius = 5.dp.toPx(), center = end)
        drawCircle(line, radius = 4.dp.toPx(), center = end)
    }
}

/**
 * This month: the net (saved or overspent, said in words) as the largest figure, then income
 * and spending as two bars on one scale, each with a tick at last month's figure.
 */
@Composable
internal fun MonthCard(
    income: BigDecimal,
    expenses: BigDecimal,
    lastIncome: BigDecimal,
    lastExpenses: BigDecimal,
    currency: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = locale()
    val colors = moneyColors
    val today = LocalDate.now()
    val monthName = today.format(DateTimeFormatter.ofPattern(if (locale.language == "zh") "M月" else "MMMM", locale))
    val daysLeft = today.lengthOfMonth() - today.dayOfMonth
    val inc = income.abs()
    val exp = expenses.abs()
    val net = inc - exp
    val scale = listOf(inc, exp, lastIncome.abs(), lastExpenses.abs()).maxOf { it }.takeIf { it.signum() > 0 }
    val empty = inc.signum() == 0 && exp.signum() == 0
    fun money(v: BigDecimal) = CurrencyFormatter.formatCurrency(v, currency)

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
                amountWithSmallSymbol(net.abs(), currency, MaterialTheme.typography.titleLarge),
                style = MaterialTheme.typography.headlineMedium.tabular(),
                color = when {
                    empty || net.signum() == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                    net.signum() > 0 -> colors.income
                    else -> colors.expense
                }
            )
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
                val lastNet = lastIncome.abs() - lastExpenses.abs()
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
 * Account categories as a segmented list, one figure each in the main currency; empty
 * categories are left out, and investments not yet connected offer to connect.
 */
@Composable
internal fun AccountCategoryList(
    items: List<AccountOverviewItem>,
    onOpen: (AccountCategory) -> Unit,
    // The nearest card payment due, said on the credit card row
    nextCardDue: LocalDate?,
    modifier: Modifier = Modifier
) {
    val locale = locale()
    val shown = items.filter { it.count > 0 || it.status == OverviewStatus.CONNECT }
    val colors = moneyColors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        shown.forEachIndexed { index, item ->
            val top = if (index == 0) 24.dp else 6.dp
            val bottom = if (index == shown.lastIndex) 24.dp else 6.dp
            val investments = item.category == AccountCategory.INVESTMENTS
            Surface(
                onClick = { onOpen(item.category) },
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
                            item.category.icon(), null, Modifier.size(20.dp),
                            tint = if (investments) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(item.category.titleRes), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            when {
                                item.status == OverviewStatus.CONNECT -> stringResource(R.string.home_connect_hint)
                                item.category == AccountCategory.CREDIT_CARDS && nextCardDue != null ->
                                    stringResource(R.string.home_cards_due, item.count, shortDate(nextCardDue, locale))
                                else -> stringResource(R.string.overview_count, item.count)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    when (item.status) {
                        OverviewStatus.CONNECT -> OutlinedButton(
                            onClick = { onOpen(item.category) },
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier.height(32.dp)
                        ) { Text(stringResource(R.string.home_connect)) }
                        OverviewStatus.READY -> {
                            val amount = item.amount!!
                            val card = item.category == AccountCategory.CREDIT_CARDS
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    CurrencyFormatter.formatCurrency(if (card) amount.abs() else amount, item.currency),
                                    style = MaterialTheme.typography.titleMedium.tabular(),
                                    color = if (card && amount.signum() < 0) colors.income else MaterialTheme.colorScheme.onSurface
                                )
                                if (card && amount.signum() != 0) {
                                    Text(
                                        stringResource(if (amount.signum() > 0) R.string.home_owed else R.string.home_overpaid),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OverviewStatus.LOADING -> Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> Text(
                            stringResource(if (item.status == OverviewStatus.UNAVAILABLE) R.string.overview_unavailable else R.string.overview_sources),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
    transactions: List<TransactionEntity>,
    decorate: (TransactionEntity) -> TransactionDecoration,
    targetName: (TransactionEntity) -> String?,
    mainCurrency: String,
    isLoading: Boolean,
    onTransactionClick: (TransactionEntity) -> Unit,
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
                    transactions.groupBy { it.dateTime.toLocalDate() }.forEach { (day, rows) ->
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
                            val decoration = remember(transaction, decorate) { decorate(transaction) }
                            RecentTransactionRow(transaction, decoration, targetName(transaction), mainCurrency) {
                                onTransactionClick(transaction)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentTransactionRow(
    transaction: TransactionEntity,
    decoration: TransactionDecoration,
    targetName: String?,
    mainCurrency: String,
    onClick: () -> Unit
) {
    val colors = moneyColors
    val type = transaction.transactionType
    val incoming = type == TransactionType.INCOME || type == TransactionType.BORROWED
    val transfer = type == TransactionType.TRANSFER
    val accountName = decoration.account?.bankName
    val category = io.github.hootkit_dev.hootbook.presentation.common.categoryName(decoration.subcategory?.name ?: decoration.category?.name ?: transaction.category)
    val subtitle = if (transfer && accountName != null && targetName != null) "$accountName → $targetName"
        else listOfNotNull(category.takeIf { it.isNotBlank() }, accountName).joinToString("　")
    val sign = when {
        incoming -> "+"
        transfer || type == TransactionType.BALANCE_UPDATE -> ""
        else -> "−"
    }
    val amountColor = when {
        incoming -> colors.income
        transfer -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    // Under the amount: what reached the other currency, or the amount in the main currency
    val received = transaction.toAmount
    val secondLine = when {
        transfer && received != null ->
            stringResource(R.string.home_received, CurrencyFormatter.formatCurrency(received, transaction.toCurrency ?: transaction.currency))
        !transaction.currency.equals(mainCurrency, ignoreCase = true) && decoration.convertedAmount != null ->
            CurrencyFormatter.formatCurrency(decoration.convertedAmount, mainCurrency)
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BrandIcon(
            merchantName = transaction.displayTitle(),
            size = 40.dp,
            categoryEntity = decoration.category,
            subcategoryEntity = decoration.subcategory,
            category = transaction.category,
            subcategory = transaction.subcategory,
            accountIconResId = decoration.account?.iconResId ?: 0,
            accountIconName = decoration.account?.iconName,
            accountColorHex = decoration.account?.color
        )
        Column(Modifier.weight(1f)) {
            Text(transaction.displayTitle(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(sign + transaction.formatAmount(), style = MaterialTheme.typography.titleSmall.tabular(), color = amountColor, maxLines = 1)
            if (secondLine != null) {
                Text(secondLine, style = MaterialTheme.typography.bodySmall.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
