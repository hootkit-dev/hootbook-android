@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.currencies

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.nestedscroll.nestedScroll
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookSwitch
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R

/** Original two-card settings layout; preferences only select presentation, Core performs every conversion. */
@Composable
fun CoreCurrenciesScreen(onBack: () -> Unit, initialCurrency: String? = null,
    viewModel: CoreCurrenciesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appearance: io.github.hootkit_dev.hootbook.presentation.core.appearance.CoreAppearanceViewModel = hiltViewModel()
    val prefs by appearance.preferences.state.collectAsStateWithLifecycle()
    val prefsReady by appearance.preferences.loaded.collectAsStateWithLifecycle()
    val prefsBusy by appearance.busy.collectAsStateWithLifecycle()
    val prefsError by appearance.error.collectAsStateWithLifecycle()
    var showRates by rememberSaveable { mutableStateOf(initialCurrency != null) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    val enabled = prefsReady && !prefsBusy
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haze = remember { HazeState() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(initialCurrency) { viewModel.initialCurrency(initialCurrency) }
    Scaffold(modifier = Modifier.nestedScroll(scroll.nestedScrollConnection), topBar = {
        CustomTitleTopAppBar(title = stringResource(R.string.currency_settings_title),
            scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior(), scrollBehaviorLarge = scroll, hazeState = haze,
            hasBackButton = true, navigationContent = { NavigationContent(onBack) })
    }) { padding ->
        Column(Modifier.fillMaxSize().hazeSource(haze).verticalScroll(rememberScrollState()).padding(padding).consumeWindowInsets(padding).padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            prefsError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CurrencySettingsGridCard(stringResource(R.string.currency), prefs.unifiedDisplayCurrency ?: state.mainCurrency,
                    Icons.Rounded.AttachMoney, { picker = "display" }, Modifier.weight(1f), enabled)
                CurrencySettingsGridCard(stringResource(R.string.exchange_rates), stringResource(R.string.rates_info_subtitle),
                    Icons.AutoMirrored.Filled.ShowChart, { showRates = true }, Modifier.weight(1f), state.loaded)
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                CurrencyPreference(stringResource(R.string.unified_currency), stringResource(R.string.unified_currency_subtitle), prefs.unifiedDisplayEnabled,
                    enabled, ListItemPosition.Top) { value ->
                    appearance.update { it.copy(unifiedDisplayEnabled = value, unifiedDisplayCurrency = it.unifiedDisplayCurrency ?: state.mainCurrency.takeIf(String::isNotBlank)) }
                }
                AnimatedVisibility(prefs.unifiedDisplayEnabled) {
                    CurrencyChoiceRow(stringResource(R.string.display_currency), prefs.unifiedDisplayCurrency ?: state.mainCurrency,
                        ListItemPosition.Middle, enabled) { picker = "display" }
                }
                CurrencyPreference(stringResource(R.string.default_currency), stringResource(R.string.default_currency_subtitle), prefs.defaultEntryEnabled,
                    enabled, if (prefs.defaultEntryEnabled) ListItemPosition.Middle else ListItemPosition.Bottom) { value ->
                    appearance.update { it.copy(defaultEntryEnabled = value, defaultEntryCurrency = it.defaultEntryCurrency ?: state.mainCurrency.takeIf(String::isNotBlank)) }
                }
                AnimatedVisibility(prefs.defaultEntryEnabled) {
                    CurrencyChoiceRow(stringResource(R.string.default_label), prefs.defaultEntryCurrency ?: state.mainCurrency,
                        ListItemPosition.Bottom, enabled) { picker = "entry" }
                }
            }
            Text(stringResource(R.string.core_currencies_main_currency, state.mainCurrency), Modifier.padding(horizontal = 32.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showRates) CoreCurrencyRatesSheet(onBack = { showRates = false }, initialCurrency = initialCurrency, viewModel = viewModel)
    picker?.let { target ->
        CoreCurrencyPickerSheet(selectedCurrency = if (target == "entry") prefs.defaultEntryCurrency ?: state.mainCurrency else prefs.unifiedDisplayCurrency ?: state.mainCurrency,
            currencies = state.customCurrencies, enabled = enabled, onDismiss = { picker = null }, onSelect = { code ->
                appearance.update { if (target == "entry") it.copy(defaultEntryCurrency = code) else it.copy(unifiedDisplayCurrency = code) }
                picker = null
            })
    }
}

@Composable
private fun CurrencySettingsGridCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) {
    Card(onClick = onClick, enabled = enabled, modifier = modifier.height(130.dp), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CurrencyPreference(title: String, subtitle: String, checked: Boolean, enabled: Boolean, position: ListItemPosition, onChange: (Boolean) -> Unit) {
    ListItem(headline = { Text(title) }, supporting = { Text(subtitle) },
        trailing = { HootbookSwitch(checked, null, enabled = enabled) }, toggled = checked, onToggle = if (enabled) onChange else null,
        shape = position.toShape(), padding = PaddingValues(horizontal = 16.dp))
}

@Composable
private fun CurrencyChoiceRow(title: String, currency: String, position: ListItemPosition, enabled: Boolean, onClick: () -> Unit) {
    ListItem(headline = { Text(title, style = MaterialTheme.typography.bodyMedium) },
        trailing = { Row(verticalAlignment = Alignment.CenterVertically) {
            Text(currency, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            Icon(Icons.Rounded.ChevronRight, null)
        } }, onClick = if (enabled) onClick else null, shape = position.toShape(), padding = PaddingValues(horizontal = 16.dp))
}

/** Currency names and symbols are display metadata; supported custom records are supplied by Core. */
@Composable
internal fun CoreCurrencyPickerSheet(selectedCurrency: String, currencies: List<CustomCurrency> = emptyList(), enabled: Boolean = true,
    onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val popular = listOf("CNY", "HKD", "USD", "SGD", "EUR", "GBP", "JPY", "TWD", "AUD", "CAD", "CHF", "KRW", "NZD", "MYR", "THB")
    val locale = LocalConfiguration.current.locales[0]
    val codes = remember(currencies, locale) {
        (popular + java.util.Currency.getAvailableCurrencies().map { it.currencyCode }.sorted() + currencies.map { it.code }).distinct()
    }
    HootbookModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.currencies), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
            OutlinedTextField(search, { search = it }, singleLine = true, label = { Text(stringResource(R.string.search_currencies)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
            val filtered = codes.filter { code ->
                val name = currencies.firstOrNull { it.code == code }?.name ?: runCatching { java.util.Currency.getInstance(code).getDisplayName(locale) }.getOrDefault(code)
                (search.isBlank() && (showAll || code in popular || currencies.any { it.code == code })) ||
                    (search.isNotBlank() && (code.contains(search, true) || name.contains(search, true)))
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (filtered.isEmpty()) item { Text(stringResource(R.string.currency_not_available)) }
                items(filtered.chunked(3)) { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { code ->
                        val custom = currencies.firstOrNull { it.code == code }
                        val system = remember(code) { runCatching { java.util.Currency.getInstance(code) }.getOrNull() }
                        Card(onClick = { onSelect(code) }, enabled = enabled, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = if (code == selectedCurrency) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(custom?.symbol ?: system?.getSymbol(locale).orEmpty(), style = MaterialTheme.typography.titleLarge)
                                Text(code, fontWeight = FontWeight.Bold)
                                Text(custom?.name ?: system?.getDisplayName(locale).orEmpty(), style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                } }
                if (!showAll && search.isBlank()) item { TextButton(onClick = { showAll = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.view_all)) } }
            }
        }
    }
}

@Composable
private fun CoreCurrencyRatesSheet(onBack: () -> Unit, initialCurrency: String? = null,
    viewModel: CoreCurrenciesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val goBack: () -> Unit = { if (!viewModel.back()) onBack() }
    BackHandler(onBack = goBack)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(initialCurrency) { viewModel.initialCurrency(initialCurrency) }
    var deletingCurrency by remember { mutableStateOf<CustomCurrency?>(null) }
    var deletingRate by remember { mutableStateOf<ExchangeRate?>(null) }
    LaunchedEffect(state.commandRevision) { deletingCurrency = null; deletingRate = null }
    val enabled = state.loaded && !state.saving
    HootbookModalBottomSheet(onDismissRequest = goBack, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background) {
        Text(stringResource(R.string.exchange_rates), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.navigationBlocked) item { Text(stringResource(R.string.core_currencies_saving)) }
            if(state.downloadFailed)item{Text(stringResource(R.string.core_currency_download_failed),color=MaterialTheme.colorScheme.error)}
            state.error?.let { error -> item { Surface(color = MaterialTheme.colorScheme.errorContainer) { Column(Modifier.padding(12.dp)) {
                Text(error); TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.core_currencies_dismiss)) }
            } } } }
            item { Text(stringResource(R.string.core_currencies_main_currency, state.mainCurrency)) }
            item { Text(stringResource(R.string.core_currencies_provider), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { RateProvider.entries.forEach { provider ->
                    FilterChip(selected = state.provider == provider, onClick = { viewModel.provider(provider) }, enabled = enabled,
                        label = { Text(providerLabel(provider)) })
                } }
                FlowRow { Button(onClick = { viewModel.download(true) }, enabled = enabled) { Text(stringResource(R.string.core_currencies_download)) }
                    TextButton(onClick = { viewModel.download(false) }, enabled = enabled) { Text(stringResource(R.string.core_currencies_download_if_expired)) } }
                state.refreshOutcome?.let { outcome -> Text(when (outcome) {
                    RefreshOutcome.NotNeeded -> stringResource(R.string.core_currencies_not_needed)
                    is RefreshOutcome.Updated -> stringResource(R.string.core_currencies_updated, providerLabel(outcome.provider), outcome.count.toString())
                    is RefreshOutcome.Failed -> stringResource(R.string.core_currencies_download_failed, outcome.status?.toString() ?: stringResource(R.string.core_currencies_no_status))
                }, color = if (outcome is RefreshOutcome.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
            }
            item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.core_currencies_convert), style = MaterialTheme.typography.titleMedium)
                val draft = state.conversionDraft
                OutlinedTextField(draft.amount, { viewModel.conversionChanged(draft.copy(amount = it)) }, enabled = enabled, singleLine = true,
                    label = { Text(stringResource(R.string.core_currencies_amount)) })
                OutlinedTextField(draft.from, { viewModel.conversionChanged(draft.copy(from = it)) }, enabled = enabled, singleLine = true,
                    label = { Text(stringResource(R.string.core_currencies_from)) })
                OutlinedTextField(draft.to, { viewModel.conversionChanged(draft.copy(to = it)) }, enabled = enabled, singleLine = true,
                    label = { Text(stringResource(R.string.core_currencies_to)) })
                Text(stringResource(R.string.core_currencies_convert_note))
                TextButton(onClick = viewModel::convert, enabled = enabled) { Text(stringResource(R.string.core_currencies_convert)) }
                state.previewError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.preview?.let { preview ->
                    if (preview.amount == null) Text(stringResource(R.string.core_currencies_missing_rate, preview.input.from, preview.input.to), color = MaterialTheme.colorScheme.error)
                    else Text("${preview.input.amount} ${preview.input.from} = ${preview.amount.toPlainString()} ${preview.input.to}")
                    preview.rate?.let { rate ->
                        Text("1 ${rate.from} = ${rate.rate.toPlainString()} ${rate.to}")
                        Text(stringResource(when (rate.kind) { RateKind.DIRECT -> R.string.core_currencies_direct; RateKind.INVERSE -> R.string.core_currencies_inverse; RateKind.CROSS -> R.string.core_currencies_cross }) + rate.via?.let { " · $it" }.orEmpty())
                        if (rate.custom) Text(stringResource(R.string.core_currencies_custom))
                        if (rate.expired) Text(stringResource(R.string.core_currencies_expired), color = MaterialTheme.colorScheme.error)
                    }
                }
            } } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.core_currencies_custom_currencies), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { viewModel.editCurrency() }, enabled = enabled) { Text(stringResource(R.string.core_currencies_add)) }
            } }
            items(state.customCurrencies, key = { it.code }) { currency -> OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text("${currency.code} · ${currency.name} · ${currency.symbol}")
                FlowRow { TextButton(onClick = { viewModel.editCurrency(currency) }, enabled = enabled) { Text(stringResource(R.string.core_currencies_edit)) }
                    TextButton(onClick = { viewModel.clearError(); deletingCurrency = currency }, enabled = enabled) { Text(stringResource(R.string.core_currencies_delete)) } }
            } } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.core_currencies_rates), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { viewModel.editRate() }, enabled = enabled) { Text(stringResource(R.string.core_currencies_add)) }
            } }
            if (!state.loading && state.rates.isEmpty()) item { Text(stringResource(R.string.core_currencies_no_rates)) }
            items(state.rates, key = { it.from + "/" + it.to }) { rate -> OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("1 ${rate.from} = ${rate.rate.toPlainString()} ${rate.to}")
                Text(if (rate.custom) stringResource(R.string.core_currencies_custom) else rate.provider)
                Text(stringResource(R.string.core_currencies_updated_at, timestamp(rate.updatedAt)))
                Text(stringResource(R.string.core_currencies_expires_at, timestamp(rate.expiresAt)))
                if (rate.expired) Text(stringResource(R.string.core_currencies_expired), color = MaterialTheme.colorScheme.error)
                FlowRow { TextButton(onClick = { viewModel.editRate(rate) }, enabled = enabled) {
                    Text(stringResource(if (rate.custom) R.string.core_currencies_edit else R.string.core_currencies_override)) }
                    if (rate.custom) TextButton(onClick = { viewModel.clearError(); deletingRate = rate }, enabled = enabled) { Text(stringResource(R.string.core_currencies_delete)) }
                }
            } } }
        }
    }
    state.currencyDraft?.let { draft -> AlertDialog(onDismissRequest = viewModel::dismissCurrency,
        title = { Text(stringResource(R.string.core_currencies_custom_currencies)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft.code, { viewModel.currencyDraftChanged(draft.copy(code = it)) }, enabled = !state.saving && draft.originalCode == null,
                singleLine = true, label = { Text(stringResource(R.string.core_currencies_code)) })
            OutlinedTextField(draft.name, { viewModel.currencyDraftChanged(draft.copy(name = it)) }, enabled = !state.saving,
                label = { Text(stringResource(R.string.core_currencies_name)) })
            OutlinedTextField(draft.symbol, { viewModel.currencyDraftChanged(draft.copy(symbol = it)) }, enabled = !state.saving,
                label = { Text(stringResource(R.string.core_currencies_symbol)) })
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(onClick = viewModel::saveCurrency, enabled = !state.saving) { Text(stringResource(R.string.core_currencies_save)) } },
        dismissButton = { TextButton(onClick = viewModel::dismissCurrency, enabled = !state.saving) { Text(stringResource(R.string.core_currencies_cancel)) } }) }
    state.rateDraft?.let { draft -> AlertDialog(onDismissRequest = viewModel::dismissRate,
        title = { Text(stringResource(R.string.core_currencies_custom_rate)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.core_currencies_custom_rate_note))
            OutlinedTextField(draft.from, { viewModel.rateDraftChanged(draft.copy(from = it)) }, enabled = !state.saving && draft.original == null,
                singleLine = true, label = { Text(stringResource(R.string.core_currencies_from)) })
            OutlinedTextField(draft.to, { viewModel.rateDraftChanged(draft.copy(to = it)) }, enabled = !state.saving && draft.original == null,
                singleLine = true, label = { Text(stringResource(R.string.core_currencies_to)) })
            OutlinedTextField(draft.amount, { viewModel.rateDraftChanged(draft.copy(amount = it)) }, enabled = !state.saving,
                singleLine = true, label = { Text(stringResource(R.string.core_currencies_rate)) })
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(onClick = viewModel::saveRate, enabled = !state.saving) { Text(stringResource(R.string.core_currencies_save)) } },
        dismissButton = { TextButton(onClick = viewModel::dismissRate, enabled = !state.saving) { Text(stringResource(R.string.core_currencies_cancel)) } }) }
    deletingCurrency?.let { currency -> DeleteCurrencyDialog(currency.code, stringResource(R.string.core_currencies_delete_currency_note), state.saving, state.error,
        confirm = { viewModel.deleteCurrency(currency) }, dismiss = { if (!state.saving) deletingCurrency = null }) }
    deletingRate?.let { rate -> DeleteCurrencyDialog("${rate.from} → ${rate.to}", stringResource(R.string.core_currencies_delete_rate_note), state.saving, state.error,
        confirm = { viewModel.deleteRate(rate) }, dismiss = { if (!state.saving) deletingRate = null }) }
}

@Composable
private fun DeleteCurrencyDialog(title: String, note: String, saving: Boolean, error: String?, confirm: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Column { Text(note); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { TextButton(onClick = confirm, enabled = !saving) { Text(stringResource(R.string.core_currencies_delete)) } },
        dismissButton = { TextButton(onClick = dismiss, enabled = !saving) { Text(stringResource(R.string.core_currencies_cancel)) } })
}
@Composable
private fun providerLabel(provider: RateProvider): String = when (provider) {
    RateProvider.AUTO -> stringResource(R.string.core_currencies_auto)
    RateProvider.ER_API -> "ER API"
    RateProvider.FRANKFURTER -> "Frankfurter"
    RateProvider.CURRENCY_API -> "Currency API"
}
private fun timestamp(value: LocalDateTime): String = "%04d-%02d-%02d %02d:%02d:%02d".format(value.year,
    value.month.toInt(), value.day.toInt(), value.hour.toInt(), value.minute.toInt(), value.second.toInt())
