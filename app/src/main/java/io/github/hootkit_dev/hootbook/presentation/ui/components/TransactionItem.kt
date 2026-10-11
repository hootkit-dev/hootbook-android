package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreDisplayedMoney
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreRecordIcon
import io.github.hootkit_dev.hootbook.presentation.core.transactions.displayLabel
import io.github.hootkit_dev.hootbook.presentation.ui.theme.*

/** Core records remain the source of truth; this component only selects presentation. */
@Composable
fun TransactionItem(
    transaction: Transaction,
    modifier: Modifier = Modifier,
    account: Account? = null,
    category: Category? = null,
    targetAccount: Account? = null,
    currentAccountId: String? = null,
    showDate: Boolean = true,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onSelectionToggle: () -> Unit = {},
    convertedMoney: CoreDisplayedMoney? = null,
    customSymbols: Map<String, String> = emptyMap(),
    merchantIcon: Any? = null,
    shape: Shape = RoundedCornerShape(20.dp),
) {
    val brandIcon = io.github.hootkit_dev.hootbook.presentation.common.icons.BrandIcons.getIconResource(transaction.merchant)
    val child = category?.subcategories?.find { it.id == transaction.subcategoryId }
    val receiving = transaction.kind == TransactionKind.TRANSFER && currentAccountId != null &&
        transaction.toAccountId == currentAccountId && transaction.accountId != currentAccountId
    val amount = if (receiving) transaction.toAmount ?: transaction.amount else transaction.amount
    val currency = if (receiving) transaction.toCurrency ?: transaction.currency else transaction.currency
    val color = transactionColor(transaction.kind)
    val sign = when {
        receiving -> "+ "
        transaction.kind == TransactionKind.TRANSFER && currentAccountId == transaction.accountId -> "− "
        transaction.kind in listOf(TransactionKind.INCOME, TransactionKind.LOAN_IN) -> "+ "
        transaction.kind in listOf(TransactionKind.EXPENSE, TransactionKind.LOAN_OUT) -> "− "
        else -> ""
    }
    Surface(modifier = modifier.fillMaxWidth().testTag("transaction_${transaction.id}")
        .combinedClickable(onClick = if (isSelectionMode) onSelectionToggle else onClick, onLongClick = onLongClick),
        shape = shape, color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TransactionLeadingSlot(isSelectionMode, isSelected, onSelectionToggle) {
                if (merchantIcon != null || brandIcon != null) AsyncImage(merchantIcon ?: brandIcon, null, Modifier.size(40.dp))
                else CoreRecordIcon(child?.icon ?: category?.icon ?: account?.icon.orEmpty(), Modifier.size(40.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(transaction.merchant.ifBlank { category?.displayName ?: transaction.kind.displayLabel() }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(listOfNotNull(child?.displayName ?: category?.displayName, account?.name).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (transaction.kind == TransactionKind.TRANSFER) Text("${account?.name.orEmpty()} → ${targetAccount?.name.orEmpty()}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (showDate) Text(transaction.dateTime.let { io.github.hootkit_dev.hootbook.utils.DateFormats.dayTime(java.time.LocalDateTime.of(it.year, it.month.toInt(), it.day.toInt(), it.hour.toInt(), it.minute.toInt())) }, style = MaterialTheme.typography.labelSmall)
                    if (transaction.notes?.isNotBlank() == true) Icon(Icons.Rounded.Notes, stringResource(R.string.notes), Modifier.size(14.dp))
                    if (transaction.attachments.isNotEmpty()) Icon(Icons.Rounded.AttachFile, stringResource(R.string.core_full_attachment_view), Modifier.size(14.dp))
                    if (transaction.subscriptionId != null) Icon(Icons.Rounded.Repeat, stringResource(R.string.core_full_open_subscription), Modifier.size(14.dp))
                    if (transaction.deleted) Text(stringResource(R.string.core_full_label_11), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 180.dp)) {
                Text(sign + CoreMoneyPresentation.format(amount, currency, customSymbols), color = color,
                    style = MaterialTheme.typography.titleMedium, maxLines = 2, fontWeight = FontWeight.SemiBold)
                convertedMoney?.takeIf { it.currency != currency }?.let {
                    Text(CoreMoneyPresentation.format(it.amount, it.currency, customSymbols), style = MaterialTheme.typography.labelSmall)
                    if (!it.complete) Text(stringResource(R.string.core_full_conversion_missing), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun transactionColor(kind: TransactionKind): Color = when (kind) {
    TransactionKind.INCOME, TransactionKind.LOAN_IN -> if (isAppInDarkTheme) income_dark else income_light
    TransactionKind.EXPENSE, TransactionKind.LOAN_OUT -> if (isAppInDarkTheme) expense_dark else expense_light
    TransactionKind.TRANSFER -> if (isAppInDarkTheme) transfer_dark else transfer_light
    TransactionKind.INVESTMENT -> if (isAppInDarkTheme) investment_dark else investment_light
    else -> MaterialTheme.colorScheme.onSurface
}
