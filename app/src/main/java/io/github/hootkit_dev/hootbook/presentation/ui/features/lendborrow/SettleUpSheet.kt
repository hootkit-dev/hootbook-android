package io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.domain.model.LendBorrowPerson
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountSelectionSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountField
import io.github.hootkit_dev.hootbook.presentation.ui.components.BrandIcon
import io.github.hootkit_dev.hootbook.presentation.ui.components.GenericTypeSwitcher
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.NumberPad
import io.github.hootkit_dev.hootbook.presentation.ui.features.add.AmountInput
import io.github.hootkit_dev.hootbook.presentation.ui.icons.DocumentText2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Information
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettleUpSheet(
    person: LendBorrowPerson,
    accounts: List<AccountBalanceEntity> = emptyList(),
    defaultAccountId: Long? = null,
    onDismiss: () -> Unit,
    onSettle: (amount: BigDecimal, note: String, isLentSettlement: Boolean, accountId: Long?) -> Unit
) {
    // If person.netBalance > 0, person owes user (User gets -> SETTLEMENT_LENT).
    // If person.netBalance < 0, user owes person (User pays -> SETTLEMENT_BORROWED).
    val isLentSettlement = person.netBalance > BigDecimal.ZERO
    val suggestedAmount = person.netBalance.abs()

    var selectedIndex by remember { mutableStateOf(0) } // 0 = Full, 1 = Partial
    var amountText by remember { mutableStateOf(suggestedAmount.toPlainString()) }
    var noteText by remember { mutableStateOf("") }

    var selectedAccount by remember {
        mutableStateOf(defaultAccountId?.let { id -> accounts.find { it.id == id } } ?: accounts.firstOrNull())
    }
    var showNumberPad by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }

    HootbookModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 0.dp)
            ) {
                Text(
                    text = stringResource(R.string.settle_up) + "-" + person.name,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Full / Partial Tabs
                GenericTypeSwitcher(
                    selectedIndex = selectedIndex,
                    onIndexChange = { index ->
                        selectedIndex = index
                        amountText = if (index == 0) suggestedAmount.toPlainString() else "0"
                    },
                    options = listOf(
                        stringResource(R.string.full_settlement),
                        stringResource(R.string.partial_settlement)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Info Card explaining both options
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimensions.Radius.md),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Iconax.Information,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val (titleRes, descRes) = if (selectedIndex == 0) {
                                R.string.full_settlement to R.string.settle_full_desc
                            } else {
                                R.string.partial_settlement to R.string.settle_partial_desc
                            }
                            Text(
                                text = stringResource(titleRes),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(descRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Amount Input
                AmountInput(
                    amount = if (amountText.isBlank()) "0" else amountText,
                    currencySymbol = CurrencyFormatter.getCurrencySymbol(
                        selectedAccount?.currency ?: "CNY"
                    ),
                    onClick = { showNumberPad = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedIndex == 1
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Account Selection
                AccountField(
                    account = selectedAccount,
                    onClick = { showAccountSheet = true },
                    placeholder = stringResource(R.string.select_account),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                )
                Spacer(modifier = Modifier.height(1.5.dp))

                // Description TextField with same visual design as Account Selection
                TextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.settle_description),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = { Icon(Iconax.DocumentText2, contentDescription = null) },
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 4.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(160.dp))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                Button(
                    onClick = {
                        val parsedAmount = amountText.toBigDecimalOrNull()
                        if (parsedAmount == null || parsedAmount <= BigDecimal.ZERO) {
                            return@Button
                        }
                        val amount = if (selectedIndex == 0) suggestedAmount else parsedAmount
                        onSettle(amount, noteText, isLentSettlement, selectedAccount?.id)
                    },
                    modifier = Modifier
                        .padding(horizontal = Dimensions.Padding.content)
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(56.dp),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (showNumberPad) {
        HootbookModalBottomSheet(
            onDismissRequest = { showNumberPad = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            NumberPad(
                initialValue = amountText,
                onDone = {
                    amountText = it
                    // Automatically switch tabs based on the entered amount
                    val enteredAmount = it.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    if (enteredAmount.compareTo(suggestedAmount) == 0) {
                        selectedIndex = 0
                    } else {
                        selectedIndex = 1
                    }
                    showNumberPad = false
                }
            )
        }
    }

    if (showAccountSheet) {
        HootbookModalBottomSheet(
            onDismissRequest = { showAccountSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            AccountSelectionSheet(
                accounts = accounts,
                selectedAccount = selectedAccount,
                showNoneOption = false,
                onAccountSelected = {
                    selectedAccount = it
                    showAccountSheet = false
                }
            )
        }
    }
}