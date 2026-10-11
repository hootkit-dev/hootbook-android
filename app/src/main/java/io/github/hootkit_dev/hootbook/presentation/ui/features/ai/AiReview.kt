package io.github.hootkit_dev.hootbook.presentation.ui.features.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation as CurrencyFormatter
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountField
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountSelectionSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.BrandIcon
import io.github.hootkit_dev.hootbook.presentation.ui.components.CategorySelectionSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogConfirmButton
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogDismissButton
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.TransactionItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.localizedDateFormatter
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The review retains Core proposals and decisions without manufacturing ledger records. */
internal fun LazyListScope.reviewItems(
    proposals: List<AiProposal>,
    state: AiAssistantUiState,
    onOpen: ((UInt) -> Unit)?,
) {
    val groups =
        listOf(
            R.string.ai_section_accounts to
                proposals.filter { it.change is AiChange.CreateAccount },
            R.string.ai_section_account_changes to
                proposals.filter {
                    it.change is AiChange.SetBalance || it.change is AiChange.UpdateAccount
                },
            R.string.ai_section_add to proposals.filter { it.change is AiChange.AddTransaction },
            R.string.ai_section_update to
                proposals.filter { it.change is AiChange.UpdateTransaction },
            R.string.ai_section_delete to
                proposals.filter { it.change is AiChange.DeleteTransaction },
        )
    groups.forEach { (title, items) ->
        if (items.isNotEmpty())
            item(key = "header_$title") {
                Text(
                    stringResource(title, items.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        items.forEachIndexed { position, proposal ->
            item(key = "change_${proposal.index}") {
                ProposalRow(
                    proposal,
                    state,
                    Modifier.fillMaxWidth()
                        .alpha(if (state.decisions[proposal.index]?.keep == true) 1f else 0.4f),
                    shape = ListItemPosition.from(position, items.size).toShape(),
                    onClick = { onOpen?.invoke(proposal.index) },
                )
            }
        }
    }
}

@Composable
private fun ProposalRow(
    proposal: AiProposal,
    state: AiAssistantUiState,
    modifier: Modifier = Modifier,
    shape: androidx.compose.foundation.shape.CornerBasedShape = ListItemPosition.Single.toShape(),
    onClick: () -> Unit = {},
) {
    val change = proposal.change
    val decision = state.decisions[proposal.index]
    val leftOut = if (decision?.keep == false) stringResource(R.string.ai_left_out) else null
    val dates = localizedDateFormatter(withYear = false)
    when (change) {
        is AiChange.AddTransaction -> {
            val draft = decision?.edited ?: change.draft
            val account =
                (draft.account as? AiAccountRef.Existing)?.accountId?.let(state.accounts::get)
            TransactionItem(
                merchantName = draft.merchant,
                amount = draft.amount,
                transactionType = draft.kind,
                categoryEntity = state.categories[draft.categoryId],
                subcategoryEntity = state.subcategories[draft.subcategoryId],
                accountIconName = account?.icon,
                accountColorHex = account?.color,
                amountOverride = CurrencyFormatter.formatCurrency(draft.amount, draft.currency),
                subtitleOverride =
                    leftOut
                        ?: if (change.possibleDuplicate != null)
                            stringResource(R.string.ai_possible_duplicate) +
                                state.duplicates[change.possibleDuplicate]
                                    ?.let {
                                        " · " + it.dateTime.coreDateText(dates) + " " + it.merchant
                                    }
                                    .orEmpty()
                        else
                            listOfNotNull(
                                    draft.dateTime.coreDateText(dates),
                                    state.categories[draft.categoryId]?.displayName,
                                )
                                .joinToString(" · "),
                onClick = onClick,
                shape = shape,
                modifier = modifier,
            )
        }
        is AiChange.UpdateTransaction ->
            TransactionItem(
                transaction = change.before,
                merchantName = change.after.merchant,
                categoryEntity = state.categories[change.after.categoryId],
                subcategoryEntity = state.subcategories[change.after.subcategoryId],
                subtitleOverride =
                    leftOut
                        ?: stringResource(
                            R.string.ai_was,
                            listOfNotNull(
                                    change.before.merchant,
                                    state.categories[change.before.categoryId]?.displayName,
                                    state.subcategories[change.before.subcategoryId]?.displayName,
                                )
                                .joinToString(" · "),
                        ),
                onClick = onClick,
                shape = shape,
                modifier = modifier,
            )
        is AiChange.DeleteTransaction ->
            TransactionItem(
                transaction = change.before,
                categoryEntity = state.categories[change.before.categoryId],
                subcategoryEntity = state.subcategories[change.before.subcategoryId],
                subtitleOverride = leftOut ?: change.reason,
                onClick = onClick,
                shape = shape,
                modifier = modifier,
            )
        else ->
            ListItem(
                headline = {
                    Text(
                        when (change) {
                            is AiChange.CreateAccount -> change.name
                            is AiChange.SetBalance ->
                                state.accounts[change.accountId]?.name.orEmpty()
                            is AiChange.UpdateAccount ->
                                change.name ?: state.accounts[change.accountId]?.name.orEmpty()
                            else -> ""
                        }
                    )
                },
                leading = {
                    val account =
                        when (change) {
                            is AiChange.SetBalance -> state.accounts[change.accountId]
                            is AiChange.UpdateAccount -> state.accounts[change.accountId]
                            else -> null
                        }
                    val new = change as? AiChange.CreateAccount
                    BrandIcon(
                        merchantName = new?.name ?: account?.name.orEmpty(),
                        size = 40.dp,
                        showBackground = true,
                        accountIconName = new?.icon ?: account?.icon,
                        accountColorHex = new?.color ?: account?.color,
                    )
                },
                supporting = {
                    Text(listOfNotNull(leftOut, accountChangeLine(change)).joinToString(" · "))
                },
                trailing = {
                    when (change) {
                        is AiChange.CreateAccount ->
                            Text(CurrencyFormatter.formatCurrency(change.balance, change.currency))
                        is AiChange.SetBalance ->
                            Text(CurrencyFormatter.formatCurrency(change.balance, change.currency))
                        else -> Unit
                    }
                },
                onClick = onClick,
                shape = shape,
                modifier = modifier,
                padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            )
    }
}

@Composable
private fun accountChangeLine(change: AiChange): String =
    when (change) {
        is AiChange.CreateAccount ->
            listOfNotNull(change.last4, change.currency).joinToString(" · ")
        is AiChange.SetBalance ->
            listOfNotNull(
                    change.currency,
                    change.creditLimit?.let {
                        stringResource(R.string.credit_limit_label) +
                            " " +
                            CurrencyFormatter.formatCurrency(it, change.currency)
                    },
                )
                .joinToString(" · ")
        is AiChange.UpdateAccount ->
            listOfNotNull(
                    change.kind?.let { stringResource(R.string.ai_kind_changed) },
                    change.addCurrencies
                        .takeIf { it.isNotEmpty() }
                        ?.let { stringResource(R.string.ai_currencies_added, it.joinToString()) },
                    change.creditLimit?.let {
                        stringResource(R.string.credit_limit_label) +
                            " " +
                            CurrencyFormatter.formatAmount(it)
                    },
                )
                .joinToString(" · ")
        else -> ""
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReviewDetailSheet(
    proposal: AiProposal,
    state: AiAssistantUiState,
    onEdit: (AiDraftForm) -> Boolean,
    onIncludedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val change = proposal.change
    val decision = state.decisions.getValue(proposal.index)
    var draft by
        remember(proposal, decision.edited) {
            mutableStateOf(
                (change as? AiChange.AddTransaction)?.let {
                    AiDraftForm.from(decision.edited ?: it.draft)
                }
            )
        }
    fun finish() {
        if (draft?.let(onEdit) != false) onDismiss()
    }
    HootbookModalBottomSheet(
        onDismissRequest = { if (!state.saving) finish() },
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.Padding.content)
                .navigationBarsPadding()
                .padding(bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            draft?.let { current ->
                DraftForm(current, state) { draft = it }
                (change as? AiChange.AddTransaction)?.possibleDuplicate?.let { id ->
                    Text(
                        stringResource(R.string.ai_possible_duplicate),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    state.duplicates[id]?.let { existing ->
                        TransactionItem(
                            transaction = existing,
                            categoryEntity = state.categories[existing.categoryId],
                            subcategoryEntity = state.subcategories[existing.subcategoryId],
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
                ?: run {
                    if (change is AiChange.UpdateTransaction) {
                        Text(
                            stringResource(R.string.ai_before),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        TransactionItem(
                            transaction = change.before,
                            categoryEntity = state.categories[change.before.categoryId],
                        )
                        Text(
                            stringResource(R.string.ai_after),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    ProposalRow(proposal, state)
                    if (change is AiChange.CreateAccount)
                        Text(stringResource(R.string.ai_new_account_note))
                }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(Spacing.sm, androidx.compose.ui.Alignment.End),
            ) {
                OutlinedButton(
                    enabled = !state.saving,
                    onClick = {
                        if (draft?.let(onEdit) != false) {
                            onIncludedChange(!decision.keep)
                            onDismiss()
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(
                        stringResource(
                            if (decision.keep) R.string.ai_leave_out else R.string.ai_put_back
                        )
                    )
                }
                Button(
                    enabled = !state.saving,
                    onClick = { finish() },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.done))
                }
            }
        }
    }
}

/** Fields of a proposed transaction, laid out like the Add form. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DraftForm(
    draft: AiDraftForm,
    lookups: AiAssistantUiState,
    onChange: (AiDraftForm) -> Unit,
) {
    var pickingDate by remember { mutableStateOf(false) }
    var pickingCategory by remember { mutableStateOf(false) }
    var pickingAccount by remember { mutableStateOf(false) }
    var pickingTarget by remember { mutableStateOf(false) }
    val accounts = remember(lookups) { lookups.accounts.values.toList() }

    val types = listOf(TransactionKind.EXPENSE, TransactionKind.INCOME, TransactionKind.TRANSFER)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        types.forEachIndexed { i, type ->
            SegmentedButton(
                selected = draft.kind == type,
                onClick = {
                    onChange(
                        draft.copy(
                            kind = type,
                            toAccount =
                                if (type == TransactionKind.TRANSFER) draft.toAccount else null,
                        )
                    )
                },
                shape = SegmentedButtonDefaults.itemShape(i, types.size),
                // No check mark: the filled segment already shows the choice
                icon = {},
            ) {
                Text(
                    stringResource(
                        when (type) {
                            TransactionKind.INCOME -> R.string.type_income
                            TransactionKind.TRANSFER -> R.string.type_transfer
                            else -> R.string.type_expense
                        }
                    )
                )
            }
        }
    }
    OutlinedTextField(
        value = draft.merchant,
        onValueChange = { onChange(draft.copy(merchant = it)) },
        label = { Text(stringResource(R.string.ai_merchant)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.amount,
        onValueChange = { onChange(draft.copy(amount = it)) },
        label = { Text(stringResource(R.string.amount)) },
        suffix = { Text(draft.currency) },
        isError = draft.amount.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    FieldButton(
        label = stringResource(R.string.date),
        value =
            java.time.LocalDateTime.parse(draft.dateTime)
                .format(DateTimeFormatter.ofPattern(stringResource(R.string.ai_date_time_pattern))),
        onClick = { pickingDate = true },
    )
    FieldButton(
        label = stringResource(R.string.category),
        value =
            listOfNotNull(
                    lookups.categories[draft.categoryId]?.displayName,
                    lookups.subcategories[draft.subcategoryId]?.displayName,
                )
                .joinToString(" · "),
        onClick = { pickingCategory = true },
    )
    AiAccountField(draft.account, lookups, onClick = { pickingAccount = true })
    if (draft.kind == TransactionKind.TRANSFER) {
        AiAccountField(
            draft.toAccount,
            lookups,
            onClick = { pickingTarget = true },
            placeholder = stringResource(R.string.select_target_account),
        )
    }
    OutlinedTextField(
        value = draft.notes,
        onValueChange = { onChange(draft.copy(notes = it)) },
        label = { Text(stringResource(R.string.notes)) },
        modifier = Modifier.fillMaxWidth(),
    )

    if (pickingDate) {
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    java.time.LocalDateTime.parse(draft.dateTime)
                        .toLocalDate()
                        .atStartOfDay()
                        .toInstant(ZoneOffset.UTC)
                        .toEpochMilli()
            )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                DialogConfirmButton(
                    stringResource(R.string.done),
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            val day =
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            onChange(
                                draft.copy(
                                    dateTime =
                                        day.atTime(
                                                java.time.LocalDateTime.parse(draft.dateTime)
                                                    .toLocalTime()
                                            )
                                            .toString()
                                )
                            )
                        }
                        pickingDate = false
                    },
                )
            },
            dismissButton = {
                DialogDismissButton(
                    stringResource(R.string.ai_cancel),
                    onClick = { pickingDate = false },
                )
            },
        ) {
            DatePicker(state = state)
        }
    }
    if (pickingCategory) {
        HootbookModalBottomSheet(onDismissRequest = { pickingCategory = false }) {
            val subcategories =
                remember(lookups) { lookups.subcategories.values.groupBy { it.categoryId } }
            CategorySelectionSheet(
                categories = lookups.categories.values.sortedBy { it.sortOrder },
                subcategoriesMap = subcategories,
                onSelectionComplete = { category, sub ->
                    onChange(draft.copy(categoryId = category.id, subcategoryId = sub?.id))
                    pickingCategory = false
                },
                onDismiss = { pickingCategory = false },
            )
        }
    }
    if (pickingAccount || pickingTarget) {
        val target = pickingTarget
        HootbookModalBottomSheet(
            onDismissRequest = {
                pickingAccount = false
                pickingTarget = false
            }
        ) {
            val newAccounts = lookups.items.mapNotNull { it.change as? AiChange.CreateAccount }
            newAccounts.forEach { new ->
                androidx.compose.material3.TextButton(
                    onClick = {
                        onChange(
                            if (target) draft.copy(toAccount = AiAccountRef.New(new.reference))
                            else draft.copy(account = AiAccountRef.New(new.reference))
                        )
                        pickingAccount = false
                        pickingTarget = false
                    }
                ) {
                    Text(new.name)
                }
            }
            AccountSelectionSheet(
                accounts =
                    if (target)
                        accounts.filter {
                            it.id != (draft.account as? AiAccountRef.Existing)?.accountId
                        }
                    else accounts,
                selectedAccount =
                    ((if (target) draft.toAccount else draft.account) as? AiAccountRef.Existing)
                        ?.accountId
                        ?.let(lookups.accounts::get),
                title =
                    stringResource(
                        if (target) R.string.select_target_account else R.string.select_account
                    ),
                onAccountSelected = { account: Account? ->
                    onChange(
                        if (target)
                            draft.copy(toAccount = account?.let { AiAccountRef.Existing(it.id) })
                        // The amount stays in its currency; saving puts it in the account's own
                        // pocket for it, or converts it into the main one
                        else draft.copy(account = account?.let { AiAccountRef.Existing(it.id) })
                    )
                    pickingAccount = false
                    pickingTarget = false
                },
                showNoneOption = !target,
            )
        }
    }
}

/** A Core new-account proposal can be shown without inventing an Account record or ID. */
@Composable
private fun AiAccountField(
    ref: AiAccountRef?,
    state: AiAssistantUiState,
    onClick: () -> Unit,
    placeholder: String = stringResource(R.string.select_account),
) {
    val new =
        (ref as? AiAccountRef.New)?.reference?.let { reference ->
            state.items
                .mapNotNull { it.change as? AiChange.CreateAccount }
                .find { it.reference == reference }
        }
    if (new == null) {
        AccountField(
            account = (ref as? AiAccountRef.Existing)?.accountId?.let(state.accounts::get),
            onClick = onClick,
            placeholder = placeholder,
        )
        return
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BrandIcon(
                merchantName = new.name,
                size = 40.dp,
                showBackground = true,
                accountIconName = new.icon,
                accountColorHex = new.color,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    new.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                new.last4?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldButton(label: String, value: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
