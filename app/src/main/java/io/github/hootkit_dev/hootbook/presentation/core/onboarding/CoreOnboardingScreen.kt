@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.hootkit.core.SyncPhase
import dev.hootkit.core.Table
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import io.github.hootkit_dev.hootbook.presentation.core.currencies.CoreCurrencyPickerSheet
import io.github.hootkit_dev.hootbook.presentation.core.profile.CoreProfileViewModel
import io.github.hootkit_dev.hootbook.presentation.core.sync.CoreSyncScreen
import io.github.hootkit_dev.hootbook.presentation.core.sync.CoreSyncViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.components.ColorPickerContent
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.InstitutionPicker

/** Original four-step onboarding, backed only by generated Core records and platform preferences. */
@Composable
fun CoreOnboardingScreen(@Suppress("UNUSED_PARAMETER") session: CoreSession, prefs: CoreUiPreferencesStore,
    onRoute: (String) -> Unit, viewModel: CoreOnboardingViewModel = hiltViewModel(),
    onRecord: (Table, String) -> Unit = { table, id ->
        val encoded = android.net.Uri.encode(id)
        onRoute(when (table) {
            Table.TRANSACTIONS -> "transactions?transactionId=$encoded"
            Table.SUBSCRIPTIONS -> "subscriptions?id=$encoded"
            Table.BUDGETS -> "budgets?id=$encoded"
            Table.PEOPLE -> "debts?id=$encoded"
            Table.QUICK_TEMPLATES -> "templates"
            Table.CATEGORIES, Table.SUBCATEGORIES -> "categories"
            else -> "accounts?id=$encoded"
        })
    }, onFinished: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val syncViewModel: CoreSyncViewModel = hiltViewModel()
    val sync by syncViewModel.state.collectAsStateWithLifecycle()
    val profile: CoreProfileViewModel = hiltViewModel()
    val profileState by profile.state.collectAsStateWithLifecycle()
    val preferences by prefs.state.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf(preferences.userName) }
    var avatar by rememberSaveable { mutableStateOf(preferences.avatar) }
    var background by rememberSaveable { mutableIntStateOf(preferences.profileBackground) }
    var showCurrencies by rememberSaveable { mutableStateOf(false) }
    var showInstitutions by rememberSaveable { mutableStateOf(false) }
    val chinese = LocalConfiguration.current.locales[0].language == "zh"
    val busy = state.busy || profileState.busy || (state.step == CoreOnboardingStep.SYNC && (sync.busy || sync.control?.authChanging == true))
    val ready = !state.loading && !busy
    val notification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.finish() }
    val picture = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { profile.prepareImage(it) { path -> avatar = path } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    BackHandler(state.step != CoreOnboardingStep.WELCOME || busy) { if (!busy) viewModel.previous() }
    val continueEnabled = ready && when (state.step) {
        CoreOnboardingStep.ACCOUNT -> state.currency.isNotBlank() && (state.accounts.isNotEmpty() || state.accountName.isNotBlank())
        CoreOnboardingStep.SYNC -> sync.status?.lastSuccess != null && sync.status?.phase in setOf(SyncPhase.IDLE, SyncPhase.PAUSED, SyncPhase.FAILED)
        else -> true
    }
    Scaffold(modifier = Modifier.fillMaxSize().imePadding(), topBar = {
        Column(Modifier.statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.onboarding_step_count, state.step.number, 4), style = MaterialTheme.typography.labelLarge)
            LinearWavyProgressIndicator(progress = { state.step.number.toFloat() / 4 }, modifier = Modifier.fillMaxWidth())
        }
    }, bottomBar = {
        Row(Modifier.navigationBarsPadding().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.step != CoreOnboardingStep.WELCOME) FilledTonalIconButton(onClick = viewModel::previous, enabled = !busy, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.onboarding_back))
            }
            Button(onClick = {
                when (state.step) {
                    CoreOnboardingStep.WELCOME -> viewModel.step(CoreOnboardingStep.ACCOUNT)
                    CoreOnboardingStep.SYNC -> { viewModel.refresh(); viewModel.step(CoreOnboardingStep.ACCOUNT) }
                    CoreOnboardingStep.ACCOUNT -> viewModel.saveAccount()
                    CoreOnboardingStep.PROFILE -> profile.save(name, background, preferences.showBanner, avatar, preferences.banner) { viewModel.step(CoreOnboardingStep.NOTIFICATIONS) }
                    CoreOnboardingStep.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) notification.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.finish()
                }
            }, enabled = continueEnabled, modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight), contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)) {
                Text(stringResource(when (state.step) {
                    CoreOnboardingStep.WELCOME -> R.string.onboarding_start_fresh
                    CoreOnboardingStep.NOTIFICATIONS -> R.string.stay_informed
                    else -> R.string.continue_action
                }), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (state.error || profileState.error != null) {
                Text(stringResource(R.string.onboarding_operation_failed), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp))
                TextButton(onClick = viewModel::refresh, enabled = !busy) { Text(stringResource(R.string.onboarding_try_again)) }
            }
            when (state.step) {
                CoreOnboardingStep.WELCOME -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Surface(shape = MaterialTheme.shapes.extraLargeIncreased, color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(Icons.Rounded.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(40.dp).size(72.dp))
                    }
                    Text(stringResource(R.string.onboarding_welcome_title), style = MaterialTheme.typography.displaySmallEmphasized, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.onboarding_welcome_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    if (sync.status?.phase != SyncPhase.UNAVAILABLE) OnboardingWelcomeChoice(Icons.Rounded.CloudSync,
                        stringResource(R.string.onboarding_sync_title), stringResource(R.string.onboarding_sync_description), ready) { viewModel.step(CoreOnboardingStep.SYNC) }
                    OnboardingWelcomeChoice(Icons.Rounded.Restore, stringResource(R.string.onboarding_import_backup), stringResource(R.string.platform_onboarding_import_body), ready) {
                        viewModel.step(CoreOnboardingStep.ACCOUNT); onRoute("backup")
                    }
                }
                CoreOnboardingStep.SYNC -> CoreSyncScreen(onBack = viewModel::previous, onBackup = { onRoute("backup") }, onRecord = onRecord, viewModel = syncViewModel, embedded = true)
                CoreOnboardingStep.ACCOUNT -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.AccountBalance, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(if (state.accounts.isEmpty()) R.string.add_your_main_account else R.string.onboarding_sync_currency), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(if (state.accounts.isEmpty()) R.string.enter_primary_account_details else R.string.platform_onboarding_account_ready),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    if (state.accounts.isEmpty()) {
                        FilledTonalButton(onClick = { onRoute("backup") }, enabled = ready, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Icon(Icons.Rounded.Restore, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.onboarding_import_backup))
                        }
                        Spacer(Modifier.height(24.dp))
                        OutlinedButton(onClick = { showInstitutions = true }, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.AccountBalance, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.institution_choose))
                        }
                        Spacer(Modifier.height(8.dp))
                        OnboardingTextField(state.accountName, viewModel::name, stringResource(R.string.bank_name_hint), Icons.Rounded.Edit, ready)
                        Spacer(Modifier.height(16.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (state.accounts.isEmpty()) OnboardingTextField(state.openingBalance, viewModel::balance, stringResource(R.string.balance), Icons.Rounded.AccountBalanceWallet, ready,
                            Modifier.weight(1f), KeyboardType.Decimal)
                        Card(onClick = { showCurrencies = true }, enabled = ready, modifier = if (state.accounts.isEmpty()) Modifier.height(56.dp) else Modifier.fillMaxWidth().height(64.dp),
                            shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Box(Modifier.fillMaxHeight().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                                Text(state.currency, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (state.accounts.isEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        OnboardingTextField(state.last4, viewModel::last4, stringResource(R.string.last_4_digits_hint), Icons.Rounded.Pin, ready, keyboard = KeyboardType.Number)
                    } else Text(stringResource(R.string.onboarding_sync_currency_body), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium)
                }
                CoreOnboardingStep.PROFILE -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(stringResource(R.string.set_up_your_profile), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(190.dp).clip(RoundedCornerShape(24.dp))) {
                        Image(painterResource(R.drawable.banner_bg_image), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))))
                        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).size(100.dp).clip(CircleShape).background(Color(background))) {
                            val image = profile.assets.resolve(avatar)
                            if (image != null) AsyncImage(image, stringResource(R.string.profile), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Image(painterResource(R.drawable.avatar_1), stringResource(R.string.profile), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                    OnboardingTextField(name, { name = it }, stringResource(R.string.what_should_we_call_you), Icons.Rounded.Person, ready, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(CoreProfileViewModel.PRESET_AVATARS) { resource ->
                            Image(painterResource(resource), stringResource(R.string.core_ui_avatar), Modifier.size(80.dp).clip(CircleShape).clickable(enabled = ready) { profile.presetAvatar(resource) { avatar = it } })
                        }
                    }
                    TextButton(onClick = { picture.launch(arrayOf("image/*")) }, enabled = ready) { Text(stringResource(R.string.gallery)) }
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))) {
                        ColorPickerContent(initialColor = background, onColorChanged = { if (ready) background = it }, showTitle = true)
                    }
                }
                CoreOnboardingStep.NOTIFICATIONS -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Icon(Icons.Rounded.Notifications, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.stay_informed), style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.onboarding_notifications_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    TextButton(onClick = viewModel::finish, enabled = ready, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.onboarding_not_now)) }
                }
            }
        }
    }
    if (showCurrencies) CoreCurrencyPickerSheet(state.currency, state.currencies, enabled = ready, onDismiss = { showCurrencies = false }) {
        viewModel.currency(it); showCurrencies = false
    }
    if (showInstitutions) InstitutionPicker(state.institutions, brokersOnly = false, onSelect = {
        viewModel.institution(it, if (chinese) it.chineseName else it.englishName); showInstitutions = false
    }, onDismiss = { showInstitutions = false })
}

@Composable
private fun OnboardingWelcomeChoice(icon: ImageVector, title: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLargeIncreased,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null); Text(title, style = MaterialTheme.typography.titleLarge); Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun OnboardingTextField(value: String, onChange: (String) -> Unit, label: String, icon: ImageVector, enabled: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(), keyboard: KeyboardType = KeyboardType.Text) {
    TextField(value, onChange, modifier, label = { Text(label) }, leadingIcon = { Icon(icon, null) }, enabled = enabled,
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboard), shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
}
