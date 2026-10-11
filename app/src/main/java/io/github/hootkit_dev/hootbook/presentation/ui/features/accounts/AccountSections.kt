package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hootkit.core.Account
import dev.hootkit.core.AccountKind
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.accounts.CoreAccountsUiState
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreDisplayedMoney
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreRecordIcon
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListSection
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListSectionScope
import io.github.hootkit_dev.hootbook.presentation.ui.components.ScreenContentPadding
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionSpacing
import io.github.hootkit_dev.hootbook.presentation.ui.components.accountSubtitle

internal val accountSectionOrder = listOf(AccountKind.WALLET, AccountKind.BANK, AccountKind.CREDIT_CARD, AccountKind.INVESTMENT, AccountKind.UNKNOWN)
internal fun accountKindTitle(kind: AccountKind): Int = when (kind) {
    AccountKind.WALLET -> R.string.section_wallets
    AccountKind.BANK -> R.string.section_bank_accounts
    AccountKind.CREDIT_CARD -> R.string.section_credit_cards
    AccountKind.INVESTMENT -> R.string.overview_investments
    AccountKind.UNKNOWN -> R.string.account_kind_unknown
}
internal fun accountKindIcon(kind: AccountKind): ImageVector = when (kind) {
    AccountKind.WALLET -> Icons.Rounded.AccountBalanceWallet
    AccountKind.BANK -> Icons.Rounded.AccountBalance
    AccountKind.CREDIT_CARD -> Icons.Rounded.CreditCard
    AccountKind.INVESTMENT -> Icons.AutoMirrored.Rounded.ShowChart
    AccountKind.UNKNOWN -> Icons.Rounded.AccountBalance
}

/**
 * All accounts, one titled group per kind (or only [initialKind]). Investments always show,
 * with a row to connect a broker or add one when there are none.
 */
@Composable
internal fun ManageAccountsList(state: CoreAccountsUiState, initialKind: AccountKind?, onSelect: (String) -> Unit,
    onAdd: (AccountKind?) -> Unit, modifier: Modifier = Modifier) {
    val accounts = state.overview?.accounts.orEmpty()
    LazyColumn(modifier, contentPadding = ScreenContentPadding, verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
        // Nothing until the first load: a list that starts with only the (always shown) investments
        // group keeps that group anchored at the top when the groups above it arrive.
        if (state.overview == null) return@LazyColumn
        if (accounts.isEmpty() && !state.loading) item(key = "empty") {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.no_accounts_yet), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.add_account_prompt), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        accountSectionOrder.filter { initialKind == null || it == initialKind }.forEach { kind ->
            val rows = accounts.filter { it.kind == kind }
            if (rows.isEmpty() && kind != AccountKind.INVESTMENT) return@forEach
            item(key = "section_${kind.name}") {
                ListSection(title = stringResource(accountKindTitle(kind)) + if (rows.isEmpty()) "" else " · ${rows.size}") {
                    rows.forEach { account -> accountRow(account, state.customSymbols, state.displayValues[account.id]) { onSelect(account.id) } }
                    if (kind == AccountKind.INVESTMENT && rows.isEmpty()) addBrokerRow { onAdd(AccountKind.INVESTMENT) }
                }
            }
        }
    }
}

/** An account: icon, name with kind / last 4 / status, and its total (converted below it when shown in another currency). */
internal fun ListSectionScope.accountRow(account: Account, symbols: Map<String, String>, display: CoreDisplayedMoney?, onClick: () -> Unit) = item(
    modifier = Modifier.testTag("account_${account.id}"),
    leading = { AccountIcon(account) },
    headline = { Text(account.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
    supporting = {
        val note = listOfNotNull(accountSubtitle(account),
            if (account.isDefault) stringResource(R.string.account_status_default) else null,
            if (account.archived) stringResource(R.string.account_status_archived) else null,
            if (account.pockets.size > 1) account.pockets.joinToString("/") { it.currency } else null).joinToString(" · ")
        Text(note, maxLines = 2, overflow = TextOverflow.Ellipsis)
    },
    trailing = {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 168.dp)) {
            Text(CoreMoneyPresentation.format(account.total.amount, account.total.currency, symbols),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), maxLines = 2)
            display?.takeIf { it.currency != account.total.currency }?.let {
                Text(CoreMoneyPresentation.format(it.amount, it.currency, symbols), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!account.totalComplete || display?.complete == false) Text(stringResource(R.string.account_valuation_incomplete),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        }
    },
    onClick = onClick,
)

/** "Connect a broker or add a broker account": the investments group's row when it is empty. */
internal fun ListSectionScope.addBrokerRow(onClick: () -> Unit) = item(
    modifier = Modifier.testTag("investments_empty_add"),
    leading = {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    },
    headline = { Text(stringResource(R.string.accounts_investments_empty)) },
    supporting = { Text(stringResource(R.string.accounts_investments_empty_body)) },
    chevron = true, onClick = onClick,
)

/** The broker row on its own, for the add-account form. */
@Composable
internal fun InvestmentsEmptySection(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    ListSection(modifier) { addBrokerRow(onAdd) }
}

@Composable
internal fun AccountIcon(account: Account, modifier: Modifier = Modifier) {
    val background = runCatching { Color(android.graphics.Color.parseColor(account.color)) }.getOrNull() ?: MaterialTheme.colorScheme.secondaryContainer
    Box(modifier.size(44.dp).clip(CircleShape).background(background.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        if (account.icon.isNotBlank()) CoreRecordIcon(account.icon, Modifier.size(28.dp))
        else Icon(accountKindIcon(account.kind), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
