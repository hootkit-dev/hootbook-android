@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.hootkit_dev.hootbook.presentation.core.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.ui.CoreUiLanguages
import io.github.hootkit_dev.hootbook.presentation.core.currencies.CoreCurrencyPickerSheet
import io.github.hootkit_dev.hootbook.presentation.core.profile.CoreProfileViewModel
import io.github.hootkit_dev.hootbook.presentation.core.profile.ProfileAvatar
import io.github.hootkit_dev.hootbook.presentation.ui.components.ChoiceSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.ErrorBanner
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListSection
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListSectionScope
import io.github.hootkit_dev.hootbook.presentation.ui.components.NoticeCard
import io.github.hootkit_dev.hootbook.presentation.ui.components.ScreenContentPadding
import io.github.hootkit_dev.hootbook.presentation.ui.components.SecondaryScreen
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionSpacing

/**
 * Settings: personal (profile, appearance, language), bookkeeping defaults (main and default
 * account, currencies and rates), the finance pages, data and reminders, and About.
 */
@Composable
fun CoreSettingsScreen(
    onBack: () -> Unit,
    onCurrencies: () -> Unit,
    onBackup: () -> Unit,
    viewModel: CoreSettingsViewModel = hiltViewModel(),
    onRoute: (String) -> Unit = {},
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
    val systemLanguage = stringResource(R.string.core_settings_system_language)
    val language = languages.firstOrNull { it.first == state.localeSelection }?.second ?: systemLanguage
    val noDefault = stringResource(R.string.core_settings_no_default)
    SecondaryScreen(title = stringResource(R.string.settings), onBack = goBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState()).padding(ScreenContentPadding),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
            if (state.navigationBlocked) NoticeCard(stringResource(R.string.core_settings_saving))
            state.localeFailure?.let { failure ->
                NoticeCard(stringResource(when (failure) {
                    CoreLocaleFailure.CORE_COMMAND_FAILED -> R.string.core_locale_command_failed
                    CoreLocaleFailure.UI_ROLLED_BACK -> R.string.core_locale_rolled_back
                    CoreLocaleFailure.DIVERGED -> R.string.core_locale_diverged
                }), error = true) {
                    TextButton(onClick = { viewModel.language(state.localeSelection) }, enabled = !state.saving) { Text(stringResource(R.string.core_locale_retry_sync)) }
                }
            }
            state.error?.let { ErrorBanner(it, viewModel::clearError, actionLabel = stringResource(R.string.core_settings_retry), onAction = viewModel::refresh) }
            ListSection(title = stringResource(R.string.settings_group_personal)) {
                item(leading = { ProfileAvatar(profileViewModel.assets.resolve(profile.avatar), Color(profile.profileBackground), Modifier.size(40.dp)) },
                    headline = { Text(profile.userName.ifBlank { stringResource(R.string.default_user_name) }) },
                    supporting = { Text(stringResource(R.string.profile)) }, chevron = true, onClick = { route("profile") })
                destination(Icons.Rounded.Palette, R.string.appearances, R.string.appearances_subtitle, enabled) { route("appearance") }
                destination(Icons.Rounded.Translate, R.string.language, R.string.language_subtitle, enabled, language) { sheet = "language" }
            }
            ListSection(title = stringResource(R.string.settings_group_bookkeeping)) {
                destination(Icons.Rounded.Payments, R.string.core_settings_main_currency, R.string.settings_main_currency_help, enabled, state.mainCurrency) { sheet = "currency" }
                destination(Icons.Rounded.AccountBalanceWallet, R.string.core_settings_default_account, R.string.settings_default_account_help, enabled,
                    state.accounts.find { it.id == state.defaultAccountId }?.name ?: noDefault) { sheet = "account" }
                destination(Icons.Rounded.CurrencyExchange, R.string.settings_currencies, R.string.settings_currencies_help, enabled) { if (!viewModel.back()) onCurrencies() }
            }
            ListSection(title = stringResource(R.string.settings_group_finances)) {
                destination(Icons.Rounded.AccountBalance, R.string.title_accounts, R.string.accounts_subtitle, enabled) { route("accounts") }
                destination(Icons.Rounded.PieChart, R.string.budgets, R.string.budgets_subtitle, enabled) { route("budgets") }
                destination(Icons.Rounded.EventRepeat, R.string.core_plans_subscriptions, R.string.settings_subscriptions_help, enabled) { route("subscriptions") }
                destination(Icons.Rounded.People, R.string.lend_borrow_title, R.string.loan_description, enabled) { route("debts") }
                destination(Icons.Rounded.Category, R.string.categories, R.string.categories_subtitle, enabled) { route("categories") }
                destination(Icons.Rounded.Bolt, R.string.quick_add_templates, R.string.quick_templates_subtitle, enabled) { route("templates") }
                destination(Icons.Rounded.AutoAwesome, R.string.ai_assistant_title, R.string.ai_assistant_subtitle, enabled) { route("ai") }
                destination(Icons.Rounded.ShowChart, R.string.core_shell_investments, R.string.settings_investments_help, enabled) { route("investments") }
            }
            ListSection(title = stringResource(R.string.settings_group_data)) {
                destination(Icons.Rounded.NotificationsActive, R.string.notifications, R.string.notifications_subtitle, enabled) { route("notifications") }
                destination(Icons.Rounded.CloudSync, R.string.backup_sync_title, R.string.backup_sync_subtitle, enabled) { if (!viewModel.back()) onBackup() }
                destination(Icons.Rounded.Security, R.string.data_privacy_title, R.string.data_privacy_subtitle, enabled) { route("data-privacy") }
            }
            ListSection {
                destination(Icons.Rounded.Info, R.string.about, R.string.about_subtitle, enabled) { route("about") }
            }
        }
    }
    when (sheet) {
        "language" -> ChoiceSheet(stringResource(R.string.language), listOf("" to systemLanguage) + languages, state.localeSelection,
            onDismiss = { if (!state.saving) sheet = null }, onSelect = viewModel::language, enabled = enabled)
        "account" -> ChoiceSheet(stringResource(R.string.core_settings_default_account),
            listOf<Pair<String?, String>>(null to noDefault) + state.accounts.filter { !it.archived }.map { it.id to it.name }, state.defaultAccountId,
            onDismiss = { if (!state.saving) sheet = null }, onSelect = viewModel::defaultAccount, enabled = enabled,
            supporting = { id -> state.accounts.find { it.id == id }?.mainCurrency })
        "currency" -> CoreCurrencyPickerSheet(state.mainCurrency, state.customCurrencies, enabled = enabled, onDismiss = { if (!state.saving) sheet = null }) {
            viewModel.mainCurrency(it); sheet = null
        }
    }
}

/** A row that opens a page or a picker, with the current [value] before the chevron. */
private fun ListSectionScope.destination(icon: ImageVector, title: Int, subtitle: Int, enabled: Boolean, value: String? = null, onClick: () -> Unit) = item(
    leading = { Icon(icon, null) },
    headline = { Text(stringResource(title)) },
    supporting = { Text(stringResource(subtitle)) },
    trailing = value?.let { { Text(it, Modifier.widthIn(max = 120.dp), color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
    chevron = true, onClick = if (enabled) onClick else null,
)
