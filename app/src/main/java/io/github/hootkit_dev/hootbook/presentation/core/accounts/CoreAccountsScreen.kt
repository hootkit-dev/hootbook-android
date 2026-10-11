@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.accounts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.Account
import dev.hootkit.core.AccountKind
import dev.hootkit.core.BalanceAnchor
import dev.hootkit.core.Pocket
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.adaptive.LocalWindowLayout
import io.github.hootkit_dev.hootbook.presentation.ui.components.AddFab
import io.github.hootkit_dev.hootbook.presentation.ui.components.ConfirmDialog
import io.github.hootkit_dev.hootbook.presentation.ui.components.ErrorBanner
import io.github.hootkit_dev.hootbook.presentation.ui.components.MenuAction
import io.github.hootkit_dev.hootbook.presentation.ui.components.OverflowMenu
import io.github.hootkit_dev.hootbook.presentation.ui.components.SecondaryScreen
import io.github.hootkit_dev.hootbook.presentation.ui.components.TooltipIconButton
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.ManageAccountsList
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.accountKindTitle

/**
 * Accounts: the grouped list, and one account's page beside it (wide) or instead of it (narrow).
 * Account-level actions live in the top bar; everything else is a section of the page.
 */
@Composable
fun CoreAccountsScreen(
    onBack: () -> Unit,
    onTransactions: (accountId: String) -> Unit,
    viewModel: CoreAccountsViewModel = hiltViewModel(),
    initialAccountId: String? = null,
    initialHistory: Boolean = false,
    initialKind: AccountKind? = null,
    onBrokerage: (Long, String) -> Unit = { _, _ -> },
    onConnectBroker: () -> Unit = {},
    initialCreate: Boolean = false,
    onTransaction: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(initialAccountId, initialHistory) { viewModel.initialize(initialAccountId, initialHistory) }
    val goBack: () -> Unit = { if (!viewModel.back()) onBack() }
    BackHandler(onBack = goBack)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    var pocketEditor by remember { mutableStateOf<PocketEditor?>(null) }
    var anchorsOpen by rememberSaveable { mutableStateOf(false) }
    var deleteAccount by remember { mutableStateOf<Account?>(null) }
    var deletePocket by remember { mutableStateOf<Pocket?>(null) }
    var deleteAnchor by remember { mutableStateOf<BalanceAnchor?>(null) }
    LaunchedEffect(state.commandRevision) {
        pocketEditor = null; deleteAccount = null; deletePocket = null; deleteAnchor = null
    }
    val wide = LocalWindowLayout.current.widthDp >= 720
    fun addAccount(kind: AccountKind? = initialKind) {
        viewModel.edit()
        kind?.let { viewModel.state.value.draft?.let { draft -> viewModel.draftChanged(draft.copy(kind = it)) } }
    }
    InitialCreate(initialCreate, state, ::addAccount, onBack)
    val account = state.account
    val title = if (!wide && account != null) account.name
        else stringResource(initialKind?.let(::accountKindTitle) ?: R.string.title_accounts)

    SecondaryScreen(
        title = title, onBack = goBack,
        actions = { if (account != null) AccountActions(account, !state.saving, viewModel, onDelete = { deleteAccount = it }) },
        floatingActionButton = {
            if (state.selectedId == null || wide) AddFab(stringResource(R.string.add_account_fab_desc), { addAccount() })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (state.navigationBlocked) Text(stringResource(R.string.account_saving_wait), Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium)
            state.error?.let { ErrorBanner(it, viewModel::clearError, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            Row(Modifier.fillMaxSize()) {
                if (state.selectedId == null || wide) {
                    ManageAccountsList(state, initialKind, viewModel::select, { addAccount(it) }, Modifier.weight(1f).fillMaxHeight())
                }
                if (account != null) AccountDetail(
                    state = state, account = account, assets = viewModel.platformAssets,
                    onSelectCurrency = viewModel::selectCurrency,
                    onEditPocket = { pocketEditor = it },
                    onDeletePocket = { viewModel.selectCurrency(it.currency); deletePocket = it },
                    onTransactions = { viewModel.transactionNavigationTarget()?.let(onTransactions) },
                    onTransaction = onTransaction,
                    onHistory = viewModel::openHistory,
                    onAnchors = { anchorsOpen = true },
                    onBrokerage = onBrokerage,
                    modifier = Modifier.weight(if (wide) 1.35f else 1f).fillMaxHeight(),
                )
            }
        }
    }

    state.draft?.let { draft ->
        AccountEditorSheet(draft, state.institutions, state.saving, state.error, viewModel::draftChanged,
            viewModel::dismissEditor, viewModel::saveAccount, onConnectBroker)
    }
    pocketEditor?.let { editor ->
        PocketSheet(editor, state.saving, state.error, { pocketEditor = null }) { value ->
            val id = state.selectedId ?: return@PocketSheet
            when (value.action) {
                PocketAction.ADD -> viewModel.addPocket(id, value.currency, value.balance, value.at)
                PocketAction.CALIBRATE -> viewModel.calibrate(id, value.currency, value.balance, value.at)
                PocketAction.LIMIT -> viewModel.setPocketLimit(id, value.currency, value.balance)
                PocketAction.ANCHOR -> viewModel.updateAnchor(value.anchorId!!, value.balance, value.at, value.note)
            }
        }
    }
    if (state.historyOpen) BalanceHistorySheet(state, viewModel::historyPeriod, viewModel::balanceAt, viewModel::closeHistory)
    if (anchorsOpen) AnchorsSheet(state, onDismiss = { anchorsOpen = false },
        onEdit = { anchor -> pocketEditor = PocketEditor.anchor(anchor, state.currency.orEmpty()) },
        onDelete = { deleteAnchor = it })

    deleteAccount?.let { target ->
        val count = state.transactionCount
        ConfirmDialog(
            title = stringResource(R.string.account_delete_title, target.name),
            text = if (count == 0u) stringResource(R.string.account_delete_note_empty)
                else stringResource(R.string.account_delete_note, count.toString()),
            confirmLabel = stringResource(if (count == 0u) R.string.delete else R.string.account_delete_with_transactions),
            onConfirm = { viewModel.delete(target, count > 0u) }, onDismiss = { deleteAccount = null },
            destructive = true, enabled = !state.saving,
        )
    }
    deletePocket?.let { pocket ->
        val blocked = state.currency == pocket.currency && state.pocketTransactionCount > 0u
        ConfirmDialog(
            title = stringResource(R.string.account_currency_delete_title, pocket.currency),
            text = if (blocked) stringResource(R.string.account_currency_delete_blocked, state.pocketTransactionCount.toString())
                else stringResource(R.string.account_currency_delete_note),
            confirmLabel = stringResource(R.string.account_currency_delete),
            onConfirm = { viewModel.removePocket(state.selectedId!!, pocket.currency) }, onDismiss = { deletePocket = null },
            destructive = true, enabled = !state.saving, confirmEnabled = !blocked && !state.loading,
        )
    }
    deleteAnchor?.let { anchor ->
        ConfirmDialog(
            title = stringResource(R.string.account_anchor_delete_title), text = stringResource(R.string.account_anchor_delete_note),
            confirmLabel = stringResource(R.string.delete), onConfirm = { viewModel.deleteAnchor(anchor.id) },
            onDismiss = { deleteAnchor = null }, destructive = true, enabled = !state.saving,
        )
    }
}

/** Edit in the bar; the rest of an account's actions behind ⋮, delete last and in error color. */
@Composable
private fun AccountActions(account: Account, enabled: Boolean, viewModel: CoreAccountsViewModel, onDelete: (Account) -> Unit) {
    TooltipIconButton(Icons.Rounded.Edit, stringResource(R.string.edit), { viewModel.edit(account) }, enabled = enabled)
    OverflowMenu(enabled = enabled, actions = listOf(
        if (account.isDefault) MenuAction(stringResource(R.string.account_clear_default), Icons.Rounded.StarOutline) { viewModel.setDefault(account) }
        else MenuAction(stringResource(R.string.account_set_default), Icons.Rounded.Star) { viewModel.setDefault(account) },
        if (account.archived) MenuAction(stringResource(R.string.account_unarchive), Icons.Rounded.Unarchive) { viewModel.archive(account) }
        else MenuAction(stringResource(R.string.account_archive), Icons.Rounded.Archive) { viewModel.archive(account) },
        MenuAction(stringResource(R.string.account_move_up), Icons.Rounded.ArrowUpward) { viewModel.move(account, -1) },
        MenuAction(stringResource(R.string.account_move_down), Icons.Rounded.ArrowDownward) { viewModel.move(account, 1) },
        MenuAction(stringResource(R.string.account_delete), Icons.Rounded.Delete, destructive = true) { onDelete(account) },
    ))
}

/** The "add account" route opens the editor once, and leaves the screen once it closes. */
@Composable
private fun InitialCreate(initialCreate: Boolean, state: CoreAccountsUiState, addAccount: () -> Unit, onBack: () -> Unit) {
    var opened by rememberSaveable { mutableStateOf(false) }
    var observedDraft by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialCreate, state.loading) {
        if (initialCreate && !state.loading && !opened) {
            opened = true
            if (state.draft == null) addAccount()
        }
    }
    LaunchedEffect(initialCreate, state.draft != null, state.saving) {
        if (initialCreate) {
            if (state.draft != null) observedDraft = true
            else if (observedDraft && !state.saving) onBack()
        }
    }
}
