@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.TextButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ripple
import io.github.hootkit_dev.hootbook.presentation.ui.components.maskAccountNumber
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.res.painterResource
import dev.hootkit.core.InstitutionKind
import io.github.hootkit_dev.hootbook.presentation.ui.features.investments.BrokerProviderRegistry
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pin
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import io.github.hootkit_dev.hootbook.R
import dev.hootkit.core.Account
import dev.hootkit.core.AccountKind
import dev.hootkit.core.NewAccount
import dev.hootkit.core.NewCurrency
import dev.hootkit.core.Institution
import io.github.hootkit_dev.hootbook.presentation.effects.BlurredAnimatedVisibility
import io.github.hootkit_dev.hootbook.presentation.ui.components.BrandIcon
import io.github.hootkit_dev.hootbook.presentation.ui.components.ColorPickerContent
import io.github.hootkit_dev.hootbook.presentation.ui.components.CurrencyBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.DeleteAccountDialog
import io.github.hootkit_dev.hootbook.presentation.ui.components.IconSelector
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Bag
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Card
import io.github.hootkit_dev.hootbook.presentation.ui.icons.DollarCircle
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Edit2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Information
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Wallet3
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.utils.IconResolutionUtils
import java.math.BigDecimal
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EditAccountSheet(
    account: Account? = null,
    allAccounts: List<Account> = emptyList(),
    defaultCurrency: String = "CNY",
    initialCategory: AccountCategory? = null,
    isSaving: Boolean = false,
    saveError: Int? = null,
    onClearSaveError: () -> Unit = {},
    showHeading: Boolean = true,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    institutions: List<Institution> = emptyList(),
    brokerAutoSync: (@Composable () -> Unit)? = null,
    onSave: (NewAccount) -> Unit
) {
    val context = LocalContext.current
    var bankName by remember { mutableStateOf(account?.name ?: "") }
    var balance by remember { mutableStateOf(account?.pockets?.firstOrNull { it.currency == account.mainCurrency }?.balance ?: BigDecimal.ZERO) }
    var creditLimit by remember { mutableStateOf(account?.creditLimit ?: BigDecimal.ZERO) }
    var isCreditCard by remember { mutableStateOf((account?.kind == AccountKind.CREDIT_CARD).takeIf { account != null } ?: (initialCategory == AccountCategory.CREDIT_CARDS)) }
    var statementDay by remember(account) { mutableStateOf(account?.statementDay?.toInt()) }
    var dueDay by remember(account) { mutableStateOf(account?.dueDay?.toInt()) }
    // Which day is being picked: true the statement day, false the due day
    var pickingStatementDay by remember { mutableStateOf<Boolean?>(null) }
    var isWallet by remember { mutableStateOf((account?.kind == AccountKind.WALLET).takeIf { account != null } ?: (initialCategory == AccountCategory.WALLETS)) }
    // Core stores the selected account kind directly.
    var isBroker by remember { mutableStateOf(account?.kind == AccountKind.INVESTMENT || (account == null && initialCategory == AccountCategory.INVESTMENTS)) }
    var accountLast4 by remember { mutableStateOf(account?.last4 ?: "") }
    var selectedCurrency by remember { mutableStateOf(account?.mainCurrency ?: defaultCurrency) }
    var iconResId by remember {
        mutableStateOf(
            if (account != null) IconResolutionUtils.nameToResId(context, account.icon).takeIf { it != 0 } ?: R.drawable.type_finance_bank
            else if (isBroker) R.drawable.type_finance_chart_increasing
            else R.drawable.type_finance_bank
        )
    }
    var iconName by remember {
        mutableStateOf(account?.icon ?: IconResolutionUtils.resIdToName(context, iconResId))
    }
    var colorHex by remember { mutableStateOf(account?.color ?: "#33B5E5") }

    var showNumberPad by remember { mutableStateOf(false) }
    var editingCreditLimit by remember { mutableStateOf(false) }
    var showIconSelector by remember { mutableStateOf(false) }
    var showCurrencySheet by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // Currencies the account already holds besides the selected one, with their balances
    val heldBalances = remember(account) { account?.pockets?.associate { it.currency to it.balance }.orEmpty() }
    var institutionKey by remember(account) { mutableStateOf(account?.institutionKey) }
    // Currencies added here become Core pockets on save.
    val addedCurrencies = remember { mutableStateMapOf<String, BigDecimal>() }
    // A currency waiting for the "can't be removed" confirmation; true when it becomes the main one
    var pendingCurrency by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var addingCurrency by remember { mutableStateOf(false) }
    var editingAddedCurrency by remember { mutableStateOf<String?>(null) }


    val chinese = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language == "zh"
    val defaultBroker = remember(institutions) {
        institutions.find { it.key == "ibkr" } ?: institutions.firstOrNull { it.kind == InstitutionKind.BROKER }
    }
    val currentBroker = remember(institutions, institutionKey) {
        institutions.find { it.key == institutionKey && it.kind == InstitutionKind.BROKER } ?: defaultBroker
    }
    var showBrokerPickerSheet by remember { mutableStateOf(false) }

    LaunchedEffect(isBroker) {
        if (isBroker && account == null && (institutionKey == null || institutions.none { it.key == institutionKey && it.kind == InstitutionKind.BROKER })) {
            defaultBroker?.let { broker ->
                institutionKey = broker.key
                bankName = if (chinese) broker.chineseName else broker.englishName
                iconName = broker.iconName
                iconResId = IconResolutionUtils.nameToResId(context, broker.iconName).takeIf { it != 0 } ?: R.drawable.type_finance_chart_increasing
                colorHex = broker.color
            }
        }
    }

    if (showBrokerPickerSheet) {
        InstitutionPickerSheet(
            institutions = institutions,
            brokersOnly = true,
            onDismiss = { showBrokerPickerSheet = false },
            onSelected = { institution, name ->
                institutionKey = institution.key
                bankName = name
                iconName = institution.iconName
                iconResId = IconResolutionUtils.nameToResId(context, institution.iconName).takeIf { it != 0 } ?: R.drawable.type_finance_chart_increasing
                colorHex = institution.color
                showBrokerPickerSheet = false
            }
        )
    }

    LaunchedEffect(bankName, accountLast4) {
        onClearSaveError()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showNumberPad) {
        HootbookModalBottomSheet(
            onDismissRequest = { showNumberPad = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            NumberPad(
                initialValue = editingAddedCurrency?.let { addedCurrencies[it]?.toString() }
                    ?: if (editingCreditLimit) creditLimit.toString() else balance.toString(),
                onDone = {
                    val added = editingAddedCurrency
                    if (added != null) {
                        addedCurrencies[added] = it.toBigDecimalOrNull() ?: BigDecimal.ZERO
                        editingAddedCurrency = null
                    } else if (editingCreditLimit) {
                        creditLimit = it.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    } else {
                        balance = it.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    }
                    showNumberPad = false
                    },
                title = if (editingCreditLimit) stringResource(R.string.enter_credit_limit)
                        else if (account == null) stringResource(R.string.enter_amount)
                        else stringResource(R.string.update_amount)
            )
        }
    }

    if (showIconSelector) {
        HootbookModalBottomSheet(
            onDismissRequest = { showIconSelector = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            IconSelector(
                selectedIconName = iconName,
                onIconSelected = { name ->
                    iconName = name
                    iconResId = IconResolutionUtils.nameToResId(context, name)
                    showIconSelector = false
                }
            )
        }
    }

    if (showCurrencySheet) {
        CurrencyBottomSheet(
            selectedCurrency = selectedCurrency,
            onCurrencySelected = { currency ->
                showCurrencySheet = false
                when {
                    // A new account takes any currency as its main one
                    account == null -> {
                        addedCurrencies.remove(currency)
                        selectedCurrency = currency
                    }
                    // One it holds becomes the main one, showing its own balance
                    currency in heldBalances -> {
                        selectedCurrency = currency
                        balance = heldBalances.getValue(currency)
                    }
                    currency in addedCurrencies -> {
                        selectedCurrency = currency
                        balance = addedCurrencies.getValue(currency)
                    }
                    else -> pendingCurrency = currency to true
                }
            },
            onDismiss = { showCurrencySheet = false }
        )
    }

    if (addingCurrency) {
        CurrencyBottomSheet(
            selectedCurrency = selectedCurrency,
            onCurrencySelected = { currency ->
                addingCurrency = false
                if (currency != selectedCurrency && currency !in heldBalances && currency !in addedCurrencies) {
                    if (account == null) addedCurrencies[currency] = BigDecimal.ZERO
                    else pendingCurrency = currency to false
                }
            },
            onDismiss = { addingCurrency = false }
        )
    }

    pendingCurrency?.let { (currency, asMain) ->
        AlertDialog(
            onDismissRequest = { pendingCurrency = null },
            title = { Text(stringResource(R.string.account_add_currency_title, currency)) },
            text = { Text(stringResource(R.string.account_add_currency_text)) },
            confirmButton = {
                TextButton(onClick = {
                    if (asMain) {
                        // The current main currency stays held; the new one starts at zero
                        selectedCurrency = currency
                        balance = BigDecimal.ZERO
                        addedCurrencies[currency] = BigDecimal.ZERO
                    } else {
                        addedCurrencies[currency] = BigDecimal.ZERO
                    }
                    pendingCurrency = null
                }) { Text(stringResource(R.string.account_add_currency_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCurrency = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }


    if (showDeleteConfirmation) {
        DeleteAccountDialog(
            bankName = bankName,
            accountLast4 = accountLast4,
            accountIcon = iconResId,
            accountColor = colorHex,
            isCreditCard = isCreditCard,
            isWallet = isWallet,
            onDismiss = { showDeleteConfirmation = false },
            onDelete = {
                onDelete?.invoke()
                showDeleteConfirmation = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState(), overscrollEffect = null)
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (showHeading) {
                Text(
                    text = if (account == null) stringResource(R.string.add_account_title) else stringResource(R.string.edit_account_title),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    fontWeight = FontWeight.Bold
                )
            }

            // Account Type Selection
            if (account == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isBankSelected = !isCreditCard && !isWallet && !isBroker
                    // 1. Bank
                    Surface(
                        onClick = {
                            if (isWallet || isBroker) {
                                bankName = ""
                                accountLast4 = ""
                                iconResId = R.drawable.type_finance_bank
                                iconName = IconResolutionUtils.resIdToName(context, iconResId)
                                colorHex = "#33B5E5"
                            }
                            isCreditCard = false
                            isWallet = false
                            isBroker = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isBankSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = if (isBankSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .height(40.dp)
                            .semantics {
                                role = Role.RadioButton
                                selected = isBankSelected
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.AccountBalance, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            BasicText(
                                text = stringResource(R.string.type_bank),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isBankSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isBankSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                ),
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(
                                    minFontSize = 9.sp,
                                    maxFontSize = MaterialTheme.typography.labelMedium.fontSize,
                                    stepSize = 0.5.sp
                                )
                            )
                        }
                    }

                    // 2. Card
                    Surface(
                        onClick = {
                            isCreditCard = true
                            isWallet = false
                            isBroker = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCreditCard) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = if (isCreditCard) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .height(40.dp)
                            .semantics {
                                role = Role.RadioButton
                                selected = isCreditCard
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Iconax.Card, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            BasicText(
                                text = stringResource(R.string.type_card),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isCreditCard) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCreditCard) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                ),
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(
                                    minFontSize = 9.sp,
                                    maxFontSize = MaterialTheme.typography.labelMedium.fontSize,
                                    stepSize = 0.5.sp
                                )
                            )
                        }
                    }

                    // 3. Wallet
                    Surface(
                        onClick = {
                            isWallet = true
                            isCreditCard = false
                            isBroker = false
                            accountLast4 = "wallet"
                            bankName = "Cash"
                            iconName = "type_finance_dollar_banknote"
                            iconResId = R.drawable.type_finance_dollar_banknote
                            colorHex = "#8BC34A"
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isWallet) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = if (isWallet) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .height(40.dp)
                            .semantics {
                                role = Role.RadioButton
                                selected = isWallet
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Iconax.Wallet3, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            BasicText(
                                text = stringResource(R.string.type_wallet),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isWallet) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isWallet) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                ),
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(
                                    minFontSize = 9.sp,
                                    maxFontSize = MaterialTheme.typography.labelMedium.fontSize,
                                    stepSize = 0.5.sp
                                )
                            )
                        }
                    }

                    // 4. Broker
                    Surface(
                        onClick = {
                            if (isWallet || !isBroker) {
                                val broker = currentBroker ?: defaultBroker
                                if (broker != null) {
                                    institutionKey = broker.key
                                    bankName = if (chinese) broker.chineseName else broker.englishName
                                    iconName = broker.iconName
                                    iconResId = IconResolutionUtils.nameToResId(context, broker.iconName).takeIf { it != 0 } ?: R.drawable.type_finance_chart_increasing
                                    colorHex = broker.color
                                } else {
                                    bankName = ""
                                    accountLast4 = ""
                                    iconResId = R.drawable.type_finance_chart_increasing
                                    iconName = IconResolutionUtils.resIdToName(context, iconResId)
                                    colorHex = "#33B5E5"
                                }
                            }
                            isBroker = true
                            isCreditCard = false
                            isWallet = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isBroker) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = if (isBroker) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .height(40.dp)
                            .semantics {
                                role = Role.RadioButton
                                selected = isBroker
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ShowChart, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            BasicText(
                                text = stringResource(R.string.type_broker),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isBroker) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isBroker) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                ),
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(
                                    minFontSize = 9.sp,
                                    maxFontSize = MaterialTheme.typography.labelMedium.fontSize,
                                    stepSize = 0.5.sp
                                )
                            )
                        }
                    }
                }
            }

            // Broker: pick broker first, show capabilities (auto-sync or note), then details
            AnimatedVisibility(
                visible = isBroker && account == null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Section 1: Choose Broker (置顶第一步：选择券商)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.institution_choose_broker),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics { heading() }
                        )

                        Surface(
                            onClick = { showBrokerPickerSheet = true },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                val brokerLogoRes = currentBroker?.iconName?.let {
                                    IconResolutionUtils.nameToResId(context, it).takeIf { id -> id != 0 }
                                } ?: R.drawable.type_finance_chart_increasing

                                Image(
                                    painter = painterResource(brokerLogoRes),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White)
                                        .padding(4.dp)
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = currentBroker?.let { if (chinese) it.chineseName else it.englishName }
                                            ?: stringResource(R.string.broker_name_label),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = currentBroker?.let { "${it.region} · ${it.currency}" }
                                            ?: stringResource(R.string.connect_broker_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                TextButton(
                                    onClick = { showBrokerPickerSheet = true },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(
                                        text = stringResource(R.string.change_broker),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Section 2: Capability for selected broker (第二步：根据所选券商展示能力)
                    val supportsAutoSync = currentBroker?.key == "ibkr" ||
                        BrokerProviderRegistry.findByInstitution(currentBroker?.key) != null

                    if (supportsAutoSync && brokerAutoSync != null) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            brokerAutoSync.invoke()

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AccountBalance,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.manual_recording_available_below),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else if (currentBroker != null && !supportsAutoSync) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ShowChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.broker_auto_sync_not_supported),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Section 3: Account Details & Balance Header (第三步：填写/确认账户参数)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Text(
                            stringResource(R.string.broker_manual_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            stringResource(R.string.broker_manual_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Preview Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                PreviewAccountCard(
                    bankName = bankName.ifEmpty { stringResource(R.string.preview_bank_name) },
                    balance = balance,
                    accountLast4 = accountLast4.ifEmpty { "0000" },
                    iconResId = iconResId,
                    iconName = iconName,
                    colorHex = colorHex,
                    currency = selectedCurrency,
                    isCreditCard = isCreditCard,
                    isWallet = isWallet,
                    creditLimit = creditLimit
                )
                Text(
                    text = stringResource(R.string.preview_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // Input Fields
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icon Button
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .clickable { showIconSelector = true },
                        contentAlignment = Alignment.Center
                    ) {
                        BrandIcon(
                            merchantName = bankName,
                            size = 58.dp,
                            accountIconResId = iconResId,
                            accountIconName = iconName,
                            accountColorHex = colorHex
                        )
                        // Edit badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(16.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.surface,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Iconax.Edit2,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    // Balance/Outstanding Input
                    if (account == null) Surface(
                        onClick = {
                            editingCreditLimit = false
                            showNumberPad = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Leading Icon
                            Icon(
                                imageVector = Iconax.Wallet3,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            // Label and Value
                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = if (isCreditCard) stringResource(R.string.outstanding_label) else stringResource(R.string.balance_label),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = CoreMoneyPresentation.formatCurrency(
                                        balance,
                                        selectedCurrency
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                if (isCreditCard) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Credit Limit Input
                    Surface(
                        onClick = {
                            editingCreditLimit = true
                            showNumberPad = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Iconax.Card,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = stringResource(R.string.credit_limit_label),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = CoreMoneyPresentation.formatCurrency(
                                        creditLimit,
                                        selectedCurrency
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Statement closing and payment due days
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        CardDayField(
                            label = stringResource(R.string.card_statement_day),
                            day = statementDay,
                            onClick = { pickingStatementDay = true },
                            modifier = Modifier.weight(1f)
                        )
                        CardDayField(
                            label = stringResource(R.string.card_due_day),
                            day = dueDay,
                            onClick = { pickingStatementDay = false },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    pickingStatementDay?.let { statement ->
                        DayOfMonthDialog(
                            title = stringResource(if (statement) R.string.card_statement_day else R.string.card_due_day),
                            selected = if (statement) statementDay else dueDay,
                            onSelect = { day ->
                                if (statement) statementDay = day else dueDay = day
                                pickingStatementDay = null
                            },
                            onDismiss = { pickingStatementDay = null }
                        )
                    }

                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!isWallet && !isBroker) {
                    InstitutionPickerButton(institutions = institutions, brokersOnly = false) { institution, name ->
                        bankName = name
                        iconName = institution.iconName
                        iconResId = IconResolutionUtils.nameToResId(context, institution.iconName)
                        institutionKey = institution.key
                        colorHex = institution.color
                    }
                }

                // Bank Name Row
                TextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = {
                        Text(
                            stringResource(
                                when {
                                    isWallet -> R.string.wallet_name_label
                                    isBroker -> R.string.broker_name_label
                                    else -> R.string.bank_name_label
                                }
                            ),
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 4.dp,
                        bottomEnd = 4.dp
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            0.7f
                        )
                    ),
                    leadingIcon = { Icon(Iconax.Edit2, contentDescription = null)
                    }
                )

                if (!isWallet) {
                    TextField(
                        value = accountLast4,
                        onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() })
                            accountLast4 = it },
                        label = { Text(stringResource(R.string.account_number_last_4_label), fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.placeholder_last_4_digits)) },
                        singleLine = true,
                        shape = RoundedCornerShape(4.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                0.7f
                            )
                        ),
                        leadingIcon = { Icon(Icons.Rounded.Pin, contentDescription = null) }
                    )
                }

                // Currency Selection
                val currencyInteractionSource = remember { MutableInteractionSource() }
                TextField(
                    value = "$selectedCurrency (${CoreMoneyPresentation.getCurrencySymbol(selectedCurrency)})",
                    onValueChange = {},
                    label = { Text(stringResource(R.string.currency_label), fontWeight = FontWeight.SemiBold) },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = currencyInteractionSource,
                            indication = ripple()
                        ) {
                            showCurrencySheet = true
                        },
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 4.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    ),
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
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Iconax.DollarCircle,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.select_currency))
                    },
                    enabled = false
                )

                // The account's other currencies: held ones with their balances, added ones editable
                val others = heldBalances.filterKeys { it != selectedCurrency }
                Text(
                    text = stringResource(R.string.account_other_currencies),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.xs, top = Spacing.sm)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    others.forEach { (currency, amount) ->
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(CoreMoneyPresentation.formatCurrency(amount, currency)) }
                        )
                    }
                    addedCurrencies.forEach { (currency, amount) ->
                        InputChip(
                            selected = true,
                            onClick = {
                                editingAddedCurrency = currency
                                editingCreditLimit = false
                                showNumberPad = true
                            },
                            label = { Text(CoreMoneyPresentation.formatCurrency(amount, currency)) }
                        )
                    }
                    AssistChip(
                        onClick = { addingCurrency = true },
                        label = { Text(stringResource(R.string.account_add_currency)) },
                        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Color Picker Section
                ColorPickerContent(
                    initialColor = colorHex.toColorInt(),
                    onColorChanged = { colorInt ->
                        colorHex = String.format("#%06X", 0xFFFFFF and colorInt)
                    }
                )
            }

        }

        // Action Buttons at Bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val error = when {
                    else -> saveError?.let { stringResource(it) }
                }
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Delete button (only for existing accounts)
                    if (onDelete != null && account != null) {
                        OutlinedButton(
                            onClick = { showDeleteConfirmation = true },
                            modifier = Modifier.height(56.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.error,
                                        MaterialTheme.colorScheme.error
                                    )
                                )
                            ),
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Icon(
                                imageVector = Iconax.Bag,
                                contentDescription = stringResource(R.string.delete_account_desc)
                            )
                        }
                    }

                    // Save button
                    Button(
                        onClick = {
                            onSave(NewAccount(
                                name = bankName.trim(), last4 = accountLast4.takeIf { !isWallet && it.isNotBlank() },
                                kind = when { isCreditCard -> AccountKind.CREDIT_CARD; isWallet -> AccountKind.WALLET; isBroker -> AccountKind.INVESTMENT; else -> AccountKind.BANK },
                                mainCurrency = selectedCurrency, openingBalance = if (account == null) balance else null,
                                openingAt = null, creditLimit = creditLimit.takeIf { isCreditCard },
                                statementDay = statementDay?.toUByte().takeIf { isCreditCard },
                                dueDay = dueDay?.toUByte().takeIf { isCreditCard }, institutionKey = institutionKey,
                                icon = iconName, color = colorHex,
                                extraCurrencies = addedCurrencies.map { (code, amount) -> NewCurrency(code, amount) },
                            ))
                        },
                        enabled = !isSaving && bankName.isNotBlank() && (isWallet || accountLast4.length == 4),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Text(
                            text = if (isSaving) stringResource(R.string.saving_account) else if (account == null) stringResource(R.string.add_account_title) else stringResource(R.string.save_changes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PreviewAccountCard(
    bankName: String,
    balance: BigDecimal,
    accountLast4: String,
    iconResId: Int,
    iconName: String,
    colorHex: String,
    currency: String,
    isCreditCard: Boolean = false,
    isWallet: Boolean = false,
    creditLimit: BigDecimal = BigDecimal.ZERO
) {
    Card(
        modifier = Modifier.padding(bottom = Spacing.sm).fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Balance/Outstanding Section
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
                Text(
                    text = if (isCreditCard) stringResource(R.string.outstanding_label) else stringResource(R.string.balance_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = CoreMoneyPresentation.formatCurrency(balance, currency),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            BlurredAnimatedVisibility(
                visible = isCreditCard,
                enter = fadeIn() + slideInVertically(MaterialTheme.motionScheme.fastEffectsSpec()),
                exit = fadeOut() + slideOutVertically(MaterialTheme.motionScheme.fastEffectsSpec())
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.credit_limit_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CoreMoneyPresentation.formatCurrency(creditLimit, currency),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Bottom Bank Info Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = bankName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isWallet) stringResource(R.string.type_wallet) else maskAccountNumber(accountLast4).orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)
                        )
                    }

                    BrandIcon(
                        merchantName = bankName,
                        size = 48.dp,
                        showBackground = true,
                        accountIconResId = iconResId,
                        accountIconName = iconName,
                        accountColorHex = colorHex
                    )
                }
            }
        }
    }
}

/** A card day as a field: "每月 5 日", or "未设置". */
@Composable
private fun CardDayField(label: String, day: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(
                if (day == null) stringResource(R.string.card_day_unset) else stringResource(R.string.card_day_value, day),
                style = MaterialTheme.typography.bodyLarge,
                color = if (day == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Picks a day of the month (1–31) or none; a short month uses its last day. */
@Composable
private fun DayOfMonthDialog(title: String, selected: Int?, onSelect: (Int?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..31).chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { day ->
                            val chosen = day == selected
                            Surface(
                                onClick = { onSelect(day) },
                                shape = CircleShape,
                                color = if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) { Text(day.toString(), style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.card_day_short_month),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        dismissButton = { TextButton(onClick = { onSelect(null) }) { Text(stringResource(R.string.card_day_clear)) } }
    )
}
