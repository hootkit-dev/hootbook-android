@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.onboarding

import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hootkit_dev.hootbook.R
import dev.hootkit.core.SyncPhase
import io.github.hootkit_dev.hootbook.presentation.ui.components.ColorPickerContent
import io.github.hootkit_dev.hootbook.presentation.ui.components.CurrencyBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.InstitutionPickerButton
import io.github.hootkit_dev.hootbook.presentation.ui.features.profile.EditProfileState
import io.github.hootkit_dev.hootbook.presentation.ui.features.profile.PresetAvatarSelection
import io.github.hootkit_dev.hootbook.presentation.ui.features.profile.ProfileCardPreview
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.sync.SyncMessagesEffect
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.sync.SyncViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Edit2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Wallet3
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnBoardingScreen(
    modifier: Modifier = Modifier,
    onOnBoardingComplete: () -> Unit,
    onBoardingViewModel: OnBoardingViewModel = hiltViewModel(),
    syncViewModel: SyncViewModel = hiltViewModel()
) {
    val state by onBoardingViewModel.uiState.collectAsStateWithLifecycle()
    val syncState by syncViewModel.state.collectAsStateWithLifecycle()
    val wrongPassphrase by syncViewModel.wrongPassphrase.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    SyncMessagesEffect(syncViewModel, snackbar)
    // Signing in or the first sync is running: leaving the step would hide its outcome
    val syncing = state.step == OnboardingStep.SYNC && syncState.busy
    val syncReady = syncState.status?.phase in setOf(SyncPhase.IDLE, SyncPhase.PAUSED, SyncPhase.SYNCING, SyncPhase.FAILED)
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onBoardingViewModel::importBackup)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Notification permission is optional, including when denied.
        onBoardingViewModel.finishOnboarding()
    }
    val importBackup = { importLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "application/x-zip-compressed")) }
    LaunchedEffect(state.onboardingFinished) { if (state.onboardingFinished) onOnBoardingComplete() }
    LaunchedEffect(state.step, syncReady, syncState.status?.lastSuccess) {
        if (state.step == OnboardingStep.SYNC && syncReady) onBoardingViewModel.refreshSyncSummary()
    }
    BackHandler(enabled = state.isLoading || syncing || state.step != OnboardingStep.WELCOME) {
        if (!syncing) onBoardingViewModel.previousStep()
    }
    OnboardingScaffold(
        state = state, modifier = modifier,
        continueEnabled = when (state.step) {
            OnboardingStep.ACCOUNT -> state.canSaveAccount || state.hasSavedAccount
            OnboardingStep.SYNC -> syncReady
            else -> true
        },
        backEnabled = !syncing,
        snackbarHost = { SnackbarHost(snackbar) },
        onBack = onBoardingViewModel::previousStep,
        onContinue = {
            when (state.step) {
                OnboardingStep.WELCOME -> onBoardingViewModel.nextStep()
                OnboardingStep.ACCOUNT -> onBoardingViewModel.saveManualAccount()
                OnboardingStep.SYNC -> onBoardingViewModel.continueAfterSync()
                OnboardingStep.PROFILE -> onBoardingViewModel.saveProfile()
                OnboardingStep.NOTIFICATIONS -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else onBoardingViewModel.finishOnboarding()
                }
            }
        }
    ) {
        when (state.step) {
            OnboardingStep.WELCOME -> WelcomeStep(
                syncAvailable = syncViewModel.available,
                onSync = onBoardingViewModel::openSync,
                onImportBackup = importBackup
            )
            OnboardingStep.SYNC -> SyncStep(
                syncState = syncState,
                wrongPassphrase = wrongPassphrase,
                syncViewModel = syncViewModel,
                accounts = state.syncedAccounts,
                transactions = state.syncedTransactions,
                selectedCurrency = state.selectedCurrency,
                onSelectCurrency = { onBoardingViewModel.toggleCurrencyBottomSheet(true) }
            )
            OnboardingStep.ACCOUNT -> ManualAccountEntryStep(
                institutions = state.institutions, onSelectInstitution = onBoardingViewModel::selectInstitution,
                accountName = state.manualAccountName, balance = state.manualAccountBalance,
                accountLast4 = state.manualAccountLast4, selectedCurrency = state.selectedCurrency,
                onUpdateName = onBoardingViewModel::updateManualAccountName,
                onUpdateBalance = onBoardingViewModel::updateManualAccountBalance,
                onUpdateLast4 = onBoardingViewModel::updateManualAccountLast4,
                onSelectCurrency = { onBoardingViewModel.toggleCurrencyBottomSheet(true) },
                onImportBackup = importBackup
            )
            OnboardingStep.PROFILE -> ProfileStep(
                state.profileState, onBoardingViewModel::onNameChange,
                onBoardingViewModel::onProfileImageChange, onBoardingViewModel::onBackgroundColorChange
            )
            OnboardingStep.NOTIFICATIONS -> NotificationStep(onSkip = onBoardingViewModel::finishOnboarding)
        }
    }
    if (state.showCurrencyBottomSheet) {
        CurrencyBottomSheet(
            selectedCurrency = state.selectedCurrency,
            onCurrencySelected = { onBoardingViewModel.updateSelectedCurrency(it); onBoardingViewModel.toggleCurrencyBottomSheet(false) },
            onDismiss = { onBoardingViewModel.toggleCurrencyBottomSheet(false) }
        )
    }
    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onBoardingViewModel::clearError,
            title = { Text(stringResource(R.string.onboarding_try_again)) },
            text = { Text(message) },
            confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onBoardingViewModel::clearError) { Text(stringResource(R.string.ok)) } }
        )
    }
    if (state.isLoading) {
        // A modal dialog also blocks back, taps and duplicate imports while I/O runs.
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    LoadingIndicator()
                    Text(stringResource(R.string.onboarding_working))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnboardingScaffold(
    state: OnBoardingUiState,
    modifier: Modifier = Modifier,
    continueEnabled: Boolean = true,
    backEnabled: Boolean = true,
    snackbarHost: @Composable () -> Unit = {},
    onBack: () -> Unit,
    onContinue: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        snackbarHost = snackbarHost,
        topBar = {
            Column(Modifier.statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.onboarding_step_count, state.step.number, OnboardingStep.COUNT), style = MaterialTheme.typography.labelLarge)
                LinearWavyProgressIndicator(progress = { state.step.number.toFloat() / OnboardingStep.COUNT }, modifier = Modifier.fillMaxWidth())
            }
        },
        bottomBar = {
            Row(Modifier.navigationBarsPadding().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.step != OnboardingStep.WELCOME) {
                    FilledTonalIconButton(shapes = IconButtonDefaults.shapes(), onClick = onBack, enabled = !state.isLoading && backEnabled, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.onboarding_back))
                    }
                }
                Button(
                    onClick = onContinue,
                    shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                    enabled = !state.isLoading && continueEnabled,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                ) {
                    Text(stringResource(when (state.step) {
                        OnboardingStep.WELCOME -> R.string.onboarding_start_fresh
                        OnboardingStep.NOTIFICATIONS -> R.string.stay_informed
                        else -> R.string.continue_action
                    }), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                }
            }
        }
    ) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { content() } }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WelcomeStep(syncAvailable: Boolean, onSync: () -> Unit, onImportBackup: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Surface(shape = MaterialTheme.shapes.extraLargeIncreased, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(40.dp).size(72.dp))
        }
        Text(stringResource(R.string.onboarding_welcome_title), style = MaterialTheme.typography.displaySmallEmphasized, textAlign = TextAlign.Center)
        Text(stringResource(R.string.onboarding_welcome_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (syncAvailable) {
            WelcomeChoice(
                icon = Icons.Rounded.CloudSync,
                title = stringResource(R.string.onboarding_sync_title),
                description = stringResource(R.string.onboarding_sync_description),
                onClick = onSync
            )
        }
        WelcomeChoice(
            icon = Icons.Rounded.Restore,
            title = stringResource(R.string.onboarding_import_backup),
            description = stringResource(R.string.onboarding_import_description),
            onClick = onImportBackup
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WelcomeChoice(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null)
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NotificationStep(onSkip: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Icon(Icons.Rounded.Notifications, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.stay_informed), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.onboarding_notifications_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        TextButton(shapes = ButtonDefaults.shapes(), onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.onboarding_not_now)) }
    }
}

@Composable
fun ProfileStep(
    state: EditProfileState,
    onNameChange: (String) -> Unit,
    onProfileImageChange: (Uri?) -> Unit,
    onBackgroundColorChange: (Color) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = Spacing.lg)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Text(
            text = stringResource(R.string.set_up_your_profile),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = Spacing.md)
        )

        ProfileCardPreview(
            profileImageUri = state.editedProfileImageUri,
            backgroundColor = state.editedProfileBackgroundColor,
            bannerImageUri = state.editedBannerImageUri,
            modifier = Modifier.padding(vertical = Spacing.md,horizontal = Spacing.md)
        )

        TextField(
            value = state.editedUserName,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.what_should_we_call_you)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
            shape = RoundedCornerShape(Dimensions.Radius.md),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
            PresetAvatarSelection(
                selectedUri = state.editedProfileImageUri,
                onSelect = onProfileImageChange
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(Dimensions.Radius.md)
                )
        ) {
            ColorPickerContent(
                initialColor = state.editedProfileBackgroundColor.toArgb(),
                onColorChanged = { onBackgroundColorChange(Color(it)) },
                showTitle = true
            )
        }
    }
}

@Composable
fun ManualAccountEntryStep(
    accountName: String,
    balance: String,
    accountLast4: String,
    selectedCurrency: String,
    onUpdateName: (String) -> Unit,
    onUpdateBalance: (String) -> Unit,
    onUpdateLast4: (String) -> Unit,
    onSelectCurrency: () -> Unit,
    onImportBackup: () -> Unit,
    institutions: List<dev.hootkit.core.Institution> = emptyList(),
    onSelectInstitution: (dev.hootkit.core.Institution, String) -> Unit = { _, name -> onUpdateName(name) }
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.AccountBalance,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        Text(
            text = stringResource(R.string.add_your_main_account),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = stringResource(R.string.enter_primary_account_details),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Spacing.xl))

        FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onImportBackup, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Icon(Icons.Rounded.Restore, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.onboarding_import_backup))
        }
        Spacer(Modifier.height(24.dp))
        InstitutionPickerButton(institutions = institutions, onSelected = onSelectInstitution)
        Spacer(modifier = Modifier.height(Spacing.sm))

        TextField(
            value = accountName,
            onValueChange = onUpdateName,
            label = { Text(stringResource(R.string.bank_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimensions.Radius.md),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            leadingIcon = { Icon(Iconax.Edit2, contentDescription = null) }
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            TextField(
                value = balance,
                onValueChange = onUpdateBalance,
                label = { Text(stringResource(R.string.balance)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(Dimensions.Radius.md),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                leadingIcon = { Icon(Iconax.Wallet3, contentDescription = null) },
                singleLine = true
            )

            Card(
                onClick = onSelectCurrency,
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(Dimensions.Radius.md),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxHeight().padding(horizontal = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedCurrency,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        TextField(
            value = accountLast4,
            onValueChange = onUpdateLast4,
            label = { Text(stringResource(R.string.last_4_digits_hint)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.last_4_digits_placeholder)) },
            singleLine = true,
            shape = RoundedCornerShape(Dimensions.Radius.md),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            leadingIcon = { Icon(Icons.Rounded.Pin, contentDescription = null) }
        )
    }
}


@Preview(showBackground = true, name = "Welcome · restore or start fresh")
@Composable
fun OnBoardingWelcomePreview() {
    HootbookTheme(dynamicColor = false) {
        OnboardingScaffold(OnBoardingUiState(), onBack = {}, onContinue = {}) {
            WelcomeStep(syncAvailable = true, onSync = {}, onImportBackup = {})
        }
    }
}
