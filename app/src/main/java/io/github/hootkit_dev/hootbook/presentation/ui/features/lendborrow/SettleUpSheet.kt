package io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow

import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import io.github.hootkit_dev.hootbook.presentation.format.toJavaDateTime
import io.github.hootkit_dev.hootbook.presentation.format.toCoreDateTime
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore
import io.github.hootkit_dev.hootbook.presentation.ui.components.AddEditPersonSheet

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
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountSelectionSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountField
import io.github.hootkit_dev.hootbook.presentation.ui.components.BrandIcon
import io.github.hootkit_dev.hootbook.presentation.ui.components.GenericTypeSwitcher
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.NumberPad
import io.github.hootkit_dev.hootbook.presentation.ui.features.add.AmountInput
import io.github.hootkit_dev.hootbook.presentation.ui.icons.DocumentText2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Information
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation as CurrencyFormatter
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun SettleUpSheet(
    person: Person,
    accounts: List<Account> = emptyList(),
    debts: List<DebtEntry> = emptyList(),
    defaultAccountId: String? = null,
    saving: Boolean = false,
    error: Int? = null,
    onDismiss: () -> Unit,
    onSettle: (SettleDraft) -> Unit
) {
    // If person.totals.net > 0, person owes user (User gets -> REPAID_TO_ME).
    // If person.totals.net < 0, user owes person (User pays -> REPAID_BY_ME).
    val activePerCurrency: List<Money> = remember(debts, person.totals) {
        debts
            .groupBy { it.amount.currency }
            .mapNotNull { (curr, entries) ->
                val lent = entries.filter { it.kind == DebtKind.LENT }.fold(BigDecimal.ZERO) { acc, e -> acc + e.amount.amount }
                val repaidToMe = entries.filter { it.kind == DebtKind.REPAID_TO_ME }.fold(BigDecimal.ZERO) { acc, e -> acc + e.amount.amount }
                val borrowed = entries.filter { it.kind == DebtKind.BORROWED }.fold(BigDecimal.ZERO) { acc, e -> acc + e.amount.amount }
                val repaidByMe = entries.filter { it.kind == DebtKind.REPAID_BY_ME }.fold(BigDecimal.ZERO) { acc, e -> acc + e.amount.amount }
                val rec = (lent - repaidToMe).coerceAtLeast(BigDecimal.ZERO)
                val pay = (borrowed - repaidByMe).coerceAtLeast(BigDecimal.ZERO)
                val net = rec - pay
                if (net.signum() != 0) Money(net, curr) else null
            }
            .ifEmpty {
                if (person.totals.net.signum() != 0) listOf(Money(person.totals.net, person.totals.currency)) else emptyList()
            }
    }
    var selectedCurrency by remember(person.id) {
        mutableStateOf(activePerCurrency.firstOrNull()?.currency ?: person.totals.currency)
    }
    val suggestedAmount = person.totals.net.abs()

    var selectedIndex by remember { mutableStateOf(0) } // 0 = Full, 1 = Partial
    var amountText by remember { mutableStateOf(suggestedAmount.toPlainString()) }
    var noteText by remember { mutableStateOf("") }

    var selectedAccount by remember {
        mutableStateOf(defaultAccountId?.let { id -> accounts.find { it.id == id } } ?: accounts.firstOrNull())
    }
    var showNumberPad by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }

    HootbookModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
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

                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                if (!person.totals.complete) Text(stringResource(R.string.core_debts_incomplete), color = MaterialTheme.colorScheme.error)
                // Full / Partial Tabs
                GenericTypeSwitcher(
                    selectedIndex = selectedIndex,
                    onIndexChange = { index ->
                        selectedIndex = index
                        if (index == 0) {
                            selectedCurrency = person.totals.currency
                            amountText = suggestedAmount.toPlainString()
                        } else {
                            val defaultCurr = activePerCurrency.firstOrNull()?.currency ?: person.totals.currency
                            selectedCurrency = defaultCurr
                            val currNet = activePerCurrency.find { it.currency == defaultCurr }?.amount?.abs() ?: BigDecimal.ZERO
                            amountText = currNet.toPlainString()
                        }
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
                            if (selectedIndex == 0 && activePerCurrency.size > 1) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    activePerCurrency.forEach { item: Money ->
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                        ) {
                                            Text(
                                                text = "${item.currency} ${CurrencyFormatter.formatCurrency(item.amount.abs(), item.currency)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedIndex == 1 && activePerCurrency.size > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        activePerCurrency.forEach { item: Money ->
                            FilterChip(
                                selected = selectedCurrency == item.currency,
                                onClick = {
                                    selectedCurrency = item.currency
                                    amountText = item.amount.abs().toPlainString()
                                },
                                label = {
                                    Text("${item.currency} (${CurrencyFormatter.formatCurrency(item.amount.abs(), item.currency)})")
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Amount Input
                AmountInput(
                    amount = if (amountText.isBlank()) "0" else amountText,
                    currencySymbol = CurrencyFormatter.getCurrencySymbol(
                        if (selectedIndex == 0) person.totals.currency else selectedCurrency
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
                    enabled = !saving,
                    onClick = {
                        val parsedAmount = amountText.toBigDecimalOrNull()
                        if (selectedIndex == 1 && (parsedAmount == null || parsedAmount <= BigDecimal.ZERO)) {
                            return@Button
                        }
                        val amount = if (selectedIndex == 0) null else Money(parsedAmount!!, selectedCurrency)
                        onSettle(SettleDraft(amount, selectedAccount?.id, noteText, java.time.LocalDateTime.now().toCoreDateTime()))
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
