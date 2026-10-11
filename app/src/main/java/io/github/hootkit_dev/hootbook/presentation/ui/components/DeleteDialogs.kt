@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Bag
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Danger
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.LocalBlurEffects
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import io.github.hootkit_dev.hootbook.presentation.common.icons.BrandIcons
import io.github.hootkit_dev.hootbook.utils.IconResolutionUtils
import io.github.hootkit_dev.hootbook.R
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

@OptIn(ExperimentalHazeApi::class)
@Composable
fun DeleteTransactionDialog(
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    isDeleting: Boolean,
    permanent: Boolean = false,
    blurEffects: Boolean = LocalBlurEffects.current,
    hazeState: HazeState = remember { HazeState() },
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Iconax.Bag,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(stringResource(R.string.delete_transaction_title)) },
        text = {
            Text(
                text = stringResource(if (permanent) R.string.delete_multiple_transactions_confirm else R.string.restore_transaction_delete_confirm),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            DialogConfirmButton(
                text = stringResource(R.string.delete),
                onClick = onDelete,
                destructive = true,
                loading = isDeleting
            )
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.cancel), onDismiss)
        },
        containerColor = HootbookDialogDefaults.containerColor
    )
}

@OptIn(ExperimentalHazeApi::class)
@Composable
fun DeleteMultipleTransactionsDialog(
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    selectedTransactionIds: Set<String>,
    permanent: Boolean = false,
    busy: Boolean = false,
    blurEffects: Boolean = LocalBlurEffects.current,
    hazeState: HazeState = remember { HazeState() },
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = {
            Icon(
                imageVector = Iconax.Bag,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(text = pluralStringResource(R.plurals.delete_transactions_title, selectedTransactionIds.size, selectedTransactionIds.size))
        },
        text = {
            Text(
                text = stringResource(if (permanent) R.string.delete_multiple_transactions_confirm else R.string.restore_transaction_delete_confirm),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            DialogConfirmButton(
                text = stringResource(R.string.delete),
                onClick = { if (!busy) onDelete() },
                destructive = true
            )
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.cancel), onDismiss)
        },
        containerColor = HootbookDialogDefaults.containerColor
    )
}

@OptIn(ExperimentalHazeApi::class)
@Composable
fun DeleteMultiplePersonsDialog(
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    selectedPersonIds: Set<String>,
    blurEffects: Boolean = LocalBlurEffects.current,
    hazeState: HazeState = remember { HazeState() },
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(text = pluralStringResource(R.plurals.delete_persons_title, selectedPersonIds.size, selectedPersonIds.size))
        },
        text = {
            Text(
                text = stringResource(R.string.delete_multiple_persons_confirm),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            DialogConfirmButton(
                text = stringResource(R.string.delete),
                onClick = onDelete,
                destructive = true
            )
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.cancel), onDismiss)
        },
        containerColor = HootbookDialogDefaults.containerColor
    )
}

@OptIn(ExperimentalHazeApi::class)
@Composable
fun DeleteMultipleRecordsDialog(
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    selectedRecordIds: Set<String>,
    blurEffects: Boolean = LocalBlurEffects.current,
    hazeState: HazeState = remember { HazeState() },
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(text = pluralStringResource(R.plurals.delete_records_title, selectedRecordIds.size, selectedRecordIds.size))
        },
        text = {
            Text(
                text = stringResource(R.string.delete_multiple_records_confirm),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            DialogConfirmButton(
                text = stringResource(R.string.delete),
                onClick = onDelete,
                destructive = true
            )
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.cancel), onDismiss)
        },
        containerColor = HootbookDialogDefaults.containerColor
    )
}

@OptIn(ExperimentalHazeApi::class)
@Composable
fun DeleteAccountDialog(
    bankName: String,
    accountLast4: String,
    accountIcon: Int = 0,
    accountColor: String? = null,
    isCreditCard: Boolean = false,
    isWallet: Boolean = false,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    blurEffects: Boolean = LocalBlurEffects.current,
    hazeState: HazeState = remember { HazeState() },
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Iconax.Danger,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(stringResource(R.string.delete_account_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = stringResource(R.string.delete_account_confirm),
                    style = MaterialTheme.typography.bodyMedium
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val context = LocalContext.current
                        val fallbackIcon = BrandIcons.getIconResource(bankName) ?: R.drawable.type_finance_bank
                        val icon = IconResolutionUtils.getSafeResId(context, accountIcon, fallbackIcon)
                        val background = accountColor?.let { value ->
                            runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrNull()
                        }?.copy(alpha = 0.12f) ?: MaterialTheme.colorScheme.surfaceContainer
                        Box(
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(background),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(32.dp))
                        }
                        Column {
                            Text(
                                text = bankName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            val supportingText = if (isWallet) stringResource(R.string.type_wallet)
                            else accountLast4.takeIf { it.isNotBlank() }?.let { "•••• ${it.takeLast(4)}" }.orEmpty()
                            Text(
                                text = supportingText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(0.5f)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.account_delete_core_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(Spacing.sm)
                    )
                }
            }
        },
        confirmButton = {
            DialogConfirmButton(
                text = stringResource(R.string.delete),
                onClick = onDelete,
                destructive = true
            )
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.cancel), onDismiss)
        },
        containerColor = HootbookDialogDefaults.containerColor
    )
}

@OptIn(ExperimentalHazeApi::class)
@Composable
fun DeleteCategoryDialog(
    hasTransactions: Boolean = false,
    // Already resolved by Core (including localized builtin names); no App name mapping.
    categoryName: String,
    onMoveDefault: () -> Unit,
    onMoveOthers: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    blurEffects: Boolean = LocalBlurEffects.current,
    hazeState: HazeState = remember { HazeState() },
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    if (hasTransactions) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.delete_category_title))
                    IconButton(
                        shapes = IconButtonDefaults.shapes(),
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(0.5f),
                            contentColor = MaterialTheme.colorScheme.onBackground
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cancel),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            text = {
                Text(
                    stringResource(R.string.delete_category_confirm_with_transactions, categoryName)
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        shapes = ButtonDefaults.shapes(),
                        onClick = onMoveOthers,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.padding(horizontal = Dimensions.Radius.md).fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.move_to_different_category))
                    }
                    Button(
                        shapes = ButtonDefaults.shapes(),
                        onClick = onMoveDefault,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.padding(horizontal = Dimensions.Radius.md).fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.move_to_miscellaneous))
                    }
                }
            },
            containerColor = HootbookDialogDefaults.containerColor
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.delete_category_title)) },
            text = { Text(stringResource(R.string.delete_category_confirm_no_transactions, categoryName)) },
            confirmButton = {
                DialogConfirmButton(
                    text = stringResource(R.string.delete),
                    onClick = onDelete,
                    destructive = true
                )
            },
            dismissButton = {
                DialogDismissButton(stringResource(R.string.cancel), onDismiss)
            },
            containerColor = HootbookDialogDefaults.containerColor
        )
    }
}


@OptIn(ExperimentalHazeApi::class)
@Composable
fun DeleteBudgetDialog(
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    blurEffects: Boolean = LocalBlurEffects.current,
    hazeState: HazeState = remember { HazeState() },
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Iconax.Danger, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.delete_budget_title)) },
        text = { Text(stringResource(R.string.delete_budget_confirm)) },
        confirmButton = {
            DialogConfirmButton(
                text = stringResource(R.string.delete),
                onClick = onDelete,
                destructive = true
            )
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.cancel), onDismiss)
        },
        containerColor = HootbookDialogDefaults.containerColor
    )
}


@OptIn(ExperimentalHazeApi::class)
@Composable
fun DeleteSubscriptionDialog(
    subscriptionName: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    blurEffects: Boolean = LocalBlurEffects.current,
    hazeState: HazeState = remember { HazeState() },
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Iconax.Bag,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(stringResource(R.string.delete_subscription_title)) },
        text = {
            Text(
                text = stringResource(R.string.delete_subscription_confirm, subscriptionName),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            DialogConfirmButton(
                text = stringResource(R.string.delete),
                onClick = onDelete,
                destructive = true
            )
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.cancel), onDismiss)
        },
        containerColor = HootbookDialogDefaults.containerColor
    )
}

@OptIn(ExperimentalHazeApi::class)
@Composable
fun DeleteSubcategoryDialog(
    // Already resolved by Core; render directly, including user-defined names.
    subcategoryName: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    blurEffects: Boolean = LocalBlurEffects.current,
    hazeState: HazeState = remember { HazeState() },
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Iconax.Danger,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(stringResource(R.string.delete_subcategory_title)) },
        text = {
            Text(
                text = stringResource(R.string.delete_subcategory_confirm, subcategoryName),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            DialogConfirmButton(
                text = stringResource(R.string.delete),
                onClick = onDelete,
                destructive = true
            )
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.cancel), onDismiss)
        },
        containerColor = HootbookDialogDefaults.containerColor
    )
}
