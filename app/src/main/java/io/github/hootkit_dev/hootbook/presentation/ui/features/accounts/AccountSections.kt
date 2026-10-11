package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hootkit.core.Account
import dev.hootkit.core.AccountKind
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreDisplayedMoney
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreRecordIcon
import io.github.hootkit_dev.hootbook.presentation.ui.theme.AccountSurfaceElevation
import io.github.hootkit_dev.hootbook.presentation.ui.components.accountSubtitle

internal val accountSectionOrder = listOf(AccountKind.WALLET, AccountKind.BANK, AccountKind.CREDIT_CARD, AccountKind.INVESTMENT, AccountKind.UNKNOWN)
internal fun accountKindTitle(kind: AccountKind): Int = when (kind) {
    AccountKind.WALLET -> R.string.section_wallets
    AccountKind.BANK -> R.string.section_bank_accounts
    AccountKind.CREDIT_CARD -> R.string.section_credit_cards
    AccountKind.INVESTMENT -> R.string.overview_investments
    AccountKind.UNKNOWN -> R.string.core_accounts_34
}
internal fun accountKindIcon(kind: AccountKind): ImageVector = when (kind) {
    AccountKind.WALLET -> Icons.Rounded.AccountBalanceWallet
    AccountKind.BANK -> Icons.Rounded.AccountBalance
    AccountKind.CREDIT_CARD -> Icons.Rounded.CreditCard
    AccountKind.INVESTMENT -> Icons.AutoMirrored.Rounded.ShowChart
    AccountKind.UNKNOWN -> Icons.Rounded.AccountBalance
}

/** Grouping and disclosure are presentation only; all amounts are the generated Core values. */
@Composable
internal fun AccountSectionSummary(kind: AccountKind, count: Int, expanded: Boolean, onToggle: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(accountKindIcon(kind), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(stringResource(accountKindTitle(kind)), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.overview_count, count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onToggle != null) IconButton(onClick = onToggle) { Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            stringResource(if (expanded) R.string.account_section_expanded else R.string.account_section_collapsed)) }
    }
}

@Composable
internal fun CompactAccountCard(account: Account, symbols: Map<String, String>, onClick: () -> Unit, modifier: Modifier = Modifier, display: CoreDisplayedMoney? = null) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth().testTag("account_${account.id}"), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = AccountSurfaceElevation)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountIcon(account)
            Column(Modifier.weight(1f)) {
                Text(account.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val note = listOfNotNull(accountSubtitle(account), if (account.isDefault) stringResource(R.string.default_account_note) else null,
                    if (account.archived) stringResource(R.string.core_full_label_80) else null).joinToString(" · ")
                if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (account.pockets.size > 1) Text(account.pockets.joinToString(" · ") { it.currency }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 168.dp)) {
                Text(CoreMoneyPresentation.format(account.total.amount, account.total.currency, symbols), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), maxLines = 2)
                display?.takeIf { it.currency != account.total.currency }?.let { Text(CoreMoneyPresentation.format(it.amount, it.currency, symbols), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (!account.totalComplete || display?.complete == false) Text(stringResource(R.string.core_accounts_6), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
internal fun AccountIcon(account: Account, modifier: Modifier = Modifier) {
    val background = runCatching { Color(android.graphics.Color.parseColor(account.color)) }.getOrNull() ?: MaterialTheme.colorScheme.secondaryContainer
    Box(modifier.size(44.dp).clip(CircleShape).background(background.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        if (account.icon.isNotBlank()) CoreRecordIcon(account.icon, Modifier.size(28.dp))
        else Icon(accountKindIcon(account.kind), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun InvestmentsEmptySection(onAdd: () -> Unit, modifier: Modifier = Modifier, showTitle: Boolean = true) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showTitle) AccountSectionSummary(AccountKind.INVESTMENT, 0, false, null)
        Surface(onClick = onAdd, modifier = Modifier.fillMaxWidth().testTag("investments_empty_add"), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer) }
                Column(Modifier.weight(1f)) { Text(stringResource(R.string.accounts_investments_empty), style = MaterialTheme.typography.bodyLarge); Text(stringResource(R.string.accounts_investments_empty_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
