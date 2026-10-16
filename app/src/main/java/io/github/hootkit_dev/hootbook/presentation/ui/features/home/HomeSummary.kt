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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
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
        withStyle(SpanStyle(fontSize = symbolStyle.fontSize, fontWeight = FontWeight.SemiBold)) { append(symbol) }
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
    debts: DebtSummary? = null,
    displayTotals: Map<String, ConvertedMoney> = emptyMap(),
    onOpenLendBorrow: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp)) {
        Row(
            modifier = Modifier.heightIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onOpenAccounts)
                    .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.net_worth_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (canChangeCurrency) {
                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onCurrencyClick)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(currency, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Row(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onToggleBreakdown)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.home_by_category), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Icon(if (breakdownShown) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(2.dp))
        val debtRecConv = displayTotals["debtReceivable"]
        val debtPayConv = displayTotals["debtPayable"]
        val useConvDebt = (debtRecConv == null || debtRecConv.complete) && (debtPayConv == null || debtPayConv.complete)
        val debtCurrency = if (useConvDebt && (debtRecConv != null || debtPayConv != null)) currency else debts?.currency.orEmpty()
        val lentAmount = (if (useConvDebt) debtRecConv?.amount else null) ?: debts?.receivable ?: BigDecimal.ZERO
        val borrowedAmount = (if (useConvDebt) debtPayConv?.amount else null) ?: debts?.payable ?: BigDecimal.ZERO
        val hasDebts = debts != null && (lentAmount.signum() != 0 || borrowedAmount.signum() != 0)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = amountWithSmallSymbol(netWorth, currency, MaterialTheme.typography.titleLarge, customSymbols),
                style = MaterialTheme.typography.headlineLarge.tabular(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).clickable(onClick = onOpenAccounts)
            )
            if (hasDebts) {
                val debtParts = buildList {
                    if (lentAmount.signum() != 0) {
                        add("${stringResource(R.string.type_lent)} ${CurrencyFormatter.format(lentAmount, debtCurrency, customSymbols)}")
                    }
                    if (borrowedAmount.signum() != 0) {
                        add("${stringResource(R.string.type_borrowed)} ${CurrencyFormatter.format(borrowedAmount, debtCurrency, customSymbols)}")
                    }
                }.joinToString(" · ")
                Row(
                    modifier = Modifier
                        .padding(start = 8.dp, bottom = 3.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            val filter = when {
                                lentAmount.signum() != 0 && borrowedAmount.signum() == 0 ->
                                    io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow.LendBorrowFilter.YOU_GET.name
                                borrowedAmount.signum() != 0 && lentAmount.signum() == 0 ->
                                    io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow.LendBorrowFilter.YOU_OWE.name
                                else -> null
                            }
                            onOpenLendBorrow(filter)
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = debtParts,
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (!complete) Text(stringResource(R.string.core_complete_incomplete), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * This month: compact header with net income/spending below the title and an optional outstanding
 * receivable pill, then income and spending bars with inline collected/receivable notes.
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
    val converted = display.filterKeys { it != "monthly" }
    val showConverted = converted.isNotEmpty() && converted.values.all { it.complete }
    val currency = if (showConverted) converted.values.first().currency else summary.currency
    val lastIncome = (if (showConverted) display["lastIncome"]?.amount else null) ?: lastSummary?.income ?: BigDecimal.ZERO
    val lastExpenses = (if (showConverted) display["lastExpense"]?.amount else null) ?: lastSummary?.expense ?: BigDecimal.ZERO
    val inc = ((if (showConverted) display["income"]?.amount else null) ?: summary.income).abs()
    val exp = ((if (showConverted) display["expense"]?.amount else null) ?: summary.expense).abs()
    val net = (if (showConverted) display["net"]?.amount else null) ?: summary.net
    val receivable = (if (showConverted) display["receivable"]?.amount else null) ?: summary.receivable
    val collected = (if (showConverted) display["collected"]?.amount else null) ?: summary.collected
    val outstanding = (if (showConverted) display["outstanding"]?.amount else null) ?: summary.outstanding
    val scale = listOf(inc, exp, lastIncome.abs(), lastExpenses.abs()).maxOf { it }.takeIf { it.signum() > 0 }
    val empty = inc.signum() == 0 && exp.signum() == 0 && receivable.signum() == 0 && collected.signum() == 0
    fun money(v: BigDecimal) = CurrencyFormatter.format(v, currency, customSymbols)

    Surface(onClick = onClick, shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_month_title, monthName),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
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
                text = amountWithSmallSymbol(net.abs(), currency, MaterialTheme.typography.titleLarge, customSymbols),
                style = MaterialTheme.typography.headlineMedium.tabular(),
                color = when {
                    empty || net.signum() == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                    net.signum() > 0 -> colors.income
                    else -> colors.expense
                }
            )
            if (!summary.complete || lastSummary?.complete == false || converted.values.any { !it.complete }) {
                Text(stringResource(R.string.core_complete_incomplete), style = MaterialTheme.typography.bodySmall)
            }
            if (!empty) {
                Spacer(Modifier.height(10.dp))
                MoneyBar(
                    icon = Icons.Rounded.SouthWest,
                    label = stringResource(R.string.home_in),
                    inlineBadge = if (collected > BigDecimal.ZERO) "${stringResource(R.string.core_collected_short)} ${money(collected)}" else null,
                    amount = money(inc),
                    fraction = scale?.let { inc.toFloat() / it.toFloat() } ?: 0f,
                    lastFraction = scale?.let { lastIncome.abs().toFloat() / it.toFloat() },
                    color = colors.income,
                    container = colors.incomeContainer,
                    onContainer = colors.onIncomeContainer
                )
                Spacer(Modifier.height(8.dp))
                val outInlineBadges = buildList {
                    if (receivable > BigDecimal.ZERO) add("${stringResource(R.string.core_receivable_short)} ${money(receivable)}")
                    if (outstanding > BigDecimal.ZERO) add("${stringResource(R.string.core_outstanding_short)} ${money(outstanding)}")
                }.joinToString(" · ").ifEmpty { null }
                MoneyBar(
                    icon = Icons.Rounded.NorthEast,
                    label = stringResource(R.string.home_out),
                    inlineBadge = outInlineBadges,
                    amount = money(exp),
                    fraction = scale?.let { exp.toFloat() / it.toFloat() } ?: 0f,
                    lastFraction = scale?.let { lastExpenses.abs().toFloat() / it.toFloat() },
                    color = colors.expense,
                    container = colors.expenseContainer,
                    onContainer = colors.onExpenseContainer
                )
            }
            if (lastIncome.signum() != 0 || lastExpenses.signum() != 0) {
                HorizontalDivider(Modifier.padding(top = 10.dp, bottom = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                val lastNet = (if (showConverted) display["lastNet"]?.amount else null) ?: lastSummary!!.net
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The same tick as on the bars, as the note's legend
                    Box(
                        Modifier
                            .size(width = 3.dp, height = 11.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(1.5.dp))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(
                            R.string.home_last_month_note,
                            money(lastIncome.abs()), money(lastExpenses.abs()),
                            if (lastNet.signum() >= 0) stringResource(R.string.home_last_net_income, money(lastNet))
                            else stringResource(R.string.home_last_net_spending, money(lastNet.abs()))
                        ),
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
    inlineBadge: String? = null,
    amount: String,
    fraction: Float,
    lastFraction: Float?,
    color: Color,
    container: Color,
    onContainer: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(container), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(16.dp), tint = onContainer)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                if (inlineBadge != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "· $inlineBadge",
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(amount, style = MaterialTheme.typography.titleSmall.tabular())
            }
            Spacer(Modifier.height(4.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
                lastFraction?.takeIf { it > 0f }?.let { last ->
                    Box(
                        Modifier
                            .offset(x = (maxWidth - 2.dp) * last.coerceIn(0f, 1f))
                            .size(width = 2.dp, height = 12.dp)
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
    connections: List<BrokerageConnection> = emptyList(),
    cardStatuses: Map<String, CardStatus> = emptyMap(),
    // The nearest card payment due, said on the credit card row
    modifier: Modifier = Modifier
) {
    val locale = locale()
    val grouped = overview?.accounts.orEmpty().groupBy { it.category() }
    val shown = if (overview == null) emptyList() else
        (grouped + (AccountCategory.INVESTMENTS to grouped[AccountCategory.INVESTMENTS].orEmpty())).entries.sortedBy { it.key.ordinal }
    val nextCardDue = remember(overview, cardStatuses) {
        val activeDueDates = overview?.accounts.orEmpty()
            .filter { !it.archived && it.kind == AccountKind.CREDIT_CARD }
            .mapNotNull { card ->
                val mainDebt = card.pockets.firstOrNull { it.currency == card.mainCurrency }?.balance ?: BigDecimal.ZERO
                val status = cardStatuses[card.id]
                when {
                    status?.outstanding != null && status.outstanding!!.signum() > 0 -> status.dueDate
                    status != null && mainDebt.signum() > 0 -> status.dueDate
                    else -> null
                }
            }
        activeDueDates.minByOrNull { it.coreDateText() } ?: overview?.nearestDue?.dueDate
    }
    val colors = moneyColors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        shown.forEachIndexed { index, item ->
            val top = if (index == 0) 24.dp else 6.dp
            val bottom = if (index == shown.lastIndex) 24.dp else 6.dp
            val investments = item.key == AccountCategory.INVESTMENTS
            val empty = item.value.isEmpty() && (!investments || connections.isEmpty())
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
                        Text(stringResource(item.key.titleRes), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when {
                                empty -> stringResource(R.string.home_connect_hint)
                                investments && connections.isNotEmpty() -> pluralStringResource(R.plurals.broker_connections, connections.size, connections.size)
                                item.key == AccountCategory.CREDIT_CARDS && nextCardDue != null ->
                                    stringResource(R.string.home_cards_due, item.value.size, nextCardDue.coreDateText(DateTimeFormatter.ofPattern(if (locale.language == "zh") "M月d日" else "MMM d", locale)))
                                else -> stringResource(R.string.overview_count, item.value.size)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (item.key == AccountCategory.CREDIT_CARDS) overview?.creditAvailable?.let { available ->
                        Column(horizontalAlignment = Alignment.End) {
                            Text(CurrencyFormatter.formatCurrency(available.amount, available.currency),
                                style = MaterialTheme.typography.titleMedium.tabular(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.label_available), style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (empty) OutlinedButton(onClick = { onOpen(item.key) }, contentPadding = PaddingValues(horizontal = 16.dp), modifier = Modifier.height(32.dp)) {
                        Text(stringResource(R.string.home_connect))
                    } else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

internal enum class CreditCardReminderKind {
    OVERDUE,
    PAYMENT_DUE,
    STATEMENT_SOON,
}

internal data class CreditCardReminder(
    val account: Account,
    val kind: CreditCardReminderKind,
    val amount: BigDecimal,
    val currency: String,
    val targetDate: LocalDate,
    val daysDiff: Int,
)

internal fun buildCreditCardReminders(
    accounts: List<Account>,
    cardStatuses: Map<String, CardStatus>,
    today: LocalDate = LocalDate.now(),
): List<CreditCardReminder> {
    val reminders = mutableListOf<CreditCardReminder>()
    for (account in accounts) {
        if (account.archived || account.kind != AccountKind.CREDIT_CARD) continue
        val status = cardStatuses[account.id]
        val mainBalance = account.pockets.firstOrNull { it.currency == account.mainCurrency }?.balance ?: BigDecimal.ZERO
        val outstanding = status?.outstanding
        if (status != null && outstanding != null && outstanding.signum() > 0) {
            val dueDate = LocalDate.parse(status.dueDate.coreDateText())
            val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate).toInt()
            if (status.overdue || daysLeft < 0) {
                reminders += CreditCardReminder(
                    account = account,
                    kind = CreditCardReminderKind.OVERDUE,
                    amount = outstanding,
                    currency = status.currency,
                    targetDate = dueDate,
                    daysDiff = (-daysLeft).coerceAtLeast(1),
                )
            } else {
                reminders += CreditCardReminder(
                    account = account,
                    kind = CreditCardReminderKind.PAYMENT_DUE,
                    amount = outstanding,
                    currency = status.currency,
                    targetDate = dueDate,
                    daysDiff = daysLeft,
                )
            }
        } else if (status != null && status.statementAmount == null && status.statementDate == null && mainBalance.signum() > 0) {
            val dueDate = LocalDate.parse(status.dueDate.coreDateText())
            val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate).toInt()
            if (daysLeft in 0..7) {
                reminders += CreditCardReminder(
                    account = account,
                    kind = CreditCardReminderKind.PAYMENT_DUE,
                    amount = mainBalance,
                    currency = account.mainCurrency,
                    targetDate = dueDate,
                    daysDiff = daysLeft,
                )
            }
        } else if (mainBalance.signum() > 0 && account.statementDay != null) {
            val statementDay = account.statementDay!!.toInt()
            val thisMonthStatement = today.withDayOfMonth(minOf(statementDay, today.lengthOfMonth()))
            val nextStatement = if (thisMonthStatement > today) {
                thisMonthStatement
            } else {
                val nextMonth = today.plusMonths(1)
                nextMonth.withDayOfMonth(minOf(statementDay, nextMonth.lengthOfMonth()))
            }
            val daysUntilStatement = java.time.temporal.ChronoUnit.DAYS.between(today, nextStatement).toInt()
            if (daysUntilStatement in 0..5) {
                reminders += CreditCardReminder(
                    account = account,
                    kind = CreditCardReminderKind.STATEMENT_SOON,
                    amount = mainBalance,
                    currency = account.mainCurrency,
                    targetDate = nextStatement,
                    daysDiff = daysUntilStatement,
                )
            }
        }
    }
    return reminders.sortedWith(
        compareBy<CreditCardReminder> { it.kind.ordinal }
            .thenBy { if (it.kind == CreditCardReminderKind.OVERDUE) -it.daysDiff else it.daysDiff }
    )
}

@Composable
internal fun UrgentNotificationsSection(
    pendingConflictCount: Int,
    pendingAiReview: AiPendingReview?,
    cardReminders: List<CreditCardReminder>,
    upcomingSubscriptions: List<Subscription>,
    subscriptionSummary: SubscriptionSummary?,
    monthlySubscriptions: ConvertedMoney?,
    customSymbols: Map<String, String>,
    categoriesMap: Map<String, Category>,
    subcategoriesMap: Map<String, Subcategory>,
    onConflictClick: () -> Unit,
    onAiReviewClick: () -> Unit,
    onAccountClick: (String) -> Unit,
    onSubscriptionsClick: () -> Unit,
    subscriptionsModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val hasConflict = pendingConflictCount > 0
    val hasAiReview = pendingAiReview != null
    val hasSubscriptions = upcomingSubscriptions.isNotEmpty()
    val totalCount = (if (hasConflict) 1 else 0) +
        (if (hasAiReview) 1 else 0) +
        cardReminders.size +
        (if (hasSubscriptions) 1 else 0)
    if (totalCount == 0) return

    val locale = locale()
    val dateFormat = remember(locale) {
        DateTimeFormatter.ofPattern(if (locale.language == "zh") "M月d日" else "MMM d", locale)
    }

    fun shapeFor(index: Int): RoundedCornerShape {
        val top = if (index == 0) 16.dp else 4.dp
        val bottom = if (index == totalCount - 1) 16.dp else 4.dp
        return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    }

    var currentIndex = 0
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (hasConflict) {
            val shape = shapeFor(currentIndex++)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .clickable(onClick = onConflictClick),
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SyncProblem,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) {
                                append(stringResource(R.string.home_sync_conflict_banner_title, pendingConflictCount))
                            }
                            append(" · ")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) {
                                append(stringResource(R.string.home_sync_conflict_banner_subtitle))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        pendingAiReview?.let { pending ->
            val shape = shapeFor(currentIndex++)
            val subtitle = pending.review.summary?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.home_ai_pending_banner_subtitle)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .clickable(onClick = onAiReviewClick),
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) {
                                append(stringResource(R.string.home_ai_pending_banner_title, pending.review.changes.size))
                            }
                            append(" · ")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                append(subtitle)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        cardReminders.forEach { reminder ->
            val shape = shapeFor(currentIndex++)
            val isOverdue = reminder.kind == CreditCardReminderKind.OVERDUE
            val dateText = reminder.targetDate.format(dateFormat)
            val subtitle = when (reminder.kind) {
                CreditCardReminderKind.OVERDUE ->
                    stringResource(R.string.card_overdue, reminder.daysDiff)
                CreditCardReminderKind.PAYMENT_DUE -> {
                    if (reminder.daysDiff == 0) stringResource(R.string.card_due_today)
                    else stringResource(R.string.card_due_on, dateText)
                }
                CreditCardReminderKind.STATEMENT_SOON -> {
                    if (reminder.daysDiff == 0) stringResource(R.string.card_statement_today)
                    else stringResource(R.string.card_statement_on, dateText)
                }
            }
            val formattedAmount = CurrencyFormatter.format(reminder.amount, reminder.currency, customSymbols)
            val statusColor = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .clickable { onAccountClick(reminder.account.id) },
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(
                                if (isOverdue) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.secondaryContainer
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CreditCard,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = if (isOverdue) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) {
                                append(reminder.account.name)
                            }
                            append(" · ")
                            withStyle(SpanStyle(color = statusColor)) {
                                append(subtitle)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formattedAmount,
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        fontWeight = FontWeight.SemiBold,
                        color = if (isOverdue) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (hasSubscriptions) {
            val shape = shapeFor(currentIndex++)
            val monthlyAmount = CurrencyFormatter.format(
                monthlySubscriptions?.amount ?: subscriptionSummary?.monthly.takeIf { monthlySubscriptions == null },
                monthlySubscriptions?.currency ?: subscriptionSummary?.currency.orEmpty(),
                customSymbols,
            )
            val incomplete = subscriptionSummary?.complete == false || monthlySubscriptions?.complete == false
            val subtitleText = buildString {
                append(monthlyAmount)
                append(" ")
                append(stringResource(R.string.per_month))
                if (incomplete) {
                    append(" ")
                    append(stringResource(R.string.core_complete_incomplete))
                }
            }
            Surface(
                modifier = subscriptionsModifier
                    .fillMaxWidth()
                    .clip(shape)
                    .clickable(onClick = onSubscriptionsClick),
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EventRepeat,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) {
                                append(stringResource(R.string.reminder_due_title, upcomingSubscriptions.size))
                            }
                            append(" · ")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                append(subtitleText)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    io.github.hootkit_dev.hootbook.presentation.ui.components.SubscriptionIconsStack(
                        subscriptions = upcomingSubscriptions,
                        iconSize = 20.dp,
                        borderColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        categoriesMap = categoriesMap,
                        subcategoriesMap = subcategoriesMap,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
        Row(Modifier.fillMaxWidth().heightIn(min = 34.dp).padding(start = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_recent),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onViewAll)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.home_all), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
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
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
                else -> Column(Modifier.padding(bottom = 6.dp)) {
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
                            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 2.dp)
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
