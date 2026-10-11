@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow

import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import io.github.hootkit_dev.hootbook.presentation.format.toJavaDateTime
import io.github.hootkit_dev.hootbook.presentation.format.toCoreDateTime
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore
import io.github.hootkit_dev.hootbook.presentation.ui.components.AddEditPersonSheet

import androidx.compose.material3.LoadingIndicator
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookDialogDefaults
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogConfirmButton
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogDismissButton
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.MotionDurations

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.effects.BlurredAnimatedVisibility
import io.github.hootkit_dev.hootbook.presentation.ui.components.BrandIcon
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookCard
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookCheckbox
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.DeleteMultipleRecordsDialog
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionHeader
import io.github.hootkit_dev.hootbook.presentation.ui.components.listSingleItemShape
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.ActionContent
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Bag
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Danger
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Edit2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Messages
import io.github.hootkit_dev.hootbook.presentation.ui.icons.ReceiptItem
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Telegram
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Whatsapp
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.LocalBlurEffects
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation as CurrencyFormatter
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalHazeApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.PersonDetailScreen(
    onNavigateBack: () -> Unit,
    onTransactionClick: (String, String) -> Unit = { _, _ -> },
    sharedElementKey: String? = null,
    animatedContentScope: AnimatedContentScope? = null,
    viewModel: PersonDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val person = uiState.person

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }
    val blurEffects = LocalBlurEffects.current
    val lazyListState = rememberLazyListState()

    val isNameVisible by remember(person) {
        derivedStateOf {
            if (person == null || lazyListState.firstVisibleItemIndex > 0) {
                false
            } else {
                // If index 0 (header card) is visible, check offset.
                // A threshold of about 300-400px usually covers the name part.
                lazyListState.firstVisibleItemScrollOffset < 300
            }
        }
    }

    BackHandler(enabled = uiState.isSelectionMode) {
        viewModel.toggleSelectionMode()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = if (uiState.isSelectionMode) {
                    stringResource(R.string.items_selected_format, uiState.selectedRecordIds.size)
                } else {
                    person?.name ?: stringResource(R.string.khata_title)
                },
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                hasActionButton = !uiState.isSelectionMode,
                navigationContent = {
                    if (uiState.isSelectionMode) {
                        NavigationContent { viewModel.toggleSelectionMode() }
                    } else {
                        NavigationContent(onNavigateBack)
                    }
                },
                actionContent = {
                    if (uiState.isSelectionMode) {
                        BlurredAnimatedVisibility(uiState.isSelectionMode) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                val allSelected = uiState.selectedRecordIds.size == uiState.transactions.size &&
                                        uiState.transactions.isNotEmpty()
                                IconButton(
                                    onClick = {
                                        if (allSelected) viewModel.clearSelection()
                                        else viewModel.selectAllRecords()
                                    },
                                    shapes = IconButtonDefaults.shapes()
                                ) {
                                    Icon(
                                        imageVector = if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                                        contentDescription = if (allSelected) stringResource(R.string.deselect_all) else stringResource(R.string.select_all),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { showDeleteConfirmation = true },
                                    enabled = uiState.selectedRecordIds.isNotEmpty(),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    shapes = IconButtonDefaults.shapes(),
                                    modifier = Modifier.padding(end = 16.dp)
                                ) {
                                    Icon(
                                        imageVector = Iconax.Bag,
                                        contentDescription = stringResource(R.string.delete_selected),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        ActionContent(
                            showMenu = showMenu,
                            onActionClick = { showMenu = true },
                            onDismissMenu = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_person)) },
                                leadingIcon = { Icon(Iconax.Edit2, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.showEditPersonSheet(true)
                                }
                            )
                            HorizontalDivider(
                                thickness = 1.5.dp,
                                color = MaterialTheme.colorScheme.surface
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_person)) },
                                leadingIcon = {
                                    Icon(
                                        Iconax.Bag,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    viewModel.showDeleteConfirmDialog(true)
                                }
                            )
                        }
                    }
                },
                blurEffects = blurEffects,
                showTitleInLargeBar = !isNameVisible
            )
        },
        floatingActionButton = {
            if (person != null && !uiState.isSelectionMode) {
                FloatingActionButton(
                    onClick = { viewModel.showAddTransactionSheet(true) }
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_entry))
                }
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = {
                    Snackbar(snackbarData = it)
                }
            )
        },
    ) { paddingValues ->
        uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(paddingValues)) }
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
            return@Scaffold
        }

        if (person == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.no_persons_yet))
            }
            return@Scaffold
        }

        val reminderMessage = stringResource(
            R.string.reminder_message_format,
            person.name,
            CurrencyFormatter.formatCurrency(person.totals.net.abs(), uiState.baseCurrency)
        )

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = 80.dp
            )
        ) {
            item {
                if (!uiState.isSelectionMode) {
                    PersonHeaderCard(
                        person = person,
                        attachmentStore = viewModel.attachmentStore,
                        onSettleClick = { viewModel.showSettleSheet(true) },
                        onCallClick = {
                            person.phone?.let { phone ->
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                context.startActivity(intent)
                            }
                        },
                        onWhatsAppClick = {
                            person.phone?.let { phone ->
                                val normalized = phone.filter { it.isDigit() }
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$normalized?text=${Uri.encode(reminderMessage)}"))
                                context.startActivity(intent)
                            }
                        },
                        onTelegramClick = {
                            person.phone?.let { phone ->
                                val normalized = phone.filter { it.isDigit() }
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+$normalized?text=${Uri.encode(reminderMessage)}"))
                                context.startActivity(intent)
                            }
                        },
                        currency = uiState.baseCurrency,
                        sharedElementKey = sharedElementKey,
                        animatedContentScope = animatedContentScope
                    )
                    Spacer(modifier = Modifier.height(Dimensions.Padding.content))
                }
            }

            item {
                SectionHeader(
                    title = stringResource(R.string.all_txns_tab),
                    modifier = Modifier.padding(start = Dimensions.Padding.content, bottom = Dimensions.Radius.sm)
                )
            }

            if (uiState.transactions.isEmpty()) {
                item {
                    EmptyTransactionsState()
                }
            } else {
                itemsIndexed(uiState.transactions, key = { _, item -> item.id }) { index, item ->
                    val linked = uiState.linkedTransactions[item.transactionId]
                    val category = uiState.categories[linked?.categoryId]
                    val position = ListItemPosition.from(index, uiState.transactions.size)
                    LendBorrowTransactionListItem(
                        item = item,
                        categoryEntity = category,
                        transaction = linked,
                        onClick = {
                            if (uiState.isSelectionMode) {
                                viewModel.toggleRecordSelection(item.id)
                            } else {
                                item.transactionId?.let { onTransactionClick(it, "transaction_$it") }
                            }
                        },
                        onLongClick = {
                            if (uiState.isSelectionMode) {
                                viewModel.toggleRecordSelection(item.id)
                            } else {
                                viewModel.showTransactionActions(item)
                            }
                        },
                        isSelectionMode = uiState.isSelectionMode,
                        isSelected = uiState.selectedRecordIds.contains(item.id),
                        onSelectionToggle = { viewModel.toggleRecordSelection(item.id) },
                        currency = uiState.baseCurrency,
                        shape = position.toShape(),
                        animatedContentScope = animatedContentScope,
                        sharedElementKey = item.transactionId?.let { "transaction_$it" }
                    )
                    Spacer(modifier = Modifier.height(1.5.dp))
                }
            }
        }

        if (uiState.showEditPersonSheet) {
            AddEditPersonSheet(
                personToEdit = person,
                saving = uiState.saving, error = uiState.error,
                attachmentStore = viewModel.attachmentStore,
                onDismiss = { viewModel.showEditPersonSheet(false) },
                onSave = { name, phone, notes, color, avatar, category ->
                    viewModel.updatePerson(name, phone, notes, color, avatar, category)
                }
            )
        }

        if (uiState.showSettleSheet) {
            SettleUpSheet(
                person = person,
                accounts = uiState.accounts.values.toList(),
                saving = uiState.saving, error = uiState.error,
                defaultAccountId = uiState.transactions.firstOrNull { it.accountId != null }?.accountId,
                onDismiss = { viewModel.showSettleSheet(false) },
                onSettle = viewModel::settle
            )
        }

        if (uiState.showDeleteConfirmDialog) {
            val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            AlertDialog(
                onDismissRequest = { viewModel.showDeleteConfirmDialog(false) },
                icon = {
                    Icon(
                        imageVector = Iconax.Danger,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { Text(stringResource(R.string.delete_person_confirm_title)) },
                text = { Text(stringResource(R.string.delete_person_confirm_desc, person.name)) },
                confirmButton = {
                    DialogConfirmButton(
                        text = stringResource(R.string.delete),
                        onClick = { viewModel.deletePerson(onDeleted = onNavigateBack) },
                        destructive = true
                    )
                },
                dismissButton = {
                    DialogDismissButton(
                        text = stringResource(R.string.cancel),
                        onClick = { viewModel.showDeleteConfirmDialog(false) }
                    )
                },
                containerColor = HootbookDialogDefaults.containerColor
            )
        }
    }

    if (uiState.showAddTransactionSheet) {
        uiState.person?.let { person ->
            AddEditLendBorrowTransactionSheet(
                personsList = listOf(person),
                initialPerson = person,
                accounts = uiState.accounts.values.toList(),
                attachmentStore = viewModel.attachmentStore,
                showPersonSelection = false,
                onDismiss = { viewModel.showAddTransactionSheet(false) },
                onAddPerson = viewModel::createPerson,
                mainCurrency = uiState.baseCurrency,
                saving = uiState.saving, error = uiState.error,
                onSave = viewModel::addTransaction
            )
        }
    }

    if (uiState.showTransactionActionDialog) {
        uiState.transactionForAction?.let { transaction ->
            LendBorrowTransactionActionDialog(
                onDismiss = { viewModel.dismissTransactionActions() },
                onEdit = { viewModel.openTransactionEdit(transaction) },
                onDelete = { viewModel.deleteTransactionRequested(transaction) },
                onSelectMultiple = {
                    viewModel.enterSelectionMode(transaction.id)
                    viewModel.dismissTransactionActions()
                },
                blurEffects = blurEffects,
                hazeState = hazeState
            )
        }
    }

    if (uiState.showEditTransactionSheet) {
        uiState.person?.let { person ->
            AddEditLendBorrowTransactionSheet(
                personsList = listOf(person),
                initialPerson = person,
                accounts = uiState.accounts.values.toList(),
                attachmentStore = viewModel.attachmentStore,
                showPersonSelection = false,
                transactionToEdit = uiState.transactionToEdit,
                onDismiss = { viewModel.hideEditTransactionSheet() },
                onAddPerson = viewModel::createPerson,
                mainCurrency = uiState.baseCurrency,
                saving = uiState.saving, error = uiState.error,
                onSave = viewModel::addTransaction,
                onUpdate = viewModel::updateTransaction
            )
        }
    }

    if (uiState.showDeleteTransactionDialog) {
        val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        AlertDialog(
            onDismissRequest = { viewModel.dismissTransactionDeleteDialog() },
            title = { Text(stringResource(R.string.delete_record)) },
            text = { Text(stringResource(R.string.delete_transaction_confirm)) },
            confirmButton = {
                DialogConfirmButton(
                    text = stringResource(R.string.delete),
                    onClick = {
                        uiState.transactionForAction?.let { viewModel.deleteTransaction(it.id) }
                    },
                    destructive = true
                )
            },
            dismissButton = {
                DialogDismissButton(
                    text = stringResource(R.string.cancel),
                    onClick = { viewModel.dismissTransactionDeleteDialog() }
                )
            },
            containerColor = HootbookDialogDefaults.containerColor
        )
    }

    if (showDeleteConfirmation) {
        DeleteMultipleRecordsDialog(
            onDelete = {
                val count = uiState.selectedRecordIds.size
                viewModel.deleteSelectedRecords()
                showDeleteConfirmation = false
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.records_deleted_format, count),
                        duration = SnackbarDuration.Short
                    )
                }
            },
            onDismiss = { showDeleteConfirmation = false },
            selectedRecordIds = uiState.selectedRecordIds,
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.PersonHeaderCard(
    person: Person,
    attachmentStore: CoreAttachmentStore,
    onSettleClick: () -> Unit,
    onCallClick: () -> Unit,
    onWhatsAppClick: () -> Unit,
    onTelegramClick: () -> Unit,
    currency: String,
    sharedElementKey: String? = null,
    animatedContentScope: AnimatedContentScope? = null
) {
    val colorInt = try {
        person.color.toColorInt()
    } catch (e: Exception) {
        "#4CAF50".toColorInt()
    }

    val isGet = person.totals.net > BigDecimal.ZERO
    val isOwe = person.totals.net < BigDecimal.ZERO
    val statusText = when {
        isGet -> stringResource(R.string.gets_amount, CurrencyFormatter.formatCurrency(person.totals.net, currency))
        isOwe -> stringResource(R.string.owes_amount, CurrencyFormatter.formatCurrency(person.totals.net.abs(), currency))
        else -> stringResource(R.string.settled_tag)
    }

    val statusColor = when {
        isGet -> Color(0xFF4CAF50)
        isOwe -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val sharedModifier = if (animatedContentScope != null && sharedElementKey != null) {
        Modifier.sharedBounds(
            rememberSharedContentState(key = sharedElementKey),
            animatedVisibilityScope = animatedContentScope,
            boundsTransform = { _, _ ->
                tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing)
            },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center
            )
        )
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .then(sharedModifier)
                .size(84.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color(colorInt)),
            contentAlignment = Alignment.Center
        ) {
            if (person.avatar != null) {
                AsyncImage(
                    model = person.avatar?.let(attachmentStore::resolve),
                    contentDescription = null,
                    modifier = Modifier.size(84.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = person.name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = person.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (!person.totals.complete) Text(stringResource(R.string.core_debts_incomplete), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        if (!person.phone.isNullOrBlank()) {
            Text(
                text = person.phone.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
            )
        }


        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onSettleClick,
                enabled = person.totals.net.compareTo(BigDecimal.ZERO) != 0,
                modifier = Modifier.weight(1f),
                shapes = ButtonDefaults.shapes()
            ) {
                Text(
                    text = stringResource(R.string.settle_up),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = statusColor.copy(alpha = 0.1f),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = statusColor,
                    )
                }
            }
        }

        if (!person.phone.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!person.phone.isNullOrBlank()) {
                Button(
                    shapes = ButtonDefaults.shapes(),
                    onClick = onCallClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = stringResource(R.string.call_person),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Button(
                    shapes = ButtonDefaults.shapes(),
                    onClick = onWhatsAppClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Iconax.Whatsapp,
                        tint = Color.Unspecified,
                        contentDescription = stringResource(R.string.whatsapp_cd),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Button(
                    shapes = ButtonDefaults.shapes(),
                    onClick = onTelegramClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Iconax.Telegram,
                        tint = Color.Unspecified,
                        contentDescription = stringResource(R.string.telegram_cd),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (!person.notes.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Text(
                    text = person.notes.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.LendBorrowTransactionListItem(
    item: DebtEntry,
    categoryEntity: Category? = null,
    transaction: Transaction? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectionToggle: (() -> Unit)? = null,
    currency: String,
    shape: CornerBasedShape = listSingleItemShape,
    animatedContentScope: AnimatedContentScope? = null,
    sharedElementKey: String? = null
) {
    val isIncomeLike = item.kind == DebtKind.REPAID_TO_ME || item.kind == DebtKind.BORROWED
    val amountColor = if (isIncomeLike) Color(0xFF4CAF50) else Color(0xFFE57373)
    val typeLabel = when (item.kind) {
        DebtKind.LENT -> stringResource(R.string.i_lent)
        DebtKind.BORROWED -> stringResource(R.string.i_borrowed)
        DebtKind.REPAID_TO_ME -> stringResource(R.string.settlement_received)
        DebtKind.REPAID_BY_ME -> stringResource(R.string.settlement_paid)
        DebtKind.UNKNOWN -> ""
    }

    val currentYear = remember { java.time.Year.now().value }
    val dueDateFormatter = remember(item.dueDate?.year, currentYear) {
        if (item.dueDate?.year == currentYear) io.github.hootkit_dev.hootbook.utils.DateFormats.monthDayFormatter()
        else io.github.hootkit_dev.hootbook.utils.DateFormats.fullDateFormatter()
    }

    val sharedModifier = if (animatedContentScope != null && sharedElementKey != null) {
        Modifier.sharedBounds(
            rememberSharedContentState(key = sharedElementKey),
            animatedVisibilityScope = animatedContentScope,
            boundsTransform = { _, _ ->
                tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing)
            },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Fit, Alignment.Center),
            clipInOverlayDuringTransition = OverlayClip(shape),
            renderInOverlayDuringTransition = false
        )
            .skipToLookaheadSize()
    } else Modifier

    ListItem(
        modifier = sharedModifier,
        headline = {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supporting = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.basicMarquee()
            ) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.dueDate != null) {
                    Text(
                        text = stringResource(R.string.due_on_format, item.dueDate!!.coreDateText(dueDateFormatter)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        leading = {
            BlurredAnimatedVisibility(isSelectionMode) {
                HootbookCheckbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectionToggle?.invoke() },
                    modifier = Modifier.size(40.dp)
                )
            }
            BlurredAnimatedVisibility(!isSelectionMode) {
                BrandIcon(
                    merchantName = transaction?.merchant.orEmpty(),
                    categoryEntity = categoryEntity,
                    category = categoryEntity?.displayName,
                    size = 40.dp,
                    showBackground = true,
                    modifier = if (animatedContentScope != null && item.transactionId != null) {
                        Modifier.sharedElement(
                            rememberSharedContentState(key = "brand_icon_${item.transactionId}"),
                            animatedVisibilityScope = animatedContentScope
                        )
                    } else Modifier
                )
            }
        },
        trailing = {
            Text(
                text = CurrencyFormatter.formatCurrency(item.amount.amount, item.amount.currency),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = amountColor
            )
        },
        onClick = {
            if (isSelectionMode) {
                onSelectionToggle?.invoke()
            } else {
                onClick?.invoke()
            }
        },
        onLongClick = onLongClick,
        shape = shape,
        listColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        padding = PaddingValues(0.dp)
    )
}

@Composable
private fun EmptyTransactionsState(
    modifier: Modifier = Modifier
) {
    HootbookCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Iconax.ReceiptItem,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.no_transactions_khata),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.transactions_appear_here),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
