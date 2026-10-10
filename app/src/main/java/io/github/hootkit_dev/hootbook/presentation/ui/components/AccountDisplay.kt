package io.github.hootkit_dev.hootbook.presentation.ui.components

import io.github.hootkit_dev.hootbook.data.repository.LocalAccountHoldings
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
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.domain.usecase.accountKey
import io.github.hootkit_dev.hootbook.presentation.common.icons.InstitutionCatalog
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.AccountCategory
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.category
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter

// One way to show an account everywhere: icon, name, "<type> · •••• 1234", balance.

/** "•••• 1234", or null for wallets and accounts without a number. */
fun AccountBalanceEntity.maskedNumber(): String? = if (isWallet) null else maskAccountNumber(accountLast4)

/** "•••• 1234" for the last digits of an account or card; null for none or a wallet's "wallet". */
fun maskAccountNumber(last4: String?): String? =
    if (last4.isNullOrBlank() || last4.equals("wallet", ignoreCase = true)) null else "•••• $last4"

@StringRes
fun AccountBalanceEntity.typeLabelRes(): Int = when (category()) {
    AccountCategory.WALLETS -> R.string.type_wallet
    AccountCategory.BANKS -> R.string.type_bank_account
    AccountCategory.CREDIT_CARDS -> R.string.type_credit_card
    AccountCategory.INVESTMENTS -> R.string.type_investment_account
}

/** "Bank account · •••• 1234", or just the type for wallets. */
@Composable
fun accountSubtitle(account: AccountBalanceEntity): String =
    listOfNotNull(stringResource(account.typeLabelRes()), account.maskedNumber()).joinToString(" · ")

/**
 * The institution an account belongs to, so several accounts of one bank sort together:
 * "BofA (Checking)" and "BofA (Savings)" both resolve to Bank of America. Names outside the
 * catalog fall back to the part before a parenthesised suffix.
 */
fun AccountBalanceEntity.institutionKey(): String = institutionKeyOf(bankName)

/** [AccountBalanceEntity.institutionKey] for a bank name from elsewhere, e.g. a card. */
fun institutionKeyOf(bankName: String): String =
    InstitutionCatalog.find(bankName)?.id ?: bankName.substringBefore(" (").trim().lowercase()

/** The main account's key ("bankName_last4"), as the Accounts screen stores it. */
fun Context.mainAccountKey(): String? =
    getSharedPreferences("account_prefs", Context.MODE_PRIVATE).getString("main_account", null)

/**
 * Accounts by type, in the Accounts screen's order (wallets, banks, credit cards,
 * investments). Within a type, accounts of the same bank are adjacent; the main account and
 * its bank come first, then banks and names alphabetically.
 */
fun List<AccountBalanceEntity>.groupedForDisplay(mainKey: String?): List<Pair<AccountCategory, List<AccountBalanceEntity>>> {
    val sorted = sortedForDisplay(mainKey)
    return AccountCategory.entries.mapNotNull { category ->
        sorted.filter { it.category() == category }.takeIf { it.isNotEmpty() }?.let { category to it }
    }
}

/** Accounts of the same bank adjacent; the main account and its bank first, then by name. */
fun List<AccountBalanceEntity>.sortedForDisplay(mainKey: String?): List<AccountBalanceEntity> {
    val mainInstitution = firstOrNull { it.accountKey() == mainKey }?.institutionKey()
    return sortedWith(
        compareBy<AccountBalanceEntity>(
            { it.institutionKey() != mainInstitution },
            { it.institutionKey() },
            { it.accountKey() != mainKey },
            { it.bankName.lowercase() },
            { it.accountLast4 }
        )
    )
}

/**
 * The balance an account shows: for one holding several currencies, their sum in the app's main
 * currency; otherwise its own balance.
 */
@Composable
fun accountBalanceText(account: AccountBalanceEntity): String {
    val holdings = account.accountId?.let { LocalAccountHoldings.current[it] }
    return if (holdings != null && holdings.isMultiCurrency) {
        CurrencyFormatter.formatCurrency(holdings.total, holdings.totalCurrency)
    } else {
        CurrencyFormatter.formatCurrency(account.balance, account.currency)
    }
}

/** Which currencies an account holds, e.g. "含 HKD、USD"; null when it holds one. */
@Composable
fun accountCurrenciesText(account: AccountBalanceEntity): String? {
    val holdings = account.accountId?.let { LocalAccountHoldings.current[it] }?.takeIf { it.isMultiCurrency } ?: return null
    return stringResource(
        R.string.account_holds_currencies,
        holdings.pockets.joinToString(stringResource(R.string.list_separator)) { it.currency }
    )
}

/**
 * Each currency an account holds with its own balance, one small pill each ("USD $890.00");
 * nothing when it holds one.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AccountCurrencyPills(account: AccountBalanceEntity, modifier: Modifier = Modifier) {
    val holdings = account.accountId?.let { LocalAccountHoldings.current[it] }?.takeIf { it.isMultiCurrency } ?: return
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        holdings.pockets.forEach { pocket ->
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
                        CurrencyFormatter.formatCurrency(pocket.balance, pocket.currency),
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
fun RowScope.AccountRowContent(account: AccountBalanceEntity, showBalance: Boolean = true, note: String? = null) {
    BrandIcon(
        merchantName = account.bankName,
        size = 40.dp,
        showBackground = true,
        accountIconResId = account.iconResId,
        accountIconName = account.iconName,
        accountColorHex = account.color
    )
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = account.bankName,
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
            } else if (account.isCreditCard) {
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
    account: AccountBalanceEntity,
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
fun AccountGroupHeader(category: AccountCategory, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(category.titleRes),
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
    account: AccountBalanceEntity?,
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
                    merchantName = account.bankName,
                    size = 40.dp,
                    showBackground = true,
                    accountIconResId = account.iconResId,
                    accountIconName = account.iconName,
                    accountColorHex = account.color
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.bankName,
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
