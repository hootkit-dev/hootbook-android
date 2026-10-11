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
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreColorPickerRow
import io.github.hootkit_dev.hootbook.presentation.core.profile.AvatarPresets
import io.github.hootkit_dev.hootbook.presentation.core.profile.ProfileHeader
import io.github.hootkit_dev.hootbook.presentation.ui.components.ErrorBanner
import io.github.hootkit_dev.hootbook.presentation.ui.components.FormTextField
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListSection
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionTitle
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.InstitutionPicker

/**
 * First run in four steps: welcome (start fresh, turn on sync or restore a backup), the main
 * account and currency, the profile, and notifications.
 */
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
        Column(Modifier.statusBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.onboarding_step_count, state.step.number, 4), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    CoreOnboardingStep.NOTIFICATIONS -> R.string.notif_allow
                    else -> R.string.continue_action
                }), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (state.error || profileState.error != null) ErrorBanner(stringResource(R.string.onboarding_operation_failed), viewModel::refresh,
                Modifier.padding(horizontal = 24.dp), actionLabel = stringResource(R.string.onboarding_try_again), onAction = viewModel::refresh)
            when (state.step) {
                CoreOnboardingStep.WELCOME -> StepColumn {
                    Surface(Modifier.align(Alignment.CenterHorizontally), shape = MaterialTheme.shapes.extraLargeIncreased, color = MaterialTheme.colorScheme.primaryContainer) {
                        Image(painterResource(R.drawable.hootbook), null, Modifier.padding(32.dp).size(72.dp),
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer))
                    }
                    StepHeading(stringResource(R.string.onboarding_welcome_title), stringResource(R.string.onboarding_welcome_body), display = true)
                    ListSection(title = stringResource(R.string.onb_existing_data)) {
                        if (sync.status?.phase != SyncPhase.UNAVAILABLE) item(leading = { Icon(Icons.Rounded.CloudSync, null) },
                            headline = { Text(stringResource(R.string.onboarding_sync_title)) }, supporting = { Text(stringResource(R.string.onboarding_sync_description)) },
                            chevron = true, onClick = if (ready) { { viewModel.step(CoreOnboardingStep.SYNC) } } else null)
                        item(leading = { Icon(Icons.Rounded.Restore, null) }, headline = { Text(stringResource(R.string.onboarding_import_backup)) },
                            supporting = { Text(stringResource(R.string.onb_restore_body)) },
                            chevron = true, onClick = if (ready) { { viewModel.step(CoreOnboardingStep.ACCOUNT); onRoute("backup") } } else null)
                    }
                }
                CoreOnboardingStep.SYNC -> CoreSyncScreen(onBack = viewModel::previous, onBackup = { onRoute("backup") }, onRecord = onRecord, viewModel = syncViewModel, embedded = true)
                CoreOnboardingStep.ACCOUNT -> StepColumn {
                    val fresh = state.accounts.isEmpty()
                    StepHeading(stringResource(if (fresh) R.string.add_your_main_account else R.string.onboarding_sync_currency),
                        stringResource(if (fresh) R.string.enter_primary_account_details else R.string.platform_onboarding_account_ready))
                    if (fresh) {
                        ListSection {
                            item(leading = { Icon(Icons.Rounded.AccountBalance, null) }, headline = { Text(stringResource(R.string.institution_choose)) },
                                supporting = state.institution?.let { { Text(if (chinese) it.chineseName else it.englishName) } },
                                chevron = true, onClick = if (ready) { { showInstitutions = true } } else null)
                        }
                        FormTextField(state.accountName, stringResource(R.string.onb_account_name), viewModel::name, enabled = ready,
                            supporting = stringResource(R.string.bank_name_hint))
                        FormTextField(state.openingBalance, stringResource(R.string.balance), viewModel::balance, enabled = ready, decimal = true)
                    }
                    ListSection {
                        item(leading = { Icon(Icons.Rounded.CurrencyExchange, null) }, headline = { Text(stringResource(R.string.onb_currency)) },
                            supporting = { Text(stringResource(R.string.onboarding_sync_currency_body)) },
                            trailing = { Text(state.currency, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) },
                            chevron = true, onClick = if (ready) { { showCurrencies = true } } else null)
                    }
                    if (fresh) FormTextField(state.last4, stringResource(R.string.last_4_digits_hint), viewModel::last4, enabled = ready,
                        supporting = stringResource(R.string.onb_last4_help))
                    if (fresh) TextButton(onClick = { onRoute("backup") }, enabled = ready, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(stringResource(R.string.onboarding_import_backup))
                    }
                }
                CoreOnboardingStep.PROFILE -> StepColumn {
                    StepHeading(stringResource(R.string.set_up_your_profile), stringResource(R.string.onb_profile_body))
                    ProfileHeader(null, profile.assets.resolve(avatar), Color(background), avatarSize = 80.dp)
                    FormTextField(name, stringResource(R.string.what_should_we_call_you), { name = it }, enabled = ready)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle(stringResource(R.string.profile_avatar), indent = 0.dp, action = {
                            TextButton(onClick = { picture.launch(arrayOf("image/*")) }, enabled = ready) { Text(stringResource(R.string.profile_pick_photo)) }
                        })
                        AvatarPresets(ready) { resource -> profile.presetAvatar(resource) { avatar = it } }
                    }
                    CoreColorPickerRow("#%06X".format(background and 0xFFFFFF), ready, label = stringResource(R.string.profile_avatar_background)) { color ->
                        background = runCatching { android.graphics.Color.parseColor(color) }.getOrDefault(background)
                    }
                }
                CoreOnboardingStep.NOTIFICATIONS -> StepColumn {
                    Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(72.dp).align(Alignment.CenterHorizontally), tint = MaterialTheme.colorScheme.primary)
                    StepHeading(stringResource(R.string.onb_notifications_title), stringResource(R.string.onboarding_notifications_body))
                    TextButton(onClick = viewModel::finish, enabled = ready, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(stringResource(R.string.onboarding_not_now))
                    }
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

/** One step's scrolling content, at most 600 dp wide. */
@Composable
private fun StepColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp), content = content)
    }
}

@Composable
private fun StepHeading(title: String, body: String, display: Boolean = false) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = if (display) MaterialTheme.typography.displaySmallEmphasized else MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
