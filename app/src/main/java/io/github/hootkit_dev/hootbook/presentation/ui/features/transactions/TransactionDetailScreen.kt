@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import io.github.hootkit_dev.hootbook.utils.pickerDate
import io.github.hootkit_dev.hootbook.utils.toPickerMillis

import io.github.hootkit_dev.hootbook.presentation.ui.theme.transactionTypeColor
import io.github.hootkit_dev.hootbook.presentation.ui.theme.isAppInDarkTheme
import io.github.hootkit_dev.hootbook.presentation.ui.components.TooltipIconButton
import androidx.compose.material3.ripple
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookDialogDefaults
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogConfirmButton
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogDismissButton
import io.github.hootkit_dev.hootbook.utils.displayTitle
import io.github.hootkit_dev.hootbook.presentation.ui.components.maskAccountNumber
import androidx.compose.material.icons.rounded.Bolt

import androidx.compose.animation.core.FastOutSlowInEasing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.MotionDurations

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.core.content.FileProvider
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookCheckbox
import io.github.hootkit_dev.hootbook.presentation.ui.components.TransactionItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.CategoryEntity
import io.github.hootkit_dev.hootbook.data.database.entity.SubcategoryEntity
import io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.domain.model.LendBorrowTransactionItem
import io.github.hootkit_dev.hootbook.data.database.entity.LendBorrowType
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import io.github.hootkit_dev.hootbook.domain.model.PersonInfo
import io.github.hootkit_dev.hootbook.data.service.AttachmentService
import io.github.hootkit_dev.hootbook.presentation.common.icons.BrandIcons
import io.github.hootkit_dev.hootbook.presentation.common.icons.CategoryMapping
import io.github.hootkit_dev.hootbook.presentation.effects.BlurredAnimatedVisibility
import io.github.hootkit_dev.hootbook.utils.capitalizeFirst
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountSelectionSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.AttachmentSection
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountField
import io.github.hootkit_dev.hootbook.presentation.ui.components.BrandIcon
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookCard
import io.github.hootkit_dev.hootbook.presentation.ui.components.CategoryIcon
import io.github.hootkit_dev.hootbook.presentation.ui.components.CategorySelectionSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.DashedLine
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomBillingCycleCard
import io.github.hootkit_dev.hootbook.presentation.ui.components.DatePicker
import io.github.hootkit_dev.hootbook.presentation.ui.components.DeleteTransactionDialog
import io.github.hootkit_dev.hootbook.presentation.ui.components.LoadingCircle
import io.github.hootkit_dev.hootbook.presentation.ui.components.PreferenceSwitch
import io.github.hootkit_dev.hootbook.presentation.ui.components.SearchBarBox
import io.github.hootkit_dev.hootbook.presentation.ui.components.TimePicker
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.NumberPad
import io.github.hootkit_dev.hootbook.presentation.ui.features.add.AmountInput
import io.github.hootkit_dev.hootbook.presentation.ui.icons.ArrowLeft02
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Bag
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Box2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Calendar
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Card
import io.github.hootkit_dev.hootbook.presentation.ui.icons.CloseCircle
import io.github.hootkit_dev.hootbook.presentation.ui.icons.DocumentText2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Edit2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Folder2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Menu
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Messages
import io.github.hootkit_dev.hootbook.presentation.ui.icons.RefreshCircle
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Search
import io.github.hootkit_dev.hootbook.presentation.ui.icons.VideoTime
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Wallet3
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.credit_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.credit_light
import io.github.hootkit_dev.hootbook.presentation.ui.theme.loan_light
import io.github.hootkit_dev.hootbook.presentation.ui.theme.loan_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.expense_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.expense_light
import io.github.hootkit_dev.hootbook.presentation.ui.theme.income_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.income_light
import io.github.hootkit_dev.hootbook.presentation.ui.theme.investment_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.investment_light
import io.github.hootkit_dev.hootbook.presentation.ui.theme.transfer_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.transfer_light
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import io.github.hootkit_dev.hootbook.utils.IconResolutionUtils
import io.github.hootkit_dev.hootbook.utils.SubscriptionUtils
import io.github.hootkit_dev.hootbook.utils.formatAmount
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.allowHardware
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow.AddEditLendBorrowTransactionSheet
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Copy
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
fun SharedTransitionScope.TransactionDetailScreen(
    transactionId: Long,
    sharedElementKey: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToPersonDetail: (personId: Long) -> Unit = {},
    transactionDetailViewModel: TransactionDetailViewModel = hiltViewModel(),
    animatedContentScope: AnimatedContentScope? = null,
    blurEffects: Boolean,
) {
    val uiState by transactionDetailViewModel.uiState.collectAsStateWithLifecycle()
    val transaction = uiState.transaction
    val isEditMode = uiState.isEditMode
    val editableTransaction = uiState.editableTransaction
    val isSaving = uiState.isSaving
    val saveSuccess = uiState.saveSuccess
    val errorMessage = uiState.errorMessage
    val updateExistingTransactions = uiState.updateExistingTransactions
    val existingTransactionCount = uiState.existingTransactionCount
    val showMatchPreviewSheet = uiState.showMatchPreviewSheet
    val matchedTransactions = uiState.matchedTransactions
    val selectedMatchIds = uiState.selectedMatchIds
    val showDeleteDialog = uiState.showDeleteDialog
    val isDeleting = uiState.isDeleting
    val deleteSuccess = uiState.deleteSuccess
    val accountPrimaryCurrency = uiState.primaryCurrency
    val convertedAmount = uiState.convertedAmount
    val availableAccounts by transactionDetailViewModel.availableAccounts.collectAsStateWithLifecycle()
    val allSubcategories by transactionDetailViewModel.allSubcategories.collectAsStateWithLifecycle()
    val categories by transactionDetailViewModel.categories.collectAsStateWithLifecycle()
    val linkedSubscription = uiState.subscription
    val editableAttachments by transactionDetailViewModel.editableAttachments.collectAsStateWithLifecycle()
    val persons by transactionDetailViewModel.persons.collectAsStateWithLifecycle()

    val markedAsLoanSuccessStr = stringResource(R.string.marked_as_loan_success)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val rootView = LocalView.current
    val isDarkTheme = uiState.darkThemeConfig ?: isAppInDarkTheme
    val isAmoledMode = uiState.isAmoledMode

    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var showNumberPad by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var showTargetAccountSheet by remember { mutableStateOf(false) }
    var showBillingCycleMenu by remember { mutableStateOf(false) }
    var showCustomCountPad by remember { mutableStateOf(false) }
    var showCustomUnitMenu by remember { mutableStateOf(false) }
    var showCustomEndDatePicker by remember { mutableStateOf(false) }
    val hazeState = remember { HazeState() }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val personColor = remember(uiState.linkedLendBorrow, persons) {
        persons.find { it.id == uiState.linkedLendBorrow?.personId }?.color
    }

    val personAvatar = remember(uiState.linkedLendBorrow, persons) {
        persons.find { it.id == uiState.linkedLendBorrow?.personId }?.avatar
    }

    // Custom Billing Cycle Count Pad
    if (showCustomCountPad && isEditMode) {
        HootbookModalBottomSheet(
            onDismissRequest = { showCustomCountPad = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            NumberPad(
                initialValue = uiState.customCycleCount.toString(),
                onDone = { newCount ->
                    transactionDetailViewModel.updateSubscriptionCustomCycleCount(newCount.toIntOrNull() ?: 1)
                    showCustomCountPad = false
                },
                title = stringResource(R.string.repeat_every)
            )
        }
    }

    // Custom Billing Cycle End Date Picker
    if (showCustomEndDatePicker && isEditMode) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = (uiState.customCycleEndDate ?: LocalDate.now()).toPickerMillis()
        )
        DatePicker(
            onDismiss = { showCustomEndDatePicker = false },
            onConfirm = {
                datePickerState.selectedDateMillis?.let { millis ->
                    val localDate = pickerDate(millis)
                    transactionDetailViewModel.updateSubscriptionCustomCycleEndDate(localDate)
                }
                showCustomEndDatePicker = false
            },
            datePickerState = datePickerState,
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }

    // Show success snackbar
    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.transaction_updated_successfully))
                transactionDetailViewModel.clearSaveSuccess()
            }
        }
    }

    // Show error snackbar
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
            }
        }
    }

    LaunchedEffect(transactionId) {
        transactionDetailViewModel.loadTransaction(transactionId)
    }

    // Handle delete success
    LaunchedEffect(deleteSuccess) {
        if (deleteSuccess) {
            onNavigateBack()
        }
    }

    // Handle duplicate success
    LaunchedEffect(uiState.duplicateSuccess) {
        if (uiState.duplicateSuccess) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.transaction_duplicated))
                transactionDetailViewModel.clearDuplicateSuccess()
            }
        }
    }

    // Handle mark-as-loan success / error
    LaunchedEffect(uiState.markAsLoanSuccess, uiState.markAsLoanError) {
        val loanSuccess = uiState.markAsLoanSuccess
        val loanError = uiState.markAsLoanError
        if (loanSuccess) {
            scope.launch {
                snackbarHostState.showSnackbar(markedAsLoanSuccessStr)
                transactionDetailViewModel.clearMarkAsLoanResult()
            }
        } else if (!loanError.isNullOrBlank()) {
            scope.launch {
                snackbarHostState.showSnackbar(loanError)
                transactionDetailViewModel.clearMarkAsLoanResult()
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection).then(
            if (animatedContentScope != null) {
                Modifier.sharedBounds(
                    rememberSharedContentState(key = sharedElementKey ?: "transaction_$transactionId"),
                    animatedVisibilityScope = animatedContentScope,
                    boundsTransform = { _, _ ->
                        tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing)
                    },
                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Inside, Alignment.Center),
                    clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(Spacing.xxl))
                ).skipToLookaheadSize()
            } else {Modifier}
        ),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = if (isEditMode) stringResource(R.string.edit_transaction) else stringResource(R.string.transaction_details),
                hasBackButton = true,
                hasActionButton = true,
                hazeState = hazeState,
                navigationContent = {
                    TransactionNavigationContent(
                        isEditMode = isEditMode,
                        onBackClick = {
                            if (isEditMode) {
                                transactionDetailViewModel.cancelEdit()
                            } else {
                                onNavigateBack()
                            }
                        }
                    )
                },
                actionContent = {
                    if(!isEditMode){
                        Box(
                            modifier = Modifier
                                .animateContentSize()
                                .padding(end = 16.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    onClick ={ transactionDetailViewModel.enterEditMode() },
                                ),
                        ) {
                            IconButton(
                                shapes = IconButtonDefaults.shapes(),
                                onClick = { transactionDetailViewModel.enterEditMode() },
                            ) {
                                Icon(
                                    imageVector = Iconax.Edit2,
                                    contentDescription = stringResource(R.string.edit),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else{
                        Box(modifier = Modifier.size(32.dp)) //for edit transaction title alignment
                    }
                }
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            val displayTransaction = if (isEditMode) editableTransaction else transaction
            displayTransaction?.let { txn ->
                TransactionDetailContent(
                    transaction = txn,
                    isEditMode = isEditMode,
                    updateExistingTransactions = updateExistingTransactions,
                    existingTransactionCount = existingTransactionCount,
                    viewModel = transactionDetailViewModel,
                    accountPrimaryCurrency = accountPrimaryCurrency,
                    convertedAmount = convertedAmount,
                    availableAccounts = availableAccounts,
                    onAmountClick = { showNumberPad = true },
                    onCategoryClick = { showCategoryMenu = true },
                    onAccountClick = { showAccountSheet = true },
                    onTargetAccountClick = { showTargetAccountSheet = true },
                    showBillingCycleMenu = showBillingCycleMenu,
                    onBillingCycleMenuChange = { showBillingCycleMenu = it },
                    showCustomCountPad = { showCustomCountPad = it },
                    showCustomUnitMenu = { showCustomUnitMenu = it },
                    showCustomEndDatePicker = { showCustomEndDatePicker = it },
                    paddingValues = paddingValues,
                    categories = categories,
                    subcategoriesMap = allSubcategories,
                    linkedSubscription = linkedSubscription,
                    editableAttachments = editableAttachments,
                    onAddAttachment = transactionDetailViewModel::addAttachment,
                    onRemoveAttachment = transactionDetailViewModel::removeAttachment,
                    blurEffects = blurEffects,
                    hazeState = hazeState,
                    accountIconName = uiState.accountIconName,
                    isAmoledMode = isAmoledMode,
                    isDarkTheme = isDarkTheme,
                     animatedContentScope = animatedContentScope,
                     sharedTransitionScope = this@TransactionDetailScreen,
                     linkedLendBorrow = uiState.linkedLendBorrow,
                     linkedLoanPersonName = uiState.linkedLoanPersonName,
                     linkedLoanPersonColor = personColor,
                     linkedLoanPersonAvatar = personAvatar,
                     onNavigateToPersonDetail = onNavigateToPersonDetail
                 )
            }

            if (isEditMode) {
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
                ){
                    TransactionSaveContent(
                        isSaving = isSaving,
                        onSaveClick = { transactionDetailViewModel.saveChanges() },
                        modifier = Modifier
                    )
                }
            } else{
                var showMoreMenu by remember { mutableStateOf(false) }
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
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                val txn = transaction ?: return@TextButton
                                val bitmap = captureReceiptToBitmap(
                                    rootView = rootView,
                                    context = context,
                                    transaction = txn,
                                    primaryCurrency = accountPrimaryCurrency,
                                    convertedAmount = convertedAmount,
                                    categories = categories,
                                    subcategoriesMap = allSubcategories,
                                    linkedSubscription = linkedSubscription,
                                    availableAccounts = availableAccounts,
                                    attachmentService = transactionDetailViewModel.attachmentService,
                                    isDarkTheme = isDarkTheme,
                                    isAmoledMode = isAmoledMode,
                                    linkedLendBorrow = uiState.linkedLendBorrow,
                                    linkedLoanPersonName = uiState.linkedLoanPersonName,
                                    linkedLoanPersonColor = personColor,
                                    linkedLoanPersonAvatar = personAvatar
                                )
                                shareReceiptAsPng(
                                    context = context,
                                    bitmap = bitmap,
                                    transaction = txn,
                                    fileName = "receipt_${txn.id}"
                                )
                            },
                            enabled = !isSaving,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            contentPadding = PaddingValues(vertical = Spacing.md)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = stringResource(R.string.share_receipt),
                                    modifier = Modifier.size(Dimensions.Icon.small)
                                )
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text(
                                    text = stringResource(R.string.share),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        Box {
                            IconButton(
                                shapes = IconButtonDefaults.shapes(),
                                onClick = { showMoreMenu = true },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.size(50.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreHoriz,
                                    contentDescription = stringResource(R.string.more_options_desc),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            // A standard Material 3 menu: opaque tonal container, no blur, no dividers
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.duplicate_transaction)) },
                                    onClick = {
                                        showMoreMenu = false
                                        transactionDetailViewModel.duplicateTransaction()
                                    },
                                    leadingIcon = { Icon(Iconax.Copy, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.quick_template_save_from_detail)) },
                                    onClick = {
                                        showMoreMenu = false
                                        transactionDetailViewModel.saveAsQuickTemplate()
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Bolt, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete_transaction)) },
                                    onClick = {
                                        showMoreMenu = false
                                        transactionDetailViewModel.showDeleteDialog()
                                    },
                                    leadingIcon = { Icon(Iconax.Bag, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (uiState.linkedLendBorrow != null) {
                                                stringResource(R.string.unmark_as_loan)
                                            } else {
                                                stringResource(R.string.mark_as_loan)
                                            }
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        if (uiState.linkedLendBorrow != null) {
                                            transactionDetailViewModel.showUnmarkLoanConfirm()
                                        } else {
                                            transactionDetailViewModel.showMarkAsLoanSheet()
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.AccountBalanceWallet,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // NumberPad for Amount Input
    if (showNumberPad && isEditMode) {
        HootbookModalBottomSheet(
            onDismissRequest = { showNumberPad = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            val amount = editableTransaction?.amount?.stripTrailingZeros()?.toPlainString() ?: "0"
            NumberPad(
                initialValue = amount,
                onDone = { newAmount ->
                    transactionDetailViewModel.updateAmount(newAmount)
                    showNumberPad = false
                },
                title = stringResource(R.string.enter_amount)
            )
        }
    }

    // Category Selection Sheet
    if (showCategoryMenu) {
        HootbookModalBottomSheet(
            onDismissRequest = { showCategoryMenu = false },
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            CategorySelectionSheet(
                categories = categories,
                subcategoriesMap = allSubcategories,
                onSelectionComplete = { category, subcategory ->
                    transactionDetailViewModel.updateCategory(category.name)
                    transactionDetailViewModel.updateSubcategory(subcategory?.name)
                    showCategoryMenu = false
                },
                onDismiss = { showCategoryMenu = false }
            )
        }
    }

    // Account Selection Sheets
    if (showAccountSheet) {
        val accounts by transactionDetailViewModel.availableAccounts.collectAsStateWithLifecycle()
        val selectedAccount by transactionDetailViewModel.selectedAccount.collectAsStateWithLifecycle()
        HootbookModalBottomSheet(
            onDismissRequest = { showAccountSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            AccountSelectionSheet(
                accounts = accounts,
                selectedAccount = selectedAccount,
                onAccountSelected = {
                    transactionDetailViewModel.updateTransactionAccount(it)
                    showAccountSheet = false
                },
                showNoneOption = false
            )
        }
    }

    if (showTargetAccountSheet) {
        val accounts by transactionDetailViewModel.availableAccounts.collectAsStateWithLifecycle()
        val targetAccount by transactionDetailViewModel.targetAccount.collectAsStateWithLifecycle()
        HootbookModalBottomSheet(
            onDismissRequest = { showTargetAccountSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            AccountSelectionSheet(
                accounts = accounts,
                selectedAccount = targetAccount,
                title = stringResource(R.string.select_target_account),
                onAccountSelected = {
                    transactionDetailViewModel.updateTransactionTargetAccount(it)
                    showTargetAccountSheet = false
                },
                showNoneOption = false
            )
        }
    }

    // Match Preview Sheet — allows granular per-transaction selection before applying
    if (showMatchPreviewSheet) {
        val previewSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        HootbookModalBottomSheet(
            onDismissRequest = { transactionDetailViewModel.hideMatchPreviewSheet() },
            sheetState = previewSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            MatchPreviewSheetContent(
                matchedTransactions = matchedTransactions,
                selectedMatchIds = selectedMatchIds,
                searchQuery = uiState.matchSearchQuery,
                searchResults = uiState.matchSearchResults,
                onSearchQueryChange = { transactionDetailViewModel.updateMatchSearchQuery(it) },
                onAddSearchResult = { transactionDetailViewModel.addTransactionToMatchList(it) },
                onToggleSelection = { transactionDetailViewModel.toggleMatchSelection(it) },
                onSelectAll = { transactionDetailViewModel.selectAllMatches() },
                onDeselectAll = { transactionDetailViewModel.deselectAllMatches() },
                onApply = {
                    transactionDetailViewModel.applyToSelectedMatches()
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.updated_transactions_format, selectedMatchIds.size)) }
                },
                onDismiss = { transactionDetailViewModel.hideMatchPreviewSheet() },
                newCategory = editableTransaction?.category ?: "",
                isDarkTheme = uiState.darkThemeConfig ?: isAppInDarkTheme,
                transactionPersonMapping = uiState.transactionPersonMapping
            )
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        DeleteTransactionDialog(
            onDismiss = { transactionDetailViewModel.hideDeleteDialog() },
            onDelete = { transactionDetailViewModel.deleteTransaction() },
            isDeleting = isDeleting,
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }

    // Mark as loan: lend/borrow entry sheet
    if (uiState.showMarkAsLoanSheet) {
        AddEditLendBorrowTransactionSheet(
            personsList = persons,
            initialPerson = null,
            accounts = availableAccounts,
            categories = categories,
            attachmentService = transactionDetailViewModel.attachmentService,
            showPersonSelection = true,
            transactionToEdit = null,
            initialType = when (transaction?.transactionType) {
                TransactionType.LENT -> LendBorrowType.LENT
                TransactionType.BORROWED -> LendBorrowType.BORROWED
                TransactionType.EXPENSE -> LendBorrowType.LENT
                else -> LendBorrowType.BORROWED
            },
            initialAmount = transaction?.amount,
            initialTitle = transaction?.merchantName,
            blurEffects = blurEffects,
            onDismiss = { transactionDetailViewModel.hideMarkAsLoanSheet() },
            onAddPerson = { name, _, _, _, _, _ -> transactionDetailViewModel.addPerson(name) },
            onSave = { personId, type, amount, title, _, _, _, _, _ ->
                transactionDetailViewModel.markAsLoan(personId, type, amount, title)
            }
        )
    }

    // Unmark as loan confirmation
    if (uiState.showUnmarkLoanConfirm) {
        AlertDialog(
            onDismissRequest = { transactionDetailViewModel.hideUnmarkLoanConfirm() },
            title = { Text(stringResource(R.string.unmark_loan_confirm_title)) },
            text = { Text(stringResource(R.string.unmark_loan_confirm_desc)) },
            confirmButton = {
                DialogConfirmButton(
                    text = stringResource(R.string.unmark_as_loan),
                    onClick = { transactionDetailViewModel.unmarkAsLoan() }
                )
            },
            dismissButton = {
                DialogDismissButton(
                    text = stringResource(R.string.cancel),
                    onClick = { transactionDetailViewModel.hideUnmarkLoanConfirm() }
                )
            },
            containerColor = HootbookDialogDefaults.containerColor
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TransactionNavigationContent(
    isEditMode: Boolean,
    onBackClick: () -> Unit
) {
    TooltipIconButton(
        icon = if (isEditMode) Icons.Rounded.Close else Iconax.ArrowLeft02,
        label = stringResource(if (isEditMode) R.string.cancel else R.string.cd_navigate_back),
        onClick = onBackClick
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TransactionSaveContent(
    modifier: Modifier = Modifier,
    isSaving: Boolean,
    onSaveClick: () -> Unit
) {
    TextButton(
        onClick = onSaveClick,
        enabled = !isSaving,
        shapes = ButtonDefaults.shapes(),
        modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth().navigationBarsPadding(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        contentPadding = PaddingValues(vertical = Spacing.md)
    ) {
        if (isSaving) {
            LoadingCircle(
                modifier = Modifier.size(Dimensions.Icon.small)
            )
        } else {
            Text(
                text = stringResource(R.string.save),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun TransactionDetailContent(
    modifier: Modifier = Modifier,
    transaction: TransactionEntity,
    isEditMode: Boolean,
    updateExistingTransactions: Boolean,
    existingTransactionCount: Int,
    viewModel: TransactionDetailViewModel,
    accountPrimaryCurrency: String,
    convertedAmount: BigDecimal?,
    onAmountClick: () -> Unit,
    onCategoryClick: () -> Unit,
    onAccountClick: () -> Unit,
    onTargetAccountClick: () -> Unit,
    showBillingCycleMenu: Boolean,
    onBillingCycleMenuChange: (Boolean) -> Unit,
    showCustomCountPad: (Boolean) -> Unit,
    showCustomUnitMenu: (Boolean) -> Unit,
    showCustomEndDatePicker: (Boolean) -> Unit,
    paddingValues: PaddingValues,
    availableAccounts: List<AccountBalanceEntity>,
    categories: List<CategoryEntity>,
    subcategoriesMap: Map<Long, List<SubcategoryEntity>>,
    linkedSubscription: SubscriptionEntity? = null,
    editableAttachments: List<String> = emptyList(),
    onAddAttachment: (String) -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    blurEffects: Boolean,
    hazeState: HazeState = remember { HazeState()},
     accountIconName: String?,
     isAmoledMode: Boolean,
     isDarkTheme: Boolean,
     animatedContentScope: AnimatedContentScope? = null,
     sharedTransitionScope: SharedTransitionScope? = null,
     linkedLendBorrow: LendBorrowTransactionItem? = null,
     linkedLoanPersonName: String? = null,
     linkedLoanPersonColor: String? = null,
     linkedLoanPersonAvatar: String? = null,
     onNavigateToPersonDetail: (Long) -> Unit = {}
 ) {
    val context = LocalContext.current
    val receiptContainerColor = if (isAmoledMode) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surface
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .hazeSource(state = hazeState)
            .verticalScroll(
                state = rememberScrollState()
            )
            .padding(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content +
                        paddingValues.calculateTopPadding()
            ),
    ) {
        // Header with amount and merchant
        BlurredAnimatedVisibility(
            visible = isEditMode,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            val categoryEntity = categories.find { it.name == transaction.category }
            val subcategoryEntity = if (categoryEntity != null && transaction.subcategory != null) {
                subcategoriesMap[categoryEntity.id]?.find { it.name == transaction.subcategory }
            } else null

            Column(
                modifier =  Modifier
                    .animateContentSize(
                        MaterialTheme.motionScheme.fastSpatialSpec()
                    )
            ) {
                EditableTransactionHeader(
                    transaction = transaction,
                    viewModel = viewModel,
                    onAmountClick = onAmountClick,
                    categoryEntity = categoryEntity,
                    subcategoryEntity = subcategoryEntity,
                    blurEffects = blurEffects,
                    hazeState = hazeState,
                    accountIconName = accountIconName,
                    linkedLendBorrow = linkedLendBorrow,
                    linkedLoanPersonName = linkedLoanPersonName,
                    linkedLoanPersonColor = linkedLoanPersonColor,
                    linkedLoanPersonAvatar = linkedLoanPersonAvatar
                )
                Spacer(modifier = Modifier.height(Spacing.lg))

                EditableExtractedInfoCard(
                    transaction = transaction,
                    updateExistingTransactions = updateExistingTransactions,
                    existingTransactionCount = existingTransactionCount,
                    onTargetAccountClick = onTargetAccountClick,
                    showBillingCycleMenu = showBillingCycleMenu,
                    onBillingCycleMenuChange = onBillingCycleMenuChange,
                    showCustomCountPad = showCustomCountPad,
                    showCustomUnitMenu = showCustomUnitMenu,
                    showCustomEndDatePicker = showCustomEndDatePicker,
                    viewModel = viewModel,
                    onCategoryClick = onCategoryClick,
                    onAccountClick = onAccountClick
                )

                // Attachments Section in Edit Mode
                Spacer(modifier = Modifier.height(Spacing.md))
                AttachmentSection(
                    attachments = editableAttachments,
                    attachmentService = viewModel.attachmentService,
                    onAddAttachment = onAddAttachment,
                    onRemoveAttachment = onRemoveAttachment,
                    onAttachmentClick = { path ->
                        if (viewModel.attachmentService.isUrl(path)) {
                            val intent = Intent(Intent.ACTION_VIEW, path.toUri())
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Handle error
                            }
                        } else {
                            val uri = viewModel.attachmentService.getAttachmentUri(path)
                            if (uri != null) {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, viewModel.attachmentService.getAttachmentMimeType(path))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Handle error
                                }
                            }
                        }
                    },
                    isEditable = true
                )
                Spacer(modifier = Modifier.height(300.dp)) // For better scroll space
            }

        }
        BlurredAnimatedVisibility(
            visible = !isEditMode,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            val categoryEntity = categories.find { it.name == transaction.category }
            val subcategoryEntity = if (categoryEntity != null && transaction.subcategory != null) {
                subcategoriesMap[categoryEntity.id]?.find { it.name == transaction.subcategory }
            } else null

            Column {
                Box{

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .shadow(
                                2.dp,
                                shape = RoundedCornerShape(24.dp)
                            )
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                color = Color.Black,
                                shape = RoundedCornerShape(24.dp)
                            )
                            .align(Alignment.TopCenter),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                color =  receiptContainerColor.copy(0.5f),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .align(Alignment.TopCenter),
                        contentAlignment = Alignment.Center
                    ){
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .padding(horizontal = 8.dp)
                                .background(
                                    color = Color.Black,
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .border(
                                    2.dp,
                                    color = if(isDarkTheme) MaterialTheme.colorScheme.surface else Color.DarkGray,
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .align(Alignment.Center)
                        )
                    }
                    TransactionReceipt(
                        transaction = transaction,
                        primaryCurrency = accountPrimaryCurrency,
                        convertedAmount = convertedAmount,
                        availableAccounts = availableAccounts,
                        categories = categories,
                        subcategoriesMap = subcategoriesMap,
                        linkedSubscription = linkedSubscription,
                        attachmentService = viewModel.attachmentService,
                        animatedContentScope = animatedContentScope,
                        sharedTransitionScope = sharedTransitionScope,
                        linkedLendBorrow = linkedLendBorrow,
                        linkedLoanPersonName = linkedLoanPersonName,
                        linkedLoanPersonColor = linkedLoanPersonColor,
                        linkedLoanPersonAvatar = linkedLoanPersonAvatar
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .align(Alignment.TopCenter),
                        contentAlignment = Alignment.Center
                    ){
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .padding(horizontal = 10.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black,
                                            Color.Black,
                                            Color.Transparent,
                                        )
                                    ),
                                    shape = RoundedCornerShape(
                                        bottomEnd = 0.dp,
                                        bottomStart = 0.dp,
                                        topStart = 24.dp,
                                        topEnd = 24.dp
                                    )
                                )
                                .align(Alignment.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(300.dp)) // for better scroll
            }
        }
    }
}



@Composable
private fun EditableTransactionHeader(
    transaction: TransactionEntity,
    viewModel: TransactionDetailViewModel,
    onAmountClick: () -> Unit,
    categoryEntity: CategoryEntity? = null,
    subcategoryEntity: SubcategoryEntity? = null,
    blurEffects: Boolean,
    hazeState: HazeState = remember { HazeState()},
    accountIconName: String?,
    linkedLendBorrow: LendBorrowTransactionItem? = null,
    linkedLoanPersonName: String? = null,
    linkedLoanPersonColor: String? = null,
    linkedLoanPersonAvatar: String? = null
) {
    HootbookCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        contentPadding = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Amount Input
            AmountInput(
                amount = transaction.amount.stripTrailingZeros().toPlainString(),
                currencySymbol = CurrencyFormatter.getCurrencySymbol(transaction.currency),
                onClick = onAmountClick,
                modifier = Modifier.fillMaxWidth()
            )

            // Transaction Type
            Column(modifier = Modifier.fillMaxWidth()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TransactionType.entries.filter { it != TransactionType.BALANCE_UPDATE }.forEach { type ->
                        FilterChip(
                            selected = transaction.transactionType == type,
                            onClick = { viewModel.updateTransactionType(type) },
                            label = {
                                Text(
                                    text = stringResource(type.labelRes),
                                    maxLines = 1
                                )
                            },
                            leadingIcon = if (transaction.transactionType == type) {
                                {
                                    Icon(
                                         imageVector = when (type) {
                                             TransactionType.INCOME -> Icons.AutoMirrored.Filled.TrendingUp
                                             TransactionType.EXPENSE -> Icons.AutoMirrored.Filled.TrendingDown
                                             TransactionType.CREDIT -> Iconax.Card
                                             TransactionType.TRANSFER -> Icons.Rounded.SwapHoriz
                                             TransactionType.INVESTMENT -> Icons.AutoMirrored.Filled.ShowChart
                                             TransactionType.BALANCE_UPDATE -> Icons.Rounded.SwapHoriz
                                             TransactionType.LENT -> Icons.AutoMirrored.Filled.TrendingDown
                                             TransactionType.BORROWED -> Icons.AutoMirrored.Filled.TrendingUp
                                         },
                                        contentDescription = null,
                                        modifier = Modifier.size(Dimensions.Icon.small)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(0.7f),
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderWidth = 0.dp,
                                selected = transaction.transactionType == type,
                                enabled = true
                            ),
                        )
                    }
                }
            }

            // Date and Time
            DateTimeField(
                dateTime = transaction.dateTime,
                onDateTimeChange = { viewModel.updateDateTime(it) },
                blurEffects = blurEffects,
                hazeState = hazeState
            )


            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                // Merchant Name
                TextField(
                    value = transaction.merchantName,
                    onValueChange = { viewModel.updateMerchantName(it) },
                    label = { Text(stringResource(R.string.merchant_optional), fontWeight = FontWeight.SemiBold) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 4.dp,
                        bottomEnd = 4.dp
                    ),
                    leadingIcon = {
                        if (linkedLendBorrow != null) {
                            val displayName = linkedLoanPersonName ?: transaction.merchantName
                            val backgroundColor = try {
                                Color(linkedLoanPersonColor?.toColorInt() ?: 0xFF4CAF50.toInt())
                            } catch (_: Exception) {
                                Color(0xFF4CAF50)
                            }
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(backgroundColor),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!linkedLoanPersonAvatar.isNullOrBlank()) {
                                    AsyncImage(
                                        model = linkedLoanPersonAvatar,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = displayName.firstOrNull()?.uppercase()?.toString() ?: "?",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            BrandIcon(
                                merchantName = transaction.merchantName,
                                size = 26.dp,
                                showBackground = false,
                                categoryEntity = categoryEntity,
                                subcategoryEntity = subcategoryEntity,
                                accountIconName = accountIconName
                            )
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        disabledIndicatorColor = Color.Transparent,
                        disabledLabelColor = MaterialTheme.colorScheme.primary,
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                // Description
                TextField(
                    value = transaction.description ?: "",
                    onValueChange = { viewModel.updateDescription(it) },
                    label = { Text(stringResource(R.string.description_optional_label), fontWeight = FontWeight.SemiBold) },
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 4.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    ),
                    leadingIcon = {
                        Icon(
                            Iconax.DocumentText2,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditableExtractedInfoCard(
    transaction: TransactionEntity,
    updateExistingTransactions: Boolean,
    existingTransactionCount: Int,
    onCategoryClick: () -> Unit,
    onAccountClick: () -> Unit,
    onTargetAccountClick: () -> Unit,
    showBillingCycleMenu: Boolean,
    onBillingCycleMenuChange: (Boolean) -> Unit,
    showCustomCountPad: (Boolean) -> Unit,
    showCustomUnitMenu: (Boolean) -> Unit,
    showCustomEndDatePicker: (Boolean) -> Unit,
    viewModel: TransactionDetailViewModel
) {
    val selectedAccount by viewModel.selectedAccount.collectAsStateWithLifecycle()
    val targetAccount by viewModel.targetAccount.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val allSubcategories by viewModel.allSubcategories.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isCustomCycle = uiState.isCustomCycle

    val selectedCategoryObj = remember(transaction.category, categories) {
        categories.find { it.name == transaction.category }
    }
    
    val categoryId = selectedCategoryObj?.id
    val selectedSubcategoryObj = remember(transaction.subcategory, categoryId, allSubcategories) {
        if (categoryId != null) {
            allSubcategories[categoryId]?.find { it.name == transaction.subcategory }
        } else null
    }

    HootbookCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        contentPadding = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Account Selection
            val transactionType = transaction.transactionType
            
            BlurredAnimatedVisibility(
                visible = transactionType == TransactionType.TRANSFER,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                // Transfer Type UI
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            MaterialTheme.motionScheme.fastSpatialSpec()
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(1.5.dp)
                        ) {
                            // Source Account Card
                            AccountField(
                                account = selectedAccount,
                                onClick = onAccountClick,
                                placeholder = stringResource(R.string.select_source_account),
                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                            )

                            // Target Account Card
                            AccountField(
                                account = targetAccount,
                                onClick = onTargetAccountClick,
                                placeholder = stringResource(R.string.select_target_account),
                                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                            )
                        }
                        // Exchange Icon
                        Box(
                            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .shadow(elevation = 3.dp, shape = CircleShape)
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.surface,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SwapVert,
                                    contentDescription = stringResource(R.string.transfer_action),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    TransferCurrencyFields(transaction, selectedAccount, targetAccount, viewModel)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Category Selection
                    CategoryDropdown(
                        selectedCategory = transaction.category,
                        selectedSubcategory = transaction.subcategory,
                        onClick = onCategoryClick,
                        isTransferType = transactionType == TransactionType.TRANSFER,
                        viewModel = viewModel
                    )
                }
            }
            
            BlurredAnimatedVisibility(
                visible = transactionType != TransactionType.TRANSFER,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ){
                // Non-Transfer Type UI
                Column(
                    modifier = Modifier
                        .animateContentSize(
                            MaterialTheme.motionScheme.fastSpatialSpec()
                        )
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {
                    AccountField(
                        account = selectedAccount,
                        onClick = onAccountClick,
                        placeholder = stringResource(R.string.select_account),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                    )
                    SourceCurrencyChoice(transaction, selectedAccount, viewModel)
                    
                    // Category Selection
                    CategoryDropdown(
                        selectedCategory = transaction.category,
                        selectedSubcategory = transaction.subcategory,
                        onClick = onCategoryClick,
                        viewModel = viewModel
                    )
                }
            }

            // ── Merchant category helpers ─────────────────────────────────
            Spacer(modifier = Modifier.height(Spacing.sm))

            // Option 2: Update EXISTING transactions (only shown when there are matches)
            if (existingTransactionCount > 0) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec())
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                onClick = {viewModel.toggleUpdateExistingTransactions()},
                                indication = ripple(),
                                interactionSource = remember { MutableInteractionSource() }
                            )
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HootbookCheckbox(
                            checked = updateExistingTransactions,
                            onCheckedChange = { viewModel.toggleUpdateExistingTransactions() }
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            text = stringResource(R.string.update_existing_transactions_format, existingTransactionCount, if (existingTransactionCount == 1) stringResource(R.string.transaction) else stringResource(R.string.transactions_plural), transaction.merchantName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Preview button — only visible when checkbox is ON
                    AnimatedVisibility(visible = updateExistingTransactions) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = Spacing.sm, end = Spacing.sm, bottom = Spacing.xs),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Surface(
                                onClick = { viewModel.showMatchPreviewSheet() },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.wrapContentSize()
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = Spacing.md,
                                        vertical = Spacing.sm
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.preview_transaction_matches),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            // Recurring Switch and Billing Cycle
            PreferenceSwitch(
                title = stringResource(R.string.recurring_transaction),
                subtitle = stringResource(R.string.mark_as_repeating_payment),
                checked = transaction.isRecurring,
                onCheckedChange = { viewModel.updateRecurringStatus(it) },
                leadingIcon = {
                    Icon(
                        Iconax.VideoTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                isSingle = !transaction.isRecurring,
                isFirst = transaction.isRecurring,
                padding = PaddingValues(horizontal = 0.dp, vertical = 1.5.dp)
            )

            AnimatedVisibility(visible = transaction.isRecurring) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {
                    // Stored as keys, shown translated
                    val billingCycles = io.github.hootkit_dev.hootbook.utils.SubscriptionUtils.CYCLE_KEYS
                    
                    ExposedDropdownMenuBox(
                        expanded = showBillingCycleMenu,
                        onExpandedChange = { onBillingCycleMenuChange(it) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = io.github.hootkit_dev.hootbook.presentation.ui.components.billingCycleLabel(transaction.billingCycle),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.billing_cycle), fontWeight = FontWeight.SemiBold) },
                            leadingIcon = {
                                Icon(
                                    Iconax.VideoTime,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showBillingCycleMenu) },
                            shape = RoundedCornerShape(
                                topStart = 4.dp,
                                topEnd = 4.dp,
                                bottomStart = if (isCustomCycle) 4.dp else 16.dp,
                                bottomEnd = if (isCustomCycle) 4.dp else 16.dp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
                            )
                        )

                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        BlurredAnimatedVisibility(
                            visible = uiState.isCustomCycle,
                            enter = fadeIn() + slideInVertically { -it },
                            exit = fadeOut() + slideOutVertically { -it }
                        ) {
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            CustomBillingCycleCard(
                                count = uiState.customCycleCount,
                                unit = uiState.customCycleUnit,
                                endDate = uiState.customCycleEndDate,
                                onCountClick = { showCustomCountPad(true) },
                                onUnitSelected = { viewModel.updateSubscriptionCustomCycleUnit(it) },
                                onEndDateClick = { showCustomEndDatePicker(true) },
                                onClearEndDate = { viewModel.updateSubscriptionCustomCycleEndDate(null) },
                                shape = RoundedCornerShape(
                                    topStart = 4.dp,
                                    topEnd = 4.dp,
                                    bottomStart = 16.dp,
                                    bottomEnd = 16.dp
                                )
                            )
                        }

                        ExposedDropdownMenu(
                            expanded = showBillingCycleMenu,
                            onDismissRequest = { onBillingCycleMenuChange(false) },
                            shape = MaterialTheme.shapes.large
                        ) {
                            billingCycles.forEachIndexed { index, cycle ->
                                val isFirstItem = index == 0
                                val isLastItem = index == billingCycles.lastIndex
                                val isMiddleItem = !isFirstItem && !isLastItem
                                DropdownMenuItem(
                                    text = { Text(io.github.hootkit_dev.hootbook.presentation.ui.components.billingCycleLabel(cycle)) },
                                    onClick = {
                                        viewModel.updateBillingCycle(cycle)
                                        onBillingCycleMenuChange(false)
                                    }
                                )
                                // Add a Spacer for middle items
                                if (isMiddleItem || (isFirstItem && billingCycles.size > 2) ) {
                                    HorizontalDivider(
                                        thickness = 1.5.dp,
                                        color = MaterialTheme.colorScheme.surface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryDropdown(
    selectedCategory: String,
    selectedSubcategory: String?,
    isTransferType: Boolean = false,
    onClick: () -> Unit,
    viewModel: TransactionDetailViewModel
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val allSubcategories by viewModel.allSubcategories.collectAsStateWithLifecycle()

    val selectedCategoryObj = remember(selectedCategory, categories) {
        categories.find { it.name == selectedCategory }
    }
    val categoryId = selectedCategoryObj?.id
    val selectedSubcategoryObj = remember(selectedSubcategory, categoryId, allSubcategories) {
        if (categoryId != null) {
            allSubcategories[categoryId]?.find { it.name == selectedSubcategory }
        } else null
    }

    val categoryInteractionSource = remember { MutableInteractionSource() }

    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = io.github.hootkit_dev.hootbook.presentation.common.categoryName(selectedCategory),
            onValueChange = {},
            label = { Text(stringResource(R.string.category), fontWeight = FontWeight.SemiBold) },
            readOnly = true,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = categoryInteractionSource,
                    indication = ripple()
                ) {
                    onClick()
                },
            shape = RoundedCornerShape(
                topEnd = if (isTransferType) 16.dp else 4.dp,
                topStart = if (isTransferType) 16.dp else 4.dp,
                bottomEnd = 16.dp,
                bottomStart = 16.dp),
            leadingIcon = {
                val context = LocalContext.current
                val resolvedResId = remember(selectedCategoryObj) {
                    selectedCategoryObj?.let { cat ->
                        if (!cat.iconName.isNullOrEmpty()) {
                            val res = IconResolutionUtils.nameToResId(context, cat.iconName)
                            if (res != 0) res else cat.iconResId
                        } else cat.iconResId
                    } ?: 0
                }

                if (resolvedResId != 0) {
                    Icon(
                        painter = painterResource(id = resolvedResId),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(Iconax.Box2, contentDescription = null)
                }
            },
            trailingIcon = {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
            },
            enabled = false, // Disable typing, handle click above
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                disabledIndicatorColor = Color.Transparent,
                disabledLabelColor = MaterialTheme.colorScheme.primary,
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        // Subcategory Display (Read-only, selected via sheet)
        if (selectedSubcategory != null) {
            Spacer(modifier = Modifier.height(Spacing.md))
            TextField(
                value = io.github.hootkit_dev.hootbook.presentation.common.categoryName(selectedSubcategory),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.subcategory)) },
                leadingIcon = {
                    val context = LocalContext.current
                    val resolvedResId = remember(selectedSubcategoryObj) {
                        selectedSubcategoryObj?.let { sub ->
                            if (!sub.iconName.isNullOrEmpty()) {
                                val res = IconResolutionUtils.nameToResId(context, sub.iconName)
                                if (res != 0) res else sub.iconResId
                            } else sub.iconResId
                        } ?: 0
                    }

                    if (resolvedResId != 0) {
                        Icon(
                            painter = painterResource(id = resolvedResId),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.SubdirectoryArrowRight,
                            contentDescription = null
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
                colors = if (selectedSubcategoryObj != null) {
                    val color = try {
                        Color(selectedSubcategoryObj.color.toColorInt())
                    } catch (_: Exception) {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    }
                    TextFieldDefaults.colors(
                        focusedContainerColor = color.copy(alpha = 0.2f),
                        unfocusedContainerColor = color.copy(alpha = 0.2f),
                        disabledContainerColor = color.copy(alpha = 0.2f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTextColor = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        disabledIndicatorColor = Color.Transparent,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTextColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeField(
    dateTime: LocalDateTime,
    onDateTimeChange: (LocalDateTime) -> Unit,
    blurEffects: Boolean,
    hazeState: HazeState = remember { HazeState()},

) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(Dimensions.Radius.md)
                )
                .padding(4.dp)
                .clickable(
                    onClick = { showDatePicker = true },
                    indication = ripple(),
                    interactionSource = remember { MutableInteractionSource() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val themeColors = MaterialTheme.colorScheme
                Icon(
                    imageVector = Iconax.Calendar,
                    contentDescription = stringResource(R.string.date_picker),
                    tint = themeColors.onSurface
                )
                Spacer(Modifier.size(8.dp))

                val dateLabel =
                    dateTime.format(localizedDateFormatter(withYear = false))
                val yearLabel =
                    dateTime.format(DateTimeFormatter.ofPattern("yyyy"))
                Column(
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = yearLabel,
                        
                        textAlign = TextAlign.Start,
                        color = themeColors.primary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = dateLabel,
                        
                        textAlign = TextAlign.Start,
                        color = themeColors.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                }
            }
        }

        // Time Button
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .clickable { showTimePicker = true },
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                val hour = dateTime.hour
                val minute = dateTime.minute
                Box(modifier = Modifier
                    .padding(5.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(0.2f),
                        shape = RoundedCornerShape(8.dp)
                    )
                ) {
                    Text(
                        text = String.format("%02d", hour),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(5.dp)
                    )
                }

                Text(
                    text = ":",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )

                Box(
                    modifier = Modifier
                        .padding(5.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    Text(
                        text = String.format("%02d", minute),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(5.dp)
                    )
                }
            }
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateTime.toLocalDate().toPickerMillis()
        )
        DatePicker(
            onDismiss = { showDatePicker = false },
            onConfirm = {
                datePickerState.selectedDateMillis?.let { millis ->
                    val newDate = pickerDate(millis)
                    onDateTimeChange(
                        dateTime.withYear(newDate.year)
                            .withMonth(newDate.monthValue)
                            .withDayOfMonth(newDate.dayOfMonth)
                    )
                }
                showDatePicker = false
            },
            datePickerState = datePickerState,
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }

    // Time Picker Dialog
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = dateTime.hour,
            initialMinute = dateTime.minute,
            is24Hour = true
        )
        TimePicker(
            onDismiss = { showTimePicker = false },
            onConfirm = {
                onDateTimeChange(dateTime.withHour(timePickerState.hour)
                    .withMinute(timePickerState.minute))
                showTimePicker = false
            },
            timePickerState = timePickerState,
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }
}



@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun TransactionReceipt(
    transaction: TransactionEntity,
    primaryCurrency: String,
    convertedAmount: BigDecimal?,
    availableAccounts: List<AccountBalanceEntity>,
    categories: List<CategoryEntity>,
    subcategoriesMap: Map<Long, List<SubcategoryEntity>>,
    linkedSubscription: SubscriptionEntity? = null,
    attachmentService: AttachmentService,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    animatedContentScope: AnimatedContentScope? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    showAttachments: Boolean = true,
    linkedLendBorrow: LendBorrowTransactionItem? = null,
    linkedLoanPersonName: String? = null,
    linkedLoanPersonColor: String? = null,
    linkedLoanPersonAvatar: String? = null,
    isCapture: Boolean = false
) {
    // The icon picker for this merchant, opened from the merchant badge
    var showIconSheet by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    var cutoutOffsetPx by remember { mutableFloatStateOf(with(density) { 420.dp.toPx() }) }
    val cutoutRadius = 10.dp
    val cutoutRadiusPx = with(density) { cutoutRadius.toPx() }
    val scallopRadiusPx = with(density) { 8.dp.toPx() }

    val receiptShape = remember(cutoutRadiusPx, cutoutOffsetPx, scallopRadiusPx) {
        ReceiptShape(cutoutRadiusPx, cutoutOffsetPx, scallopRadiusPx)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 36.dp)
            .padding(horizontal = 12.dp)
    ) {
        // Main Receipt Card
        Surface(
            modifier = Modifier
                .shadow(
                    elevation = 4.dp,
                    shape = receiptShape,
                    clip = false
                )
                .animateContentSize(
                    animationSpec = tween(durationMillis = 300)
                )
                .fillMaxWidth(),
            shape = receiptShape,
            color = containerColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp), // Extra top padding for badge clearance
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val categoryEntity = categories.find { it.name == transaction.category }
                val subcategoryEntity = if (categoryEntity != null && transaction.subcategory != null) {
                    subcategoriesMap[categoryEntity.id]?.find { it.name == transaction.subcategory }
                } else null

                //pill shape merchant
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ){
                    DashedLine(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )

                    ReceiptBadge(
                        merchantName = transaction.merchantName,
                        categoryEntity = categoryEntity,
                        subcategoryEntity = subcategoryEntity,
                        category = transaction.category,
                        subcategory = transaction.subcategory,
                        transactionId = transaction.id,
                        animatedContentScope = animatedContentScope,
                        sharedTransitionScope = sharedTransitionScope,
                        linkedLendBorrow = linkedLendBorrow,
                        personName = linkedLoanPersonName,
                        personColor = linkedLoanPersonColor,
                        personAvatar = linkedLoanPersonAvatar,
                        isCapture = isCapture,
                        onIconClick = if (isCapture) null else { { showIconSheet = true } }
                    )
                    if (showIconSheet) {
                        MerchantIconSheet(merchantName = transaction.merchantName, onDismiss = { showIconSheet = false })
                    }
                    DashedLine(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                }


                // Transaction Details Columns
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimensions.Padding.content),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Date Section
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.date),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = transaction.dateTime.format(
                                    localizedDateFormatter()
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Time Section
                        val dateTime = transaction.dateTime
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                val hour = dateTime.hour
                                val minute = dateTime.minute
                                Box(modifier = Modifier
                                    .padding(5.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(0.2f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                ) {
                                    Text(
                                        text = String.format("%02d", hour),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        lineHeight = 16.sp,
                                        modifier = Modifier.padding(5.dp)
                                    )
                                }

                                Text(
                                    text = ":",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge,
                                )

                                Box(
                                    modifier = Modifier
                                        .padding(5.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    Text(
                                        text = String.format("%02d", minute),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyLarge,
                                        lineHeight = 16.sp,
                                        modifier = Modifier.padding(5.dp)
                                    )
                                }
                            }
                        }
                    }

                    ReceiptInfoRow(
                        label = stringResource(R.string.type),
                        value = stringResource(transaction.transactionType.labelRes),
                        linkedLendBorrow = linkedLendBorrow
                    )

                    val subcategoryValue = transaction.subcategory
                    ReceiptInfoRow(
                        label = stringResource(R.string.category),
                        value = transaction.category,
                        subValue = subcategoryValue,
                        icon = {
                            CategoryIcon(
                                category = transaction.category,
                                size = 20.dp,
                                tint = null, // Original colors
                                iconResId = categoryEntity?.iconResId ?: 0,
                                iconName = categoryEntity?.iconName
                            )
                        },
                        subIcon = {
                            if(transaction.subcategory != null) {
                                CategoryIcon(
                                    category = transaction.subcategory,
                                    size = 20.dp,
                                    tint = null, // Original colors
                                    iconResId = subcategoryEntity?.iconResId ?: 0,
                                    iconName = subcategoryEntity?.iconName
                                )
                            }
                        },
                        subcategoryColor = run {
                             if (subcategoryEntity != null) {
                                  try {
                                      Color(subcategoryEntity.color.toColorInt()).copy(alpha = 0.2f)
                                  } catch (_: Exception) {
                                      null
                                  }
                             } else null
                        }
                    )

                    val fromAccount = transaction.fromAccount ?: transaction.accountNumber
                    val toAccount = transaction.toAccount
                    val isTransfer = transaction.transactionType == TransactionType.TRANSFER
                    
                    // Prefer the account whose name also matches: two banks can share the last four digits.
                    val fromAccountEntity = availableAccounts.find { it.accountLast4 == fromAccount && it.bankName == transaction.bankName }
                        ?: availableAccounts.find { it.accountLast4 == fromAccount }
                    val toAccountEntity = toAccount?.let { acc -> availableAccounts.find { it.accountLast4 == acc } }

                    // Name and last four digits, so two cards of one bank can be told apart
                    val fromBankName = if (isTransfer) {
                        withLast4(fromAccountEntity?.bankName ?: transaction.bankName, fromAccount)
                            ?: stringResource(R.string.source)
                    } else {
                        withLast4(transaction.bankName, fromAccount) ?: stringResource(R.string.account)
                    }

                    val toBankName = if (isTransfer && toAccount != null) {
                        withLast4(toAccountEntity?.bankName, toAccount) ?: toAccount
                    } else null

                    ReceiptInfoRow(
                        label = stringResource(R.string.account),
                        // Transfers name both accounts instead of showing their last four digits.
                        value = fromBankName,
                        subValue = toBankName,
                        bankName = fromBankName,
                        subBankName = toBankName,
                        isTransfer = isTransfer,
                        icon = {
                            BrandIcon(
                                merchantName = fromBankName,
                                size = 26.dp,
                                showBackground = false,
                                accountIconResId = fromAccountEntity?.iconResId ?: 0,
                                accountIconName = fromAccountEntity?.iconName,
                                accountColorHex = fromAccountEntity?.color
                            )
                        },
                        subIcon = {
                            if (toBankName != null) {
                                BrandIcon(
                                    merchantName = toBankName,
                                    size = 26.dp,
                                    showBackground = false,
                                    accountIconResId = toAccountEntity?.iconResId ?: 0,
                                    accountIconName = toAccountEntity?.iconName,
                                    accountColorHex = toAccountEntity?.color
                                )
                            }
                        }
                    )

                    transaction.balanceAfter?.let {
                        ReceiptInfoRow(
                            label = stringResource(R.string.balance),
                            value = CurrencyFormatter.formatCurrency(it, primaryCurrency),
                            icon = {
                                Icon(
                                    imageVector = Iconax.Wallet3,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        )
                    }

                    if (transaction.isRecurring && linkedSubscription?.nextPaymentDate != null) {
                        ReceiptInfoRow(
                            label = stringResource(R.string.next_billing),
                            value = linkedSubscription.nextPaymentDate.format(
                                localizedDateFormatter()
                            ),
                            icon = {
                                Icon(
                                    imageVector = Iconax.VideoTime,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        )
                    }
                }

                // Expandable Description
                if (!transaction.description.isNullOrBlank()) {
                    var isDescriptionExpanded by remember { mutableStateOf(false) }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimensions.Padding.content)
                            .padding(top = Spacing.md)
                            .animateContentSize(
                                MaterialTheme.motionScheme.fastSpatialSpec()
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onClick = { isDescriptionExpanded = !isDescriptionExpanded }
                            ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                            ){
                                Icon(
                                    imageVector = Iconax.DocumentText2,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = stringResource(R.string.description),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Icon(
                                imageVector = if (isDescriptionExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        
                        BlurredAnimatedVisibility(
                            visible = isDescriptionExpanded,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = transaction.description,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 16.sp
                                    ),
                                    modifier = Modifier.padding(Spacing.sm)
                                )
                            }
                        }
                    }
                }


                Spacer(modifier = Modifier.height(40.dp)) // Slightly reduced spacer

                // Dashed Line
                DashedLine(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .onGloballyPositioned { coordinates ->
                            cutoutOffsetPx = coordinates.positionInParent().y + (coordinates.size.height / 2f)
                        },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                // Amount
                Text(
                    text = stringResource(R.string.amount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                
                val amountColor = transactionTypeColor(transaction.transactionType)
                val sign = when (transaction.transactionType) {
                    TransactionType.INCOME -> "+"
                    TransactionType.EXPENSE -> "-"
                    TransactionType.CREDIT -> "💳"
                    TransactionType.TRANSFER -> "↔"
                    TransactionType.INVESTMENT -> "📈"
                    TransactionType.BALANCE_UPDATE -> "↔"
                    TransactionType.LENT -> "-"
                    TransactionType.BORROWED -> "+"
                }

                Text(
                    text = "$sign${transaction.formatAmount()}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )

                if (transaction.currency.isNotEmpty() && !transaction.currency.equals(primaryCurrency, ignoreCase = true) && convertedAmount != null) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = "≈ ${CurrencyFormatter.formatCurrency(convertedAmount, primaryCurrency)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.lg))


                if (showAttachments) {
                    // Attachments
                    val attachments = remember(transaction.attachments) {
                        attachmentService.parseAttachments(transaction.attachments)
                    }
                    val context = LocalContext.current

                    if (attachments.isNotEmpty()) {
                        DashedLine(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AttachmentSection(
                                attachments = attachments,
                                attachmentService = attachmentService,
                                onAddAttachment = {},
                                onRemoveAttachment = {},
                                onAttachmentClick = { path ->
                                    if (attachmentService.isUrl(path)) {
                                        val intent = Intent(Intent.ACTION_VIEW, path.toUri())
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            // Handle error
                                        }
                                    } else {
                                        val uri = attachmentService.getAttachmentUri(path)
                                        if (uri != null) {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, attachmentService.getAttachmentMimeType(path))
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                // Handle error
                                            }
                                        }
                                    }
                                },
                                isEditable = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ReceiptBadge(
    merchantName: String,
    categoryEntity: CategoryEntity? = null,
    subcategoryEntity: SubcategoryEntity? = null,
    category: String? = null,
    subcategory: String? = null,
    transactionId: Long = -1L,
    animatedContentScope: AnimatedContentScope? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    linkedLendBorrow: LendBorrowTransactionItem? = null,
    personName: String? = null,
    personColor: String? = null,
    personAvatar: String? = null,
    isCapture: Boolean = false,
    onIconClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 2.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
        modifier = Modifier.wrapContentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            val brandIconModifier = if (
                sharedTransitionScope != null &&
                animatedContentScope != null &&
                transactionId != -1L
            ) {
                with(sharedTransitionScope) {
                    Modifier.sharedElement(
                        rememberSharedContentState(key = "brand_icon_$transactionId"),
                        animatedVisibilityScope = animatedContentScope
                    )
                }
            } else Modifier

            if (linkedLendBorrow != null) {
                val displayName = personName ?: merchantName
                val backgroundColor = remember(personColor) {
                    try {
                        Color(personColor?.toColorInt() ?: 0xFF4CAF50.toInt())
                    } catch (_: Exception) {
                        Color(0xFF4CAF50)
                    }
                }
                Box(
                    modifier = brandIconModifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(backgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (!personAvatar.isNullOrBlank()) {
                        val avatarModel: Any = if (isCapture) {
                            ImageRequest.Builder(LocalPlatformContext.current)
                                .data(personAvatar)
                                .allowHardware(false)
                                .build()
                        } else personAvatar
                        AsyncImage(
                            model = avatarModel,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = displayName.firstOrNull()?.uppercase()?.toString() ?: "?",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                BrandIcon(
                    merchantName = merchantName,
                    size = 34.dp,
                    showBackground = true,
                    categoryEntity = categoryEntity,
                    subcategoryEntity = subcategoryEntity,
                    category = category,
                    subcategory = subcategory,
                    accountIconName = null, // Not an account icon in this context
                    modifier = brandIconModifier.then(
                        if (onIconClick != null) {
                            Modifier
                                .clip(CircleShape)
                                .clickable(onClickLabel = stringResource(R.string.merchant_icon_title), onClick = onIconClick)
                        } else Modifier
                    )
                )
                Text(
                    text = merchantName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ReceiptInfoRow(
    label: String,
    value: String,
    isTransfer: Boolean = false,
    subValue: String? = null,
    bankName: String? = null,
    subBankName: String? = null,
    icon: (@Composable () -> Unit)? = null,
    subIcon: (@Composable () -> Unit)? = null,
    subcategoryColor: Color? = null,
    linkedLendBorrow: LendBorrowTransactionItem? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (label) {
            stringResource(R.string.type), stringResource(R.string.balance), stringResource(R.string.next_billing) -> {
                val displayValue = if (label == stringResource(R.string.type) && linkedLendBorrow != null) {
                    when (linkedLendBorrow.type) {
                        LendBorrowType.LENT -> stringResource(R.string.loan_type_lent)
                        LendBorrowType.BORROWED -> stringResource(R.string.loan_type_borrowed)
                        LendBorrowType.SETTLEMENT_LENT -> stringResource(R.string.settlement_received)
                        LendBorrowType.SETTLEMENT_BORROWED -> stringResource(R.string.settlement_paid)
                    }
                } else value

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    DashedLine(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                    Box(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row {
                            if (icon != null) {
                                icon()
                            }
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(
                                text = displayValue,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            stringResource(R.string.category) -> {
                if (subValue != null) {
                    Text(
                        text = label.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        val categoryColor = CategoryMapping.categories[value]?.color?.copy(0.2f) ?: MaterialTheme.colorScheme.surfaceVariant
                        Box(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .background(
                                    color = categoryColor,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                if (icon != null) {
                                    icon()
                                }
                                Text(
                                    text = io.github.hootkit_dev.hootbook.presentation.common.categoryName(value),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        val resolvedSubcategoryColor = subcategoryColor ?: CategoryMapping.categories[value]?.color?.copy(0.2f) ?: MaterialTheme.colorScheme.surfaceVariant
                        if (subValue != null) {
                            DashedLine(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )

                            Box(
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .background(
                                        color = resolvedSubcategoryColor,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    if (subIcon != null) {
                                        subIcon()
                                    }
                                    Text(
                                        text = io.github.hootkit_dev.hootbook.presentation.common.categoryName(subValue),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                } else{
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        DashedLine(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                        val categoryColor = CategoryMapping.categories[value]?.color?.copy(0.2f) ?: MaterialTheme.colorScheme.surfaceVariant
                        Box(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .background(
                                    color = categoryColor,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                if (icon != null) {
                                    icon()
                                }
                                Text(
                                    text = io.github.hootkit_dev.hootbook.presentation.common.categoryName(value),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            stringResource(R.string.account) -> {
                if (isTransfer) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement =Arrangement.spacedBy(Spacing.sm) ,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val fromColorStr = bankName?.let { BrandIcons.getBrandColor(it) }
                        val fromColor = fromColorStr?.let { Color(it.toColorInt()).copy(0.2f) } ?: MaterialTheme.colorScheme.surfaceVariant
                        val toColorStr = subBankName?.let { BrandIcons.getBrandColor(it) }
                        val toColor = toColorStr?.let { Color(it.toColorInt()).copy(0.2f) } ?: MaterialTheme.colorScheme.surfaceVariant

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ){
                            Text(
                                text = stringResource(R.string.from),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            )
                            Text(
                                text = stringResource(R.string.to_destination),
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.End,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .background(
                                        color = fromColor,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    if (icon != null) {
                                        icon()
                                    }
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            DashedLine(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .background(
                                        color = toColor,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    if (subIcon != null) {
                                        subIcon()
                                    }
                                    if (subValue != null) {
                                        Text(
                                            text = subValue,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else{
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        DashedLine(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                        val bankColorStr = bankName?.let { BrandIcons.getBrandColor(it) }
                        val bankColor = bankColorStr?.let { Color(it.toColorInt()).copy(0.2f) } ?: MaterialTheme.colorScheme.surfaceVariant
                        
                        Box(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .background(
                                    color = bankColor,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                if (icon != null) {
                                    icon()
                                }
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        icon()
                    }
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}


private class ReceiptShape(
    private val cutoutRadius: Float,
    private val cutoutTopOffset: Float,
    private val scallopRadius: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            val scallopDiameter = scallopRadius * 2
            val scallopCount = (size.width / scallopDiameter).toInt().coerceAtLeast(1)
            val actualScallopWidth = size.width / scallopCount

            // Start from bottom-left (after the last scallop)
            moveTo(0f, size.height - scallopRadius)
            
            // Left edge with cutout
            lineTo(0f, cutoutTopOffset + cutoutRadius)
            arcTo(
                rect = Rect(-cutoutRadius, cutoutTopOffset - cutoutRadius, cutoutRadius, cutoutTopOffset + cutoutRadius),
                startAngleDegrees = 90f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false
            )
            lineTo(0f, scallopRadius)
            
            // Top edge with scallops (left to right)
            for (i in 0 until scallopCount) {
                val x = i * actualScallopWidth
                arcTo(
                    rect = Rect(x, 0f, x + actualScallopWidth, actualScallopWidth),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )
            }
            
            // Right edge with cutout
            lineTo(size.width, cutoutTopOffset - cutoutRadius)
            arcTo(
                rect = Rect(size.width - cutoutRadius, cutoutTopOffset - cutoutRadius, size.width + cutoutRadius, cutoutTopOffset + cutoutRadius),
                startAngleDegrees = 270f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false
            )
            lineTo(size.width, size.height - scallopRadius)
            
            // Bottom edge with scallops (right to left)
            for (i in 0 until scallopCount) {
                val x = size.width - (i * actualScallopWidth)
                arcTo(
                    rect = Rect(x - actualScallopWidth, size.height - actualScallopWidth, x, size.height),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )
            }
            
            close()
        }
        return Outline.Generic(path)
    }
}

// Match Preview Sheet
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MatchPreviewSheetContent(
    matchedTransactions: List<TransactionEntity>,
    selectedMatchIds: Set<Long>,
    searchQuery: String,
    searchResults: List<TransactionEntity>,
    onSearchQueryChange: (String) -> Unit,
    onAddSearchResult: (TransactionEntity) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    newCategory: String,
    isDarkTheme: Boolean,
    transactionPersonMapping: Map<Long, PersonInfo> = emptyMap()
) {

    val isDark = isDarkTheme
    Box( modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Sheet header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.matches_preview),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.matches_selected_format, selectedMatchIds.size, matchedTransactions.size, io.github.hootkit_dev.hootbook.presentation.common.categoryName(newCategory)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
                // Select all / deselect all
                val allSelected =
                    selectedMatchIds.size == matchedTransactions.size && matchedTransactions.isNotEmpty()

                IconButton(
                    onClick = {
                        if (allSelected) onDeselectAll() else onSelectAll()
                    },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        imageVector = if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                        contentDescription = if (allSelected) stringResource(R.string.deselect_all) else stringResource(R.string.select_all),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            var searchTextFieldValue by remember(searchQuery) {
                mutableStateOf(
                    TextFieldValue(
                        text = searchQuery,
                        selection = TextRange(searchQuery.length)
                    )
                )
            }
            // Search Bar
            SearchBarBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.xs),
                searchQuery = searchTextFieldValue,
                onSearchQueryChange = {
                    searchTextFieldValue = it
                    onSearchQueryChange(it.text)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Iconax.Search,
                        contentDescription = stringResource(R.string.search),
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                    )
                },
                trailingIcon = {
                    BlurredAnimatedVisibility(searchTextFieldValue.text.isNotEmpty()) {
                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                            searchTextFieldValue = TextFieldValue("")
                            onSearchQueryChange("")
                        }) {
                            Icon(
                                imageVector = Iconax.CloseCircle,
                                contentDescription = stringResource(R.string.clear_search),
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                            )
                        }
                    }
                },
                label = {
                    Text(
                        text = stringResource(R.string.search_transactions_to_add),
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                    )
                }
            )

            // Show Search Results if available
            if (searchQuery.isNotEmpty()) {
                BlurredAnimatedVisibility(searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_additional_transactions_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                BlurredAnimatedVisibility(
                    visible = searchResults.isNotEmpty(),
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.sm)
                            .clip(RoundedCornerShape(Dimensions.Padding.content))
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 460.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        item {
                            Text(
                                text = stringResource(R.string.search_results),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs)
                            )
                        }
                        itemsIndexed(searchResults, key = { _, txn -> "search_${txn.id}" }) { index, txn ->
                            val position = ListItemPosition.from(index, searchResults.size)
                            ListItem(
                                headline = {
                                    Text(
                                        text = txn.displayTitle(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supporting = {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = txn.dateTime.format(localizedDateFormatter()),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (!txn.category.isNullOrBlank()) {
                                            Text(
                                                text = io.github.hootkit_dev.hootbook.presentation.common.categoryName(txn.category),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                },
                                leading = {
                                    val personInfo = transactionPersonMapping[txn.id]
                                    if (personInfo != null) {
                                        val backgroundColor = try {
                                            Color(personInfo.color.toColorInt())
                                        } catch (_: Exception) {
                                            Color(0xFF4CAF50)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(backgroundColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!personInfo.avatar.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = personInfo.avatar,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Text(
                                                    text = personInfo.name.firstOrNull()?.uppercase()?.toString() ?: "?",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    } else {
                                        BrandIcon(
                                            merchantName = txn.merchantName,
                                            category = txn.category,
                                            subcategory = txn.subcategory,
                                            size = 32.dp,
                                            showBackground = true
                                        )
                                    }
                                },
                                trailing = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                                    ) {
                                        Text(
                                            text = CurrencyFormatter.formatCurrency(
                                                txn.amount,
                                                txn.currency
                                            ),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = when (txn.transactionType) {
                                                TransactionType.INCOME -> MaterialTheme.colorScheme.tertiary
                                                TransactionType.BORROWED -> MaterialTheme.colorScheme.tertiary
                                                else -> MaterialTheme.colorScheme.error
                                            }
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = stringResource(R.string.add_transaction_cd),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                onClick = { onAddSearchResult(txn) },
                                shape = position.toShape(),
                                padding = PaddingValues(0.dp)
                            )
                        }
                        item{
                            Spacer(modifier = Modifier.height(150.dp))
                        }
                    }
                }
            } else if (matchedTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_matching_transactions_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.sm)
                        .clip(RoundedCornerShape(Dimensions.Padding.content))
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    itemsIndexed(
                        items = matchedTransactions,
                        key = { _, txn -> txn.id }
                    ) { index, txn ->
                        val isSelected = selectedMatchIds.contains(txn.id)
                        val position = ListItemPosition.from(index, matchedTransactions.size)
                        ListItem(
                            headline = {
                                Text(
                                    text = txn.displayTitle(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            supporting = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = txn.dateTime.format(
                                            localizedDateFormatter()
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                                    )
                                    if (!txn.category.isNullOrBlank()) {
                                        Text(
                                            text = io.github.hootkit_dev.hootbook.presentation.common.categoryName(txn.category),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            },
                            leading = {
                                val personInfo = transactionPersonMapping[txn.id]
                                if (personInfo != null) {
                                    val backgroundColor = try {
                                        Color(personInfo.color.toColorInt())
                                    } catch (_: Exception) {
                                        Color(0xFF4CAF50)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(backgroundColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!personInfo.avatar.isNullOrBlank()) {
                                            AsyncImage(
                                                model = personInfo.avatar,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(
                                                text = personInfo.name.firstOrNull()?.uppercase()?.toString() ?: "?",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else {
                                    BrandIcon(
                                        merchantName = txn.merchantName,
                                        category = txn.category,
                                        subcategory = txn.subcategory,
                                        size = 32.dp,
                                        showBackground = true
                                    )
                                }
                            },
                            trailing = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                                ) {
                                    Text(
                                        text = CurrencyFormatter.formatCurrency(
                                            txn.amount,
                                            txn.currency
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when (txn.transactionType) {
                                            TransactionType.INCOME -> if (!isDark) income_light else income_dark
                                            TransactionType.EXPENSE -> if (!isDark) expense_light else expense_dark
                                            TransactionType.CREDIT -> if (!isDark) credit_light else credit_dark
                                            TransactionType.TRANSFER -> if (!isDark) transfer_light else transfer_dark
                                            TransactionType.INVESTMENT -> if (!isDark) investment_light else investment_dark
                                            TransactionType.BALANCE_UPDATE -> if (!isDark) transfer_light else transfer_dark
                                            TransactionType.LENT -> if (!isDark) expense_light else expense_dark
                                            TransactionType.BORROWED -> if (!isDark) income_light else income_dark
                                        }
                                    )

                                    HootbookCheckbox(
                                        checked = isSelected,
                                        onCheckedChange = { onToggleSelection(txn.id) },
                                    )

                                }
                            },
                            selected = isSelected,
                            onClick = { onToggleSelection(txn.id) },
                            shape = position.toShape(),
                            padding = PaddingValues(0.dp),
                            selectedListColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    }
                    item{
                        Spacer(modifier = Modifier.height(150.dp))
                    }
                }
            }
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
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Save button
            Button(
                onClick = onApply,
                enabled = selectedMatchIds.isNotEmpty(),
                modifier = Modifier
                    .height(52.dp)
                    .fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shapes = ButtonDefaults.shapes()
            ) {
                Text(
                    text = if (selectedMatchIds.isEmpty()) stringResource(R.string.select_transactions_to_update)
                    else stringResource(R.string.apply_to_transactions_format, selectedMatchIds.size, if (selectedMatchIds.size == 1) stringResource(R.string.transaction) else stringResource(R.string.transactions_plural)),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun captureReceiptToBitmap(
    rootView: View,
    context: android.content.Context,
    transaction: TransactionEntity,
    primaryCurrency: String,
    convertedAmount: BigDecimal?,
    categories: List<CategoryEntity>,
    subcategoriesMap: Map<Long, List<SubcategoryEntity>>,
    linkedSubscription: SubscriptionEntity?,
    availableAccounts: List<AccountBalanceEntity>,
    attachmentService: AttachmentService,
    isDarkTheme: Boolean,
    isAmoledMode: Boolean,
    linkedLendBorrow: LendBorrowTransactionItem? = null,
    linkedLoanPersonName: String? = null,
    linkedLoanPersonColor: String? = null,
    linkedLoanPersonAvatar: String? = null
): Bitmap {
    val density = context.resources.displayMetrics.density
    val widthDp = 360f
    val widthPx = (widthDp * density).toInt()

    val composeView = ComposeView(context).apply {
        setContent {
            val colorScheme = if (isDarkTheme) {
                if (isAmoledMode) {
                    darkColorScheme(
                        background = Color.Black,
                        surface = Color.Black
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    dynamicDarkColorScheme(context)
                } else {
                    darkColorScheme()
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicLightColorScheme(context)
            } else {
                lightColorScheme()
            }
            MaterialTheme(colorScheme = colorScheme) {
                TransactionReceipt(
                    transaction = transaction,
                    primaryCurrency = primaryCurrency,
                    convertedAmount = convertedAmount,
                    availableAccounts = availableAccounts,
                    categories = categories,
                    subcategoriesMap = subcategoriesMap,
                    linkedSubscription = linkedSubscription,
                    attachmentService = attachmentService,
                    showAttachments = false,
                    linkedLendBorrow = linkedLendBorrow,
                    linkedLoanPersonName = linkedLoanPersonName,
                    linkedLoanPersonColor = linkedLoanPersonColor,
                    linkedLoanPersonAvatar = linkedLoanPersonAvatar,
                    isCapture = true
                )
            }
        }
    }

    val decorView = rootView.rootView as? ViewGroup
    if (decorView == null) {
        val fallbackBitmap = Bitmap.createBitmap(widthPx, 200, Bitmap.Config.ARGB_8888)
        val fallbackCanvas = Canvas(fallbackBitmap)
        fallbackCanvas.drawColor(android.graphics.Color.WHITE)
        return fallbackBitmap
    }

    composeView.visibility = View.INVISIBLE
    val lp = ViewGroup.MarginLayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
    decorView.addView(composeView, lp)
    try {
        val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        composeView.measure(widthMeasureSpec, heightMeasureSpec)
        val heightPx = composeView.measuredHeight.coerceAtLeast(1)
        composeView.layout(0, 0, widthPx, heightPx)

        // Record, then let the GPU render: images (bank logos) load as hardware bitmaps, which a
        // software Canvas over a plain Bitmap refuses to draw. A Picture accepts them, and
        // createBitmap renders such a picture with the hardware renderer, then copies it out.
        val picture = android.graphics.Picture()
        composeView.draw(picture.beginRecording(widthPx, heightPx))
        picture.endRecording()
        return Bitmap.createBitmap(picture, widthPx, heightPx, Bitmap.Config.ARGB_8888)
    } finally {
        decorView.removeView(composeView)
    }
}

private fun shareReceiptAsPng(
    context: android.content.Context,
    bitmap: Bitmap,
    transaction: TransactionEntity,
    fileName: String
) {
    try {
        val exportDir = java.io.File(context.cacheDir, "exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        val file = java.io.File(exportDir, "$fileName.png")
        java.io.FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_receipt))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_receipt)))
    } catch (e: Exception) {
        Log.e("TransactionDetail", "Error sharing receipt", e)
    }
}

/** "中国银行 · 1234" / "Bank of China · 1234"; just the name for wallets or when digits are unknown. */
internal fun withLast4(bankName: String?, last4: String?): String? {
    val digits = last4?.takeIf { it.length in 3..4 && it.all(Char::isDigit) }
    return when {
        bankName.isNullOrBlank() -> digits?.let { "•• $it" }
        digits == null -> bankName
        else -> "$bankName · $digits"
    }
}

/** Dates as the locale writes them: 2026年10月4日 / 4 Oct 2026 (10月4日 / 4 October without the year). */
@Composable
internal fun localizedDateFormatter(withYear: Boolean = true): DateTimeFormatter {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    return remember(locale, withYear) {
        val zh = locale.language == "zh"
        val pattern = when {
            zh && withYear -> "yyyy年M月d日"
            zh -> "M月d日"
            withYear -> "d MMM yyyy"
            else -> "d MMMM"
        }
        DateTimeFormatter.ofPattern(pattern, locale)
    }
}

/** Which of the account's currencies the transaction is in, when it holds more than one. */
@Composable
private fun SourceCurrencyChoice(
    transaction: TransactionEntity,
    account: AccountBalanceEntity?,
    viewModel: TransactionDetailViewModel
) {
    val holdings = io.github.hootkit_dev.hootbook.data.repository.LocalAccountHoldings.current
    val currencies = remember(account, holdings) { viewModel.currenciesOf(account) }
    if (currencies.size > 1) {
        io.github.hootkit_dev.hootbook.presentation.ui.components.AccountCurrencyChoice(
            currencies = currencies,
            selected = transaction.currency,
            onSelect = viewModel::updateTransactionCurrency,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
        )
    }
}

/**
 * A transfer's currencies: the source's and the target's, when either holds several, and what
 * reached the target when the two differ.
 */
@Composable
private fun TransferCurrencyFields(
    transaction: TransactionEntity,
    source: AccountBalanceEntity?,
    target: AccountBalanceEntity?,
    viewModel: TransactionDetailViewModel
) {
    SourceCurrencyChoice(transaction, source, viewModel)
    val holdings = io.github.hootkit_dev.hootbook.data.repository.LocalAccountHoldings.current
    val targetCurrencies = remember(target, holdings) { viewModel.currenciesOf(target) }
    val toCurrency = transaction.toCurrency ?: target?.currency
    if (targetCurrencies.size > 1) {
        io.github.hootkit_dev.hootbook.presentation.ui.components.AccountCurrencyChoice(
            currencies = targetCurrencies,
            selected = toCurrency,
            onSelect = viewModel::updateTransactionTargetCurrency,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
        )
    }
    if (target != null && toCurrency != null && toCurrency != transaction.currency) {
        val received = transaction.toAmount
        var text by remember { mutableStateOf(received?.toPlainString().orEmpty()) }
        // Follows the rate until the user types; a half-typed "12." still reads as 12
        LaunchedEffect(received) {
            if (received != null && text.toBigDecimalOrNull()?.compareTo(received) != 0) text = received.toPlainString()
        }
        TextField(
            value = text,
            onValueChange = { typed ->
                val filtered = typed.filter { it.isDigit() || it == '.' }
                if (filtered.count { it == '.' } <= 1) {
                    text = filtered
                    viewModel.updateReceivedAmount(filtered)
                }
            },
            label = { Text(stringResource(R.string.transfer_received_amount, toCurrency), fontWeight = FontWeight.SemiBold) },
            supportingText = { Text(stringResource(R.string.transfer_received_amount_hint, transaction.currency, toCurrency)) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
            ),
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
