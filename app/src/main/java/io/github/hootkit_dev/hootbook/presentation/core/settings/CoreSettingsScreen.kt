@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.hootkit_dev.hootbook.presentation.core.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.ui.CoreUiLanguages
import io.github.hootkit_dev.hootbook.presentation.core.profile.CoreProfileViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem as SettingsListItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape

/** The original personal / finances / notifications / app settings groups, backed by Core. */
@Composable
fun CoreSettingsScreen(
    onBack: () -> Unit,
    onCurrencies: () -> Unit,
    onBackup: () -> Unit,
    viewModel: CoreSettingsViewModel = hiltViewModel(),
    onRoute: (String) -> Unit = {},
    coreSchemaVersion: UInt? = null,
    profileViewModel: CoreProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val profile by profileViewModel.preferences.state.collectAsStateWithLifecycle()
    val goBack: () -> Unit = { if (!viewModel.back()) onBack() }
    val route: (String) -> Unit = { if (!viewModel.back()) onRoute(it) }
    BackHandler(onBack = goBack)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration) { viewModel.refresh() }
    val enabled = state.loaded && !state.saving
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val languages = CoreUiLanguages.supported(LocalContext.current)
    val language = languages.firstOrNull { it.first == state.localeSelection }?.second
        ?: stringResource(R.string.core_settings_system_language)
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.settings),
                scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior(),
                scrollBehaviorLarge = scroll,
                hasBackButton = true,
                navigationContent = { NavigationContent(goBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.navigationBlocked) item { Text(stringResource(R.string.core_settings_saving)) }
            state.localeFailure?.let { failure -> item {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(when (failure) {
                            CoreLocaleFailure.CORE_COMMAND_FAILED -> R.string.core_locale_command_failed
                            CoreLocaleFailure.UI_ROLLED_BACK -> R.string.core_locale_rolled_back
                            CoreLocaleFailure.DIVERGED -> R.string.core_locale_diverged
                        }))
                        TextButton(onClick = { viewModel.language(state.localeSelection) }, enabled = !state.saving) {
                            Text(stringResource(R.string.core_locale_retry_sync))
                        }
                    }
                }
            } }
            state.error?.let { error -> item {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp)) {
                        Text(error)
                        Row {
                            TextButton(onClick = viewModel::refresh, enabled = !state.saving) { Text(stringResource(R.string.core_settings_retry)) }
                            TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.core_settings_dismiss)) }
                        }
                    }
                }
            } }
            item {
                SettingsGroup(R.string.settings_group_personal) {
                    SettingsListItem(
                        headline = { Text(profile.userName.ifBlank { stringResource(R.string.default_user_name) }, fontWeight = FontWeight.Medium) },
                        supporting = { Text(stringResource(R.string.profile), style = MaterialTheme.typography.bodyMedium) },
                        leading = {
                            Box(Modifier.size(48.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest).background(Color(profile.profileBackground))) {
                                val avatar = profileViewModel.assets.resolve(profile.avatar)
                                if (avatar != null) AsyncImage(avatar, stringResource(R.string.profile), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Image(painterResource(R.drawable.avatar_1), stringResource(R.string.profile), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                        },
                        trailing = { Icon(Icons.Rounded.ChevronRight, null) },
                        onClick = { route("profile") }, shape = ListItemPosition.Top.toShape(),
                        listColor = MaterialTheme.colorScheme.primaryContainer, padding = PaddingValues(0.dp),
                    )
                    SettingsDestination(R.string.appearances, R.string.appearances_subtitle, Icons.Rounded.Palette, ListItemPosition.Middle, enabled) { route("appearance") }
                    SettingsDestination(R.string.language, R.string.language_subtitle, Icons.Rounded.Translate, ListItemPosition.Middle, enabled, language) { sheet = "language" }
                    SettingsDestination(R.string.currency, R.string.currency_subtitle, Icons.Rounded.CurrencyExchange, ListItemPosition.Middle, enabled, state.mainCurrency) { if (!viewModel.back()) onCurrencies() }
                    SettingsDestination(R.string.core_settings_main_currency, R.string.core_settings_main_currency_note, Icons.Rounded.Payments, ListItemPosition.Middle, enabled, state.mainCurrency) { sheet = "currency" }
                    SettingsDestination(R.string.core_settings_default_account, R.string.core_settings_default_account_note, Icons.Rounded.AccountBalanceWallet, ListItemPosition.Bottom, enabled,
                        state.accounts.find { it.id == state.defaultAccountId }?.name ?: stringResource(R.string.core_settings_no_default)) { sheet = "account" }
                }
            }
            item {
                SettingsGroup(R.string.settings_group_finances) {
                    SettingsDestination(R.string.title_accounts, R.string.accounts_subtitle, Icons.Rounded.AccountBalance, ListItemPosition.Top, enabled) { route("accounts") }
                    SettingsDestination(R.string.budgets, R.string.budgets_subtitle, Icons.Rounded.PieChart, ListItemPosition.Middle, enabled) { route("budgets") }
                    SettingsDestination(R.string.lend_borrow_title, R.string.loan_description, Icons.Rounded.People, ListItemPosition.Middle, enabled) { route("debts") }
                    SettingsDestination(R.string.categories, R.string.categories_subtitle, Icons.Rounded.Category, ListItemPosition.Middle, enabled) { route("categories") }
                    SettingsDestination(R.string.quick_add_templates, R.string.quick_templates_subtitle, Icons.Rounded.Bolt, ListItemPosition.Middle, enabled) { route("templates") }
                    SettingsDestination(R.string.core_plans_subscriptions, null, Icons.Rounded.EventRepeat, ListItemPosition.Middle, enabled) { route("subscriptions") }
                    SettingsDestination(R.string.ai_assistant_title, R.string.ai_assistant_subtitle, Icons.Rounded.AutoAwesome, ListItemPosition.Middle, enabled) { route("ai") }
                    SettingsDestination(R.string.core_shell_investments, null, Icons.Rounded.ShowChart, ListItemPosition.Middle, enabled) { route("investments") }
                    SettingsDestination(R.string.data_privacy_title, R.string.data_privacy_subtitle, Icons.Rounded.Security, ListItemPosition.Middle, enabled) { route("data-privacy") }
                    SettingsDestination(R.string.backup_sync_title, R.string.backup_sync_subtitle, Icons.Rounded.CloudSync, ListItemPosition.Bottom, enabled) { if (!viewModel.back()) onBackup() }
                }
            }
            item {
                SettingsGroup(R.string.settings_group_automation) {
                    SettingsDestination(R.string.notifications, R.string.notifications_subtitle, Icons.Rounded.NotificationsActive, ListItemPosition.Single, enabled) { route("notifications") }
                }
            }
            item {
                SettingsGroup(R.string.settings_group_app) {
                    SettingsDestination(R.string.about, R.string.about_subtitle, Icons.Rounded.Info, ListItemPosition.Single, enabled) { route("about") }
                }
                Text("${io.github.hootkit_dev.hootbook.BuildConfig.VERSION_NAME} · ${coreSchemaVersion ?: "—"}",
                    Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    sheet?.let { current ->
        HootbookModalBottomSheet(onDismissRequest = { if (!state.saving) sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(when (current) { "language" -> R.string.language; "account" -> R.string.core_settings_default_account; else -> R.string.core_settings_main_currency }), style = MaterialTheme.typography.headlineSmall)
                val languageOptions = listOf("" to stringResource(R.string.core_settings_system_language)) + languages
                when (current) {
                    "language" -> LazyColumn(Modifier.heightIn(max = 480.dp)) {
                        items(languageOptions, key = { it.first }) { (tag, label) ->
                            SettingsListItem(headline = { Text(label) }, leading = { RadioButton(state.localeSelection == tag, null) },
                                onClick = if (enabled) ({ viewModel.language(tag); sheet = null }) else null)
                        }
                    }
                    "account" -> LazyColumn(Modifier.heightIn(max = 480.dp)) {
                        item { SettingsListItem(headline = { Text(stringResource(R.string.core_settings_no_default)) }, leading = { RadioButton(state.defaultAccountId == null, null) }, onClick = if (enabled) ({ viewModel.defaultAccount(null); sheet = null }) else null) }
                        items(state.accounts.filter { !it.archived }, key = { it.id }) { account ->
                            SettingsListItem(headline = { Text(account.name) }, supporting = { Text(account.mainCurrency) }, leading = { RadioButton(account.id == state.defaultAccountId, null) },
                                onClick = if (enabled) ({ viewModel.defaultAccount(account.id); sheet = null }) else null)
                        }
                        if (state.accounts.isEmpty()) item { Text(stringResource(R.string.core_settings_no_accounts)) }
                    }
                    else -> {
                        Text(stringResource(R.string.core_settings_main_currency_note))
                        OutlinedTextField(state.currencyDraft, viewModel::currencyChanged, label = { Text(stringResource(R.string.core_settings_currency_code)) }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = viewModel::discardCurrencyDraft, enabled = enabled && state.currencyDirty) { Text(stringResource(R.string.core_settings_reset)) }
                            Button(onClick = viewModel::saveCurrency, enabled = enabled && state.currencyDirty) { Text(stringResource(R.string.core_settings_save)) }
                        }
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsGroup(title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
        Text(stringResource(title), Modifier.semantics { heading() }.padding(start = 16.dp, top = 8.dp, bottom = 10.dp),
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
internal fun SettingsDestination(title: Int, subtitle: Int?, icon: ImageVector, position: ListItemPosition,
    enabled: Boolean = true, value: String? = null, onClick: () -> Unit) {
    SettingsListItem(
        headline = { Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) },
        supporting = subtitle?.let { { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        leading = { Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) } },
        trailing = { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 120.dp)) {
            value?.let { Text(it, Modifier.weight(1f, fill = false), maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        onClick = onClick.takeIf { enabled }, shape = position.toShape(), padding = PaddingValues(0.dp),
    )
}
