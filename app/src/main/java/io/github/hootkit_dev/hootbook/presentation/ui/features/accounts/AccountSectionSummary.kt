package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hootkit.core.Account
import dev.hootkit.core.AccountKind
import dev.hootkit.core.BrokerageConnection
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountRowContent
import io.github.hootkit_dev.hootbook.presentation.ui.theme.AccountSurfaceElevation
import java.math.BigDecimal

/**
 * An independent rounded card container for an account category (Wallets, Bank Accounts,
 * Credit Cards, Investments) with distinct visual separation, balance summary, and collapsible
 * child accounts.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AccountCategoryCard(
    kind: AccountCategory,
    accounts: List<Account>,
    modifier: Modifier = Modifier,
    connections: List<BrokerageConnection> = emptyList(),
    expanded: Boolean = false,
    onToggle: (() -> Unit)? = null,
    titleOverride: Int? = null,
    onNavigateToAccountDetail: (String) -> Unit = {},
    onBrokerage: (Long, String) -> Unit = { _, _ -> },
    onNavigateToAddAccount: (AccountCategory?) -> Unit = {},
) {
    val (title, icon) = when (kind) {
        AccountCategory.WALLETS -> R.string.section_wallets to Icons.Rounded.AccountBalanceWallet
        AccountCategory.BANKS -> R.string.section_bank_accounts to Icons.Rounded.AccountBalance
        AccountCategory.CREDIT_CARDS -> R.string.section_credit_cards to Icons.Rounded.CreditCard
        AccountCategory.INVESTMENTS -> R.string.overview_investments to Icons.AutoMirrored.Rounded.ShowChart
    }
    val iconContainerColor = when (kind) {
        AccountCategory.WALLETS -> MaterialTheme.colorScheme.primaryContainer
        AccountCategory.BANKS -> MaterialTheme.colorScheme.secondaryContainer
        AccountCategory.CREDIT_CARDS -> MaterialTheme.colorScheme.tertiaryContainer
        AccountCategory.INVESTMENTS -> MaterialTheme.colorScheme.secondaryContainer
    }
    val iconContentColor = when (kind) {
        AccountCategory.WALLETS -> MaterialTheme.colorScheme.onPrimaryContainer
        AccountCategory.BANKS -> MaterialTheme.colorScheme.onSecondaryContainer
        AccountCategory.CREDIT_CARDS -> MaterialTheme.colorScheme.onTertiaryContainer
        AccountCategory.INVESTMENTS -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    // Compute totals per currency from accounts' pockets
    val totals: List<Pair<String, BigDecimal>> = remember(accounts) {
        val map = linkedMapOf<String, BigDecimal>()
        for (account in accounts) {
            for (pocket in account.pockets) {
                map[pocket.currency] = (map[pocket.currency] ?: BigDecimal.ZERO).add(pocket.balance)
            }
            account.securities?.let { sec ->
                if (sec.amount > BigDecimal.ZERO) {
                    map[sec.currency] = (map[sec.currency] ?: BigDecimal.ZERO).add(sec.amount)
                }
            }
        }
        map.toList()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = AccountSurfaceElevation)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header Row (Clickable to toggle expand/collapse)
            val headerModifier = if (onToggle != null) {
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple()
                    ) { onToggle() }
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            }

            Row(
                modifier = headerModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(iconContainerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = iconContentColor
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(titleOverride ?: title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val countText = listOfNotNull(
                        stringResource(R.string.overview_count, accounts.size).takeIf { accounts.isNotEmpty() || connections.isEmpty() },
                        if (connections.isNotEmpty()) pluralStringResource(R.plurals.broker_connections, connections.size, connections.size) else null,
                    ).joinToString(" · ")
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (onToggle != null) {
                    val toggleLabel = stringResource(
                        if (expanded) R.string.account_section_collapse_all else R.string.account_section_expand_all,
                        stringResource(title)
                    )
                    val toggleState = stringResource(
                        if (expanded) R.string.account_section_expanded else R.string.account_section_collapsed
                    )
                    IconButton(
                        onClick = onToggle,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier
                            .size(40.dp)
                            .semantics { stateDescription = toggleState }
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = toggleLabel,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Summary Area (Totals per currency)
            if (totals.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(
                            if (kind == AccountCategory.CREDIT_CARDS) R.string.account_summary_outstanding
                            else R.string.account_summary_balance
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    totals.forEach { (currency, total) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currency,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = CoreMoneyPresentation.formatCurrency(total, currency),
                                style = if (totals.size == 1) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Expanded Accounts List
            if (expanded) {
                if (accounts.isNotEmpty() || connections.isNotEmpty()) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(10.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        accounts.forEach { account ->
                            Surface(
                                onClick = { onNavigateToAccountDetail(account.id) },
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AccountRowContent(
                                            account = account,
                                            note = if (account.isDefault) stringResource(R.string.default_account_note) else null
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    if (account.kind == AccountKind.CREDIT_CARD) {
                                        AccountCreditDetails(account)
                                    }
                                    account.broker?.let { broker ->
                                        TextButton(
                                            onClick = { onBrokerage(broker.connectionId, broker.brokerAccountId) },
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.overview_investments) + " · " + broker.asOf.coreDateText(),
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (kind == AccountCategory.INVESTMENTS) {
                            connections.forEach { connection ->
                                connection.accounts.forEach { snapshot ->
                                    io.github.hootkit_dev.hootbook.presentation.ui.features.investments.BrokerageSnapshotRow(
                                        connection,
                                        snapshot,
                                        onClick = { onBrokerage(connection.id, snapshot.accountId) }
                                    )
                                }
                                if (connection.accounts.isEmpty()) {
                                    io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem(
                                        headline = { Text(connection.label) },
                                        onClick = { onBrokerage(connection.id, "") }
                                    )
                                }
                            }
                            InvestmentsEmptySection(
                                onAdd = { onNavigateToAddAccount(AccountCategory.INVESTMENTS) },
                                showTitle = false
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Legacy summary compatibility wrapper.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AccountSectionSummary(
    kind: AccountCategory,
    accounts: List<Account>,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onToggle: (() -> Unit)? = null,
    titleOverride: Int? = null,
    connectionCount: Int = 0
) {
    AccountCategoryCard(
        kind = kind,
        accounts = accounts,
        modifier = modifier,
        expanded = expanded,
        onToggle = onToggle,
        titleOverride = titleOverride
    )
}

/**
 * The Investments section with nothing in it yet (no broker account, no connection):
 * styled as a card matching [AccountCategoryCard].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun InvestmentsEmptySection(onAdd: () -> Unit, modifier: Modifier = Modifier, showTitle: Boolean = false) {
    if (showTitle) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.overview_investments),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
            )
            InvestmentsEmptyCard(onAdd)
        }
    } else {
        InvestmentsEmptyCard(onAdd, modifier)
    }
}

@Composable
internal fun InvestmentsEmptyCard(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onAdd,
        modifier = modifier.fillMaxWidth().testTag("investments_empty_add"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = AccountSurfaceElevation)
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.accounts_investments_empty),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    stringResource(R.string.accounts_investments_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
