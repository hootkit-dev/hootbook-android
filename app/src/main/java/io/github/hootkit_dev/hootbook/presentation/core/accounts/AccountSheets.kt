@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.hootkit.core.AccountKind
import dev.hootkit.core.AnchorSource
import dev.hootkit.core.BalanceAnchor
import dev.hootkit.core.Institution
import dev.hootkit.core.LocalDateTime
import dev.hootkit.core.Pocket
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreRecordIcon
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreVisualStylePicker
import io.github.hootkit_dev.hootbook.presentation.ui.components.BalanceChart
import io.github.hootkit_dev.hootbook.presentation.ui.components.BalancePoint
import io.github.hootkit_dev.hootbook.presentation.ui.components.DateTimeField
import io.github.hootkit_dev.hootbook.presentation.ui.components.FormSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.FormTextField
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListSection
import io.github.hootkit_dev.hootbook.presentation.ui.components.MenuAction
import io.github.hootkit_dev.hootbook.presentation.ui.components.OverflowMenu
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.InstitutionPicker
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.InvestmentsEmptySection
import io.github.hootkit_dev.hootbook.utils.DateFormats

/** The editable kinds, in the order the editor offers them. */
private val editorKinds = listOf(AccountKind.BANK, AccountKind.CREDIT_CARD, AccountKind.WALLET, AccountKind.INVESTMENT)

@Composable
internal fun accountKindLabel(kind: AccountKind): String = stringResource(when (kind) {
    AccountKind.BANK -> R.string.account_kind_bank
    AccountKind.CREDIT_CARD -> R.string.account_kind_credit_card
    AccountKind.WALLET -> R.string.account_kind_wallet
    AccountKind.INVESTMENT -> R.string.account_kind_investment
    AccountKind.UNKNOWN -> R.string.account_kind_unknown
})

/** Add or edit an account. The institution preset comes first: it fills the name, currency and look. */
@Composable
internal fun AccountEditorSheet(
    draft: CoreAccountDraft,
    institutions: List<Institution>,
    saving: Boolean,
    error: String?,
    changed: (CoreAccountDraft) -> Unit,
    dismiss: () -> Unit,
    save: () -> Unit,
    onConnectBroker: () -> Unit,
) {
    var institutionsOpen by remember { mutableStateOf(false) }
    val chinese = LocalConfiguration.current.locales[0].language == "zh"
    val institution = institutions.find { it.key == draft.institutionKey }
    val credit = draft.kind == AccountKind.CREDIT_CARD
    FormSheet(
        title = stringResource(if (draft.id == null) R.string.add_account_title else R.string.edit_account_title),
        onDismiss = dismiss, onConfirm = save, busy = saving, confirmEnabled = draft.name.isNotBlank(), error = error,
    ) {
        if (draft.kind == AccountKind.INVESTMENT && draft.id == null) {
            InvestmentsEmptySection(onAdd = { if (!saving) { dismiss(); onConnectBroker() } })
        }
        ListSection {
            item(
                headline = { Text(stringResource(R.string.account_institution)) },
                leading = institution?.let { { CoreRecordIcon(it.iconName, Modifier.size(32.dp)) } },
                trailing = { Text(institution?.let { if (chinese) it.chineseName else it.englishName } ?: stringResource(R.string.account_institution_none),
                    color = MaterialTheme.colorScheme.primary) },
                chevron = true, onClick = { if (!saving) institutionsOpen = true },
            )
        }
        FormTextField(draft.name, stringResource(R.string.account_name), { changed(draft.copy(name = it)) }, enabled = !saving)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            editorKinds.forEachIndexed { index, kind ->
                SegmentedButton(
                    selected = draft.kind == kind, onClick = { changed(draft.copy(kind = kind)) }, enabled = !saving,
                    shape = SegmentedButtonDefaults.itemShape(index, editorKinds.size), icon = {},
                    label = { Text(accountKindLabel(kind), maxLines = 1) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FormTextField(draft.currency, stringResource(R.string.account_main_currency), { changed(draft.copy(currency = it)) },
                Modifier.weight(1f), enabled = !saving)
            if (draft.kind != AccountKind.WALLET) FormTextField(draft.last4, stringResource(R.string.account_last4),
                { changed(draft.copy(last4 = it)) }, Modifier.weight(1f), enabled = !saving, decimal = true)
        }
        if (draft.id == null) {
            FormTextField(draft.openingBalance, stringResource(if (credit) R.string.account_opening_debt else R.string.account_opening_balance),
                { changed(draft.copy(openingBalance = it)) }, enabled = !saving, decimal = true)
            DateTimeField(draft.openingAt, stringResource(R.string.account_opening_at), { changed(draft.copy(openingAt = it)) },
                enabled = !saving, emptyText = stringResource(R.string.account_now))
        }
        if (credit) {
            FormTextField(draft.creditLimit, stringResource(R.string.account_credit_limit), { changed(draft.copy(creditLimit = it)) },
                enabled = !saving, decimal = true)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormTextField(draft.statementDay, stringResource(R.string.account_statement_day), { changed(draft.copy(statementDay = it)) },
                    Modifier.weight(1f), enabled = !saving, decimal = true, supporting = stringResource(R.string.account_day_hint))
                FormTextField(draft.dueDay, stringResource(R.string.account_due_day), { changed(draft.copy(dueDay = it)) },
                    Modifier.weight(1f), enabled = !saving, decimal = true, supporting = stringResource(R.string.account_day_hint))
            }
        }
        CoreVisualStylePicker(draft.icon, draft.color, !saving, { changed(draft.copy(icon = it)) }, { changed(draft.copy(color = it)) })
    }
    if (institutionsOpen) InstitutionPicker(institutions, draft.kind == AccountKind.INVESTMENT, { picked ->
        changed(draft.copy(institutionKey = picked.key,
            name = draft.name.ifBlank { if (chinese) picked.chineseName else picked.englishName },
            currency = if (draft.id == null) picked.currency else draft.currency, icon = picked.iconName, color = picked.color))
        institutionsOpen = false
    }, { institutionsOpen = false })
}

internal enum class PocketAction { ADD, CALIBRATE, LIMIT, ANCHOR }

/** What a pocket sheet edits; text only at the input boundary. */
internal data class PocketEditor(
    val action: PocketAction,
    val currency: String = "",
    val balance: String = "",
    val at: String = "",
    val anchorId: String? = null,
    val note: String = "",
) {
    companion object {
        fun calibrate(pocket: Pocket) = PocketEditor(PocketAction.CALIBRATE, pocket.currency, pocket.balance.toPlainString())
        fun limit(pocket: Pocket) = PocketEditor(PocketAction.LIMIT, pocket.currency, pocket.creditLimit?.toPlainString().orEmpty())
        fun anchor(anchor: BalanceAnchor, currency: String) = PocketEditor(PocketAction.ANCHOR, currency, anchor.balance.toPlainString(),
            anchor.at.inputText(), anchor.id, anchor.note.orEmpty())
    }
}

/** Add a currency, calibrate a balance, set a limit or edit a calibration. */
@Composable
internal fun PocketSheet(initial: PocketEditor, saving: Boolean, error: String?, dismiss: () -> Unit, save: (PocketEditor) -> Unit) {
    var editor by remember(initial) { mutableStateOf(initial) }
    val title = when (editor.action) {
        PocketAction.ADD -> stringResource(R.string.account_currency_add)
        PocketAction.CALIBRATE -> stringResource(R.string.account_calibrate_title, editor.currency)
        PocketAction.LIMIT -> stringResource(R.string.account_limit_title, editor.currency)
        PocketAction.ANCHOR -> stringResource(R.string.account_anchor_edit_title)
    }
    FormSheet(title, dismiss, { save(editor) }, busy = saving, error = error,
        confirmEnabled = editor.action == PocketAction.LIMIT || editor.balance.isNotBlank()) {
        if (editor.action == PocketAction.CALIBRATE) Text(stringResource(R.string.account_calibrate_note),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (editor.action == PocketAction.ADD) FormTextField(editor.currency, stringResource(R.string.account_currency_code),
            { editor = editor.copy(currency = it) }, enabled = !saving)
        FormTextField(editor.balance, stringResource(if (editor.action == PocketAction.LIMIT) R.string.account_credit_limit else R.string.account_balance),
            { editor = editor.copy(balance = it) }, enabled = !saving, decimal = true,
            supporting = if (editor.action == PocketAction.LIMIT) stringResource(R.string.account_limit_hint) else null)
        if (editor.action != PocketAction.LIMIT) DateTimeField(editor.at, stringResource(R.string.account_balance_at),
            { editor = editor.copy(at = it) }, enabled = !saving, emptyText = stringResource(R.string.account_now))
        if (editor.action == PocketAction.ANCHOR) FormTextField(editor.note, stringResource(R.string.account_notes),
            { editor = editor.copy(note = it) }, enabled = !saving)
    }
}

private val historyPeriods = listOf(1 to R.string.net_worth_history_month, 3 to R.string.net_worth_history_quarter,
    6 to R.string.net_worth_history_half_year, 12 to R.string.net_worth_history_year)

/** The selected currency's day-end balances over a period, and its balance at any moment. */
@Composable
internal fun BalanceHistorySheet(state: CoreAccountsUiState, onPeriod: (Int) -> Unit, onBalanceAt: (String) -> Unit, onDismiss: () -> Unit) {
    val currency = state.currency.orEmpty()
    HootbookModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.account_balance_trend) + " · " + currency, style = MaterialTheme.typography.headlineSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                historyPeriods.forEachIndexed { index, (months, label) ->
                    SegmentedButton(selected = state.historyMonths == months, onClick = { onPeriod(months) }, icon = {},
                        shape = SegmentedButtonDefaults.itemShape(index, historyPeriods.size), label = { Text(stringResource(label), maxLines = 1) })
                }
            }
            if (state.history.isNotEmpty()) BalanceChart(primaryCurrency = currency, balanceHistory = state.history.map { day ->
                BalancePoint(java.time.LocalDate.of(day.date.year, day.date.month.toInt(), day.date.day.toInt()).atStartOfDay(), day.balance, currency)
            }, height = 200)
            else if (!state.historyLoading) Text(stringResource(R.string.account_trend_empty), Modifier.heightIn(min = 48.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DateTimeField(state.balanceAtInput, stringResource(R.string.account_balance_at), onBalanceAt,
                emptyText = stringResource(R.string.account_now))
            state.historicalBalance?.let {
                ListSection { item(headline = { Text(stringResource(R.string.account_balance_then)) },
                    trailing = { Text(CoreMoneyPresentation.format(it, currency, state.customSymbols), style = MaterialTheme.typography.titleMedium) }) }
            }
        }
    }
}

/** The selected currency's balance calibrations, newest first, each editable or deletable. */
@Composable
internal fun AnchorsSheet(state: CoreAccountsUiState, onDismiss: () -> Unit, onEdit: (BalanceAnchor) -> Unit, onDelete: (BalanceAnchor) -> Unit) {
    val currency = state.currency.orEmpty()
    HootbookModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.account_calibrations) + " · " + currency, Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.headlineSmall)
            if (state.anchors.isEmpty()) Text(stringResource(R.string.account_calibrations_empty), Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else ListSection {
                state.anchors.forEach { anchor ->
                    item(
                        headline = { Text(CoreMoneyPresentation.format(anchor.balance, currency, state.customSymbols), style = MaterialTheme.typography.titleMedium) },
                        supporting = { Text(listOfNotNull(anchor.at.text(), anchorSourceLabel(anchor.source),
                            anchor.difference?.let { stringResource(R.string.account_anchor_difference, CoreMoneyPresentation.format(it, currency, state.customSymbols)) },
                            anchor.note).joinToString(" · ")) },
                        trailing = { OverflowMenu(enabled = !state.saving, actions = listOf(
                            MenuAction(stringResource(R.string.edit), Icons.Rounded.Edit) { onEdit(anchor) },
                            MenuAction(stringResource(R.string.delete), Icons.Rounded.Delete, destructive = true) { onDelete(anchor) },
                        )) },
                    )
                }
            }
        }
    }
}

@Composable
private fun anchorSourceLabel(source: AnchorSource): String = stringResource(when (source) {
    AnchorSource.OPENING -> R.string.account_anchor_opening
    AnchorSource.MANUAL -> R.string.account_anchor_manual
    AnchorSource.BROKER -> R.string.account_anchor_broker
    AnchorSource.IMPORT -> R.string.account_anchor_import
    AnchorSource.UNKNOWN -> R.string.account_anchor_other
})

private fun LocalDateTime.text(): String =
    java.time.LocalDateTime.of(year, month.toInt(), day.toInt(), hour.toInt(), minute.toInt()).let {
        DateFormats.shortDate(it.toLocalDate()) + " " + DateFormats.time(it)
    }

/** A plain outlined field; kept for the forms not yet on [FormTextField]. */
@Composable
internal fun CoreTextField(value: String, label: String, saving: Boolean, changed: (String) -> Unit) =
    FormTextField(value, label, changed, enabled = !saving)
