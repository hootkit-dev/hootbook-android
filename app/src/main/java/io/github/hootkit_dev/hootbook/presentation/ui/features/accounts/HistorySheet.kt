@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import io.github.hootkit_dev.hootbook.presentation.ui.components.maskAccountNumber
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Bag
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Card
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Clock
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Edit2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Information
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Messages
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Path
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistorySheet(
    bankName: String,
    accountLast4: String,
    balanceHistory: List<AccountBalanceEntity>,
    onDeleteBalance: (Long) -> Unit,
    onUpdateBalance: (Long, BigDecimal) -> Unit
) {
    // Get the primary currency for this account
    val accountPrimaryCurrency = remember(balanceHistory, bankName) {
        balanceHistory.firstOrNull()?.currency ?: CurrencyFormatter.getBankBaseCurrency(bankName)
    }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var editingValue by remember { mutableStateOf("") }
    var showDeleteConfirmation by remember { mutableStateOf<Long?>(null) }
    var expandedSources by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.md)
    ) {
        // Header
        Column {
            Text(
                text = stringResource(R.string.balance_history_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = listOfNotNull(bankName, maskAccountNumber(accountLast4)).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Iconax.Information,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.records_count_latest_balance_note, balanceHistory.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        if (balanceHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_balance_history_available),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Balance History List
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                contentPadding = PaddingValues(bottom = Spacing.xl)
            ) {
                items(balanceHistory) { balance ->
                    val isLatest = balance == balanceHistory.first()
                    val isOnlyRecord = balanceHistory.size == 1
                    val isExpanded = expandedSources.contains(balance.id)

                    HistoryRecordItem(
                        balance = balance,
                        isLatest = isLatest,
                        isOnlyRecord = isOnlyRecord,
                        isExpanded = isExpanded,
                        editingId = editingId,
                        editingValue = editingValue,
                        accountPrimaryCurrency = accountPrimaryCurrency,
                        onEditClick = {
                            editingId = balance.id
                            editingValue = balance.balance.toPlainString()
                        },
                        onDeleteClick = {
                            showDeleteConfirmation = balance.id
                        },
                        onEditValueChange = { value ->
                            if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                                editingValue = value
                            }
                        },
                        onSaveEdit = {
                            editingValue.toBigDecimalOrNull()?.let { newBalance ->
                                onUpdateBalance(balance.id, newBalance)
                                editingId = null
                                editingValue = ""
                            }
                        },
                        onCancelEdit = {
                            editingId = null
                            editingValue = ""
                        },
                        onToggleExpand = {
                            expandedSources = if (isExpanded) {
                                expandedSources - balance.id
                            } else {
                                expandedSources + balance.id
                            }
                        },
                        clipboard = clipboard
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    showDeleteConfirmation?.let { balanceId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = null },
            title = { Text(stringResource(R.string.delete_balance_record_title)) },
            text = { Text(stringResource(R.string.delete_balance_record_confirm)) },
            confirmButton = {
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = {
                        onDeleteBalance(balanceId)
                        showDeleteConfirmation = null
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { showDeleteConfirmation = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryRecordItem(
    balance: AccountBalanceEntity,
    isLatest: Boolean,
    isOnlyRecord: Boolean,
    isExpanded: Boolean,
    editingId: Long?,
    editingValue: String,
    accountPrimaryCurrency: String,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onEditValueChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onToggleExpand: () -> Unit,
    clipboard: ClipboardManager
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLatest) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Top Row: Date and Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Iconax.Clock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = balance.timestamp.format(
                            DateTimeFormatter.ofPattern(
                                if (java.util.Locale.getDefault().language == "zh") "yyyy年M月d日 HH:mm" else "MMM d, yyyy HH:mm"
                            )
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Rows a transaction wrote are its effect: they change with the transaction
                if (editingId != balance.id && !isOnlyRecord && balance.transactionId == null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            shapes = IconButtonDefaults.shapes(),
                            onClick = onEditClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Iconax.Edit2,
                                contentDescription = stringResource(R.string.edit),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            shapes = IconButtonDefaults.shapes(),
                            onClick = onDeleteClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Iconax.Bag,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Balance Display or Edit
            if (editingId == balance.id) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    OutlinedTextField(
                        value = editingValue,
                        onValueChange = onEditValueChange,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.new_balance_label)) },
                        prefix = {
                            Text(
                                text = CurrencyFormatter.getCurrencySymbol(accountPrimaryCurrency) + " ",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Button(
                            shapes = ButtonDefaults.shapes(),
                            onClick = onSaveEdit,
                            enabled = editingValue.toBigDecimalOrNull() != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.save))
                        }
                        OutlinedButton(
                            shapes = ButtonDefaults.shapes(),
                            onClick = onCancelEdit,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        // Each row in its own currency: an account may hold several
                        text = CurrencyFormatter.formatCurrency(balance.balance, balance.currency),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isLatest) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )

                    // Source Badge
                    val (sourceIcon, sourceText, sourceColor) = when (balance.sourceType) {
                        "TRANSACTION", "TRANSACTION_CALCULATED", "TRANSACTION_SMS_BALANCE" -> Triple(Icons.Rounded.SwapHoriz, stringResource(R.string.source_transaction), MaterialTheme.colorScheme.tertiary)
                        // A balance reported with an old SMS import
                        "SMS_BALANCE" -> Triple(Iconax.Edit2, stringResource(R.string.balance_calibration), MaterialTheme.colorScheme.primary)
                        "CARD_LINK" -> Triple(Iconax.Card, stringResource(R.string.source_card_link), MaterialTheme.colorScheme.primary)
                        "BALANCE_CALIBRATION" -> Triple(Iconax.Edit2, stringResource(R.string.balance_calibration), MaterialTheme.colorScheme.primary)
                        "MANUAL", "MANUAL_EDIT" -> Triple(Iconax.Edit2, stringResource(R.string.source_manual), MaterialTheme.colorScheme.onSurfaceVariant)
                        "OPENING_BALANCE" -> Triple(Iconax.Edit2, stringResource(R.string.source_opening), MaterialTheme.colorScheme.primary)
                        "DELETE_REVERSAL" -> Triple(Icons.Rounded.SwapHoriz, stringResource(R.string.source_deleted), MaterialTheme.colorScheme.tertiary)
                        "CASHEW_IMPORT" -> Triple(Iconax.Information, stringResource(R.string.source_import), MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> Triple(Iconax.Information, stringResource(R.string.source_system), MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Surface(
                        color = sourceColor.copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                sourceIcon,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = sourceColor
                            )
                            Text(
                                text = sourceText.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = sourceColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

        }
    }
}
