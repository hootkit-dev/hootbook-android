@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.CreditScore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hootkit.core.Account
import dev.hootkit.core.AccountKind
import dev.hootkit.core.LocalDate
import dev.hootkit.core.Pocket
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.ui.CorePlatformAssets
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountCard
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListSection
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListSectionScope
import io.github.hootkit_dev.hootbook.presentation.ui.components.MenuAction
import io.github.hootkit_dev.hootbook.presentation.ui.components.OverflowMenu
import io.github.hootkit_dev.hootbook.presentation.ui.components.ScreenContentPadding
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionSpacing
import io.github.hootkit_dev.hootbook.presentation.ui.components.TooltipIconButton
import io.github.hootkit_dev.hootbook.presentation.ui.components.TransactionItem
import io.github.hootkit_dev.hootbook.utils.DateFormats

/** One account's page: summary, then statement, currencies, recent transactions and balance. */
@Composable
internal fun AccountDetail(
    state: CoreAccountsUiState,
    account: Account,
    assets: CorePlatformAssets?,
    onSelectCurrency: (String) -> Unit,
    onEditPocket: (PocketEditor) -> Unit,
    onDeletePocket: (Pocket) -> Unit,
    onTransactions: () -> Unit,
    onTransaction: (String) -> Unit,
    onHistory: () -> Unit,
    onAnchors: () -> Unit,
    onBrokerage: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val symbols = state.customSymbols
    val enabled = !state.saving
    LazyColumn(modifier, contentPadding = ScreenContentPadding, verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
        item(key = "summary") { AccountSummary(state, account) }
        if (account.broker != null || account.securities != null) item(key = "broker") {
            ListSection(title = stringResource(R.string.account_section_broker)) {
                account.broker?.let { broker ->
                    item(headline = { Text(stringResource(R.string.account_open_broker, broker.brokerAccountId)) },
                        supporting = account.securities?.let { { Text(stringResource(R.string.account_securities, CoreMoneyPresentation.format(it.amount, it.currency, symbols))) } },
                        chevron = true, onClick = { if (enabled) onBrokerage(broker.connectionId, broker.brokerAccountId) })
                } ?: account.securities?.let {
                    item(headline = { Text(stringResource(R.string.account_securities, CoreMoneyPresentation.format(it.amount, it.currency, symbols))) })
                }
            }
        }
        state.cardStatus?.let { card -> item(key = "card") {
            ListSection(title = stringResource(R.string.account_section_card)) {
                card.statementDate?.let { value(R.string.account_statement_date, it.text()) }
                value(R.string.account_due_date, card.dueDate.text(), error = card.overdue,
                    note = if (card.overdue) R.string.account_overdue else null)
                card.statementAmount?.let { value(R.string.account_statement_amount, CoreMoneyPresentation.format(it, card.currency, symbols)) }
                card.repaidSince?.let { value(R.string.account_repaid, CoreMoneyPresentation.format(it, card.currency, symbols)) }
                card.outstanding?.let { value(R.string.account_outstanding, CoreMoneyPresentation.format(it, card.currency, symbols)) }
            }
        } }
        item(key = "currencies") {
            ListSection(
                title = stringResource(R.string.account_section_currencies),
                action = { TooltipIconButton(Icons.Rounded.Add, stringResource(R.string.account_currency_add), { onEditPocket(PocketEditor(PocketAction.ADD)) }, enabled = enabled) },
            ) {
                val several = account.pockets.size > 1
                account.pockets.forEach { pocket -> pocketRow(account, pocket, state, several, enabled, onSelectCurrency, onEditPocket, onDeletePocket) }
            }
        }
        item(key = "recent") {
            ListSection(title = stringResource(R.string.account_section_recent)) {
                if (state.recent.isEmpty()) item(headline = { Text(stringResource(R.string.account_no_transactions)) })
                state.recent.forEach { transaction ->
                    row { shape ->
                        TransactionItem(transaction, account = account, category = state.categories.find { it.id == transaction.categoryId },
                            targetAccount = state.overview?.accounts?.find { it.id == transaction.toAccountId }, currentAccountId = account.id,
                            onClick = { onTransaction(transaction.id) }, customSymbols = symbols, shape = shape,
                            merchantIcon = assets?.merchantIcon(transaction.merchant))
                    }
                }
                if (state.transactionCount > 0u) item(headline = { Text(stringResource(R.string.account_all_transactions)) },
                    trailing = { Text(state.transactionCount.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    chevron = true, onClick = onTransactions)
            }
        }
        item(key = "balance") {
            val currency = state.currency.orEmpty()
            ListSection(title = if (account.pockets.size > 1) stringResource(R.string.account_section_balance_currency, currency)
                else stringResource(R.string.account_section_balance)) {
                val pocket = account.pockets.find { it.currency == currency }
                item(headline = { Text(stringResource(R.string.account_calibrate)) }, leading = { Icon(Icons.Rounded.Tune, null) },
                    chevron = true, onClick = { if (enabled && pocket != null) onEditPocket(PocketEditor.calibrate(pocket)) })
                item(headline = { Text(stringResource(R.string.account_balance_trend)) }, leading = { Icon(Icons.AutoMirrored.Rounded.ShowChart, null) },
                    chevron = true, onClick = { if (enabled) onHistory() })
                item(headline = { Text(stringResource(R.string.account_calibrations)) }, leading = { Icon(Icons.Rounded.History, null) },
                    trailing = { Text(state.anchors.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    chevron = true, onClick = onAnchors)
            }
        }
    }
}

/** The account card, its converted total and what is incomplete, then its status labels. */
@Composable
private fun AccountSummary(state: CoreAccountsUiState, account: Account) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AccountCard(account, customSymbols = state.customSymbols)
        val notes = buildList {
            state.displayValues[account.id]?.takeIf { it.currency != account.total.currency }?.let { converted ->
                add(stringResource(R.string.account_converted, CoreMoneyPresentation.format(converted.amount, converted.currency, state.customSymbols)) to false)
                if (!converted.complete) add(stringResource(R.string.core_full_conversion_missing) to true)
            }
            if (account.kind == AccountKind.CREDIT_CARD) add(stringResource(R.string.account_credit_balance_is_debt) to false)
        }
        notes.forEach { (text, error) ->
            Text(text, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (account.isDefault || account.archived) FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (account.isDefault) StatusLabel(stringResource(R.string.account_status_default))
            if (account.archived) StatusLabel(stringResource(R.string.account_status_archived))
        }
    }
}

/** A non-interactive status, such as Default or Archived. */
@Composable
internal fun StatusLabel(text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

private fun ListSectionScope.value(
    label: Int, value: String, error: Boolean = false, note: Int? = null,
) = item(
    headline = { Text(stringResource(label)) },
    supporting = note?.let { { Text(stringResource(it), color = MaterialTheme.colorScheme.error) } },
    trailing = { Text(value, style = MaterialTheme.typography.bodyLarge, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
)

private fun ListSectionScope.pocketRow(
    account: Account, pocket: Pocket, state: CoreAccountsUiState, several: Boolean, enabled: Boolean,
    onSelect: (String) -> Unit, onEdit: (PocketEditor) -> Unit, onDelete: (Pocket) -> Unit,
) = row { shape ->
    val symbols = state.customSymbols
    val main = pocket.currency == account.mainCurrency
    val supporting = listOfNotNull(
        if (main) stringResource(R.string.account_currency_main) else null,
        pocket.creditLimit?.let { stringResource(R.string.account_currency_limit, CoreMoneyPresentation.format(it, pocket.currency, symbols)) },
        pocket.availableCredit?.let { stringResource(R.string.account_currency_available, CoreMoneyPresentation.format(it, pocket.currency, symbols)) },
    ).joinToString(" · ")
    val actions = listOfNotNull(
        if (account.kind == AccountKind.CREDIT_CARD) MenuAction(stringResource(R.string.account_credit_limit), Icons.Rounded.CreditScore) { onEdit(PocketEditor.limit(pocket)) } else null,
        if (!main) MenuAction(stringResource(R.string.account_currency_delete), Icons.Rounded.Delete, destructive = true) { onDelete(pocket) } else null,
    )
    ListItem(
        headline = { Text(currencyName(pocket.currency), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supporting = supporting.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
        leading = { CurrencyBadge(pocket.currency) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(CoreMoneyPresentation.format(pocket.balance, pocket.currency, symbols), style = MaterialTheme.typography.titleMedium)
                if (actions.isNotEmpty()) OverflowMenu(actions, enabled = enabled)
            }
        },
        selected = several && pocket.currency == state.currency, shape = shape,
        padding = PaddingValues(0.dp),
        onClick = if (several) ({ onSelect(pocket.currency) }) else null,
    )
}

@Composable
private fun CurrencyBadge(code: String) {
    Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
        Text(code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun currencyName(code: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return runCatching { java.util.Currency.getInstance(code).getDisplayName(locale) }.getOrDefault(code)
}

private fun LocalDate.text(): String = DateFormats.shortDate(java.time.LocalDate.of(year, month.toInt(), day.toInt()))
