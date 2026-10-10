package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.presentation.ui.theme.AccountSurfaceElevation
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AccountSectionSummary(
    section: AccountSection,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onToggle: (() -> Unit)? = null,
    titleOverride: Int? = null
) {
    val (title, icon) = when (section.kind) {
        AccountSectionKind.WALLETS -> R.string.section_wallets to Icons.Rounded.AccountBalanceWallet
        AccountSectionKind.BANKS -> R.string.section_bank_accounts to Icons.Rounded.AccountBalance
        AccountSectionKind.CREDIT_CARDS -> R.string.section_credit_cards to Icons.Rounded.CreditCard
        AccountSectionKind.INVESTMENTS -> R.string.overview_investments to Icons.AutoMirrored.Rounded.ShowChart
    }
    val container = when (section.kind) {
        AccountSectionKind.WALLETS -> MaterialTheme.colorScheme.primaryContainer
        AccountSectionKind.BANKS -> MaterialTheme.colorScheme.secondaryContainer
        AccountSectionKind.CREDIT_CARDS -> MaterialTheme.colorScheme.tertiaryContainer
        AccountSectionKind.INVESTMENTS -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content = when (section.kind) {
        AccountSectionKind.WALLETS -> MaterialTheme.colorScheme.onPrimaryContainer
        AccountSectionKind.BANKS -> MaterialTheme.colorScheme.onSecondaryContainer
        AccountSectionKind.CREDIT_CARDS -> MaterialTheme.colorScheme.onTertiaryContainer
        AccountSectionKind.INVESTMENTS -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = AccountSurfaceElevation
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onToggle != null) Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onToggle != null) Text(stringResource(titleOverride ?: title), style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.overview_count, section.accounts.size + section.linkedCount), style = MaterialTheme.typography.bodyMedium)
                if (onToggle != null) {
                    val toggleLabel = stringResource(if (expanded) R.string.account_section_collapse_all else R.string.account_section_expand_all, stringResource(title))
                    val toggleState = stringResource(if (expanded) R.string.account_section_expanded else R.string.account_section_collapsed)
                    IconButton(
                        onClick = onToggle,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.size(48.dp).semantics { stateDescription = toggleState }
                    ) {
                        Icon(
                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = toggleLabel
                        )
                    }
                }
            }
            Text(
                stringResource(if (section.kind == AccountSectionKind.CREDIT_CARDS) R.string.account_summary_outstanding else R.string.account_summary_balance),
                style = MaterialTheme.typography.labelLarge
            )
            section.totals.forEach { (currency, total) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(currency, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        CurrencyFormatter.formatCurrency(total, currency),
                        style = if (section.totals.size == 1) MaterialTheme.typography.headlineMediumEmphasized else MaterialTheme.typography.titleLargeEmphasized,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (section.linkedCount > 0) Text(stringResource(R.string.overview_snapshot_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AccountSectionToggle(section: AccountSection, expanded: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (expanded) R.string.account_section_expanded else R.string.account_section_collapsed)
    TextButton(
        onClick = onToggle,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { stateDescription = state }
    ) {
        Text(if (expanded) stringResource(R.string.account_show_less) else stringResource(R.string.account_show_more, section.accounts.size - section.collapsedPreviewCount))
        Spacer(Modifier.width(8.dp))
        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
    }
}

/**
 * The Investments section with nothing in it yet (no broker account, no connection): its title
 * and one row that opens Add account with the Broker type, where a broker can be connected or
 * a manual broker account added.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun InvestmentsEmptySection(onAdd: () -> Unit, modifier: Modifier = Modifier, showTitle: Boolean = true) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showTitle) {
            Row(
                Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ShowChart, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.overview_investments), style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
            }
        }
        Surface(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().testTag("investments_empty_add"),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.accounts_investments_empty), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(R.string.accounts_investments_empty_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
