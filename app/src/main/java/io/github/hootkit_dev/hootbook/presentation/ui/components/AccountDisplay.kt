package io.github.hootkit_dev.hootbook.presentation.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.R
import dev.hootkit.core.Account
import dev.hootkit.core.AccountKind
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation as CoreMoneyPresentation

// One way to show an account everywhere: icon, name, "<type> · •••• 1234", balance.

/** Display helpers only; all balances come from Core. */
fun Account.maskedNumber(): String? = maskAccountNumber(last4)
fun maskAccountNumber(last4: String?): String? = last4?.takeIf { it.isNotBlank() }?.let { "•••• $it" }
@StringRes
fun AccountKind.typeLabelRes(): Int = when (this) {
    AccountKind.WALLET -> R.string.type_wallet
    AccountKind.CREDIT_CARD -> R.string.type_credit_card
    AccountKind.INVESTMENT -> R.string.type_investment_account
    else -> R.string.type_bank_account
}
@Composable
fun accountSubtitle(account: Account): String = listOfNotNull(stringResource(account.kind.typeLabelRes()), account.maskedNumber()).joinToString(" · ")
fun List<Account>.groupedForDisplay(): List<Pair<AccountKind, List<Account>>> =
    sortedWith(compareBy<Account>({ !it.isDefault }, { it.institutionKey ?: it.name }, { it.name }))
        .groupBy { it.kind }.toList()
@Composable
fun accountBalanceText(account: Account): String = CoreMoneyPresentation.formatCurrency(account.total.amount, account.total.currency)
@Composable
fun accountCurrenciesText(account: Account): String? = if (account.pockets.size > 1)
    stringResource(R.string.account_holds_currencies, account.pockets.joinToString(stringResource(R.string.list_separator)) { it.currency }) else null

/**
 * Each currency an account holds with its own balance, one small pill each ("USD $890.00");
 * nothing when it holds one.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AccountCurrencyPills(account: Account, modifier: Modifier = Modifier) {
    if (account.pockets.size < 2) return
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        account.pockets.forEach { pocket ->
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        pocket.currency,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        CoreMoneyPresentation.formatCurrency(pocket.balance, pocket.currency),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Icon, name and subtitle, plus the balance on the right unless [showBalance] is false.
 * [note] is appended to the subtitle, e.g. "Main".
 */
@Composable
fun RowScope.AccountRowContent(account: Account, showBalance: Boolean = true, note: String? = null) {
    BrandIcon(
        merchantName = account.name,
        size = 40.dp,
        showBackground = true,
        accountIconResId = 0,
        accountIconName = account.icon,
        accountColorHex = account.color
    )
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = account.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = listOfNotNull(accountSubtitle(account), note).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (note != null) 2 else 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    if (showBalance) {
        Spacer(Modifier.width(Spacing.sm))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = accountBalanceText(account),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            val currencies = accountCurrenciesText(account)
            if (currencies != null) {
                Text(
                    text = currencies,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (account.kind == AccountKind.CREDIT_CARD) {
                Text(
                    text = stringResource(R.string.outstanding_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * An account as a list row (about 64 dp). [trailing] holds a radio button, checkbox or
 * menu; without one, a selected row shows a check mark.
 */
@Composable
fun AccountRow(
    account: Account,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    showBalance: Boolean = true,
    shape: Shape = RoundedCornerShape(20.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onClick: (() -> Unit)? = null,
    // Supporting text after the account kind, e.g. what a change does to it
    note: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val color = if (selected) MaterialTheme.colorScheme.secondaryContainer else containerColor
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = Spacing.md, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccountRowContent(account, showBalance, note)
            when {
                trailing != null -> {
                    Spacer(Modifier.width(Spacing.sm))
                    trailing()
                }
                selected -> {
                    Spacer(Modifier.width(Spacing.sm))
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = color, modifier = modifier.fillMaxWidth(), content = content)
    } else {
        Surface(shape = shape, color = color, modifier = modifier.fillMaxWidth(), content = content)
    }
}

/** Header above a group of account rows, e.g. "Credit Cards". */
@Composable
fun AccountGroupHeader(category: AccountKind, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(category.typeLabelRes()),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = Spacing.sm, top = Spacing.sm, bottom = Spacing.xs)
    )
}

/**
 * The form field that shows the chosen account and opens the picker. Shows [placeholder]
 * until an account is chosen.
 */
@Composable
fun AccountField(
    account: Account?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.select_account),
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    enabled: Boolean = true
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor, disabledContainerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (account != null) {
                BrandIcon(
                    merchantName = account.name,
                    size = 40.dp,
                    showBackground = true,
                    accountIconResId = 0,
                    accountIconName = account.icon,
                    accountColorHex = account.color
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = accountSubtitle(account),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
