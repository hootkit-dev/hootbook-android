@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.currencies

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.ui.components.*
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem
import io.github.hootkit_dev.hootbook.utils.DateFormats

/**
 * Currencies: how amounts are shown (one display currency, a default for new entries) and the
 * exchange rates page (provider and download, a converter, custom currencies, saved rates).
 */
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
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(initialCurrency) { viewModel.initialCurrency(initialCurrency) }
    val goBack: () -> Unit = { if (!viewModel.back()) { if (showRates) showRates = false else onBack() } }
    BackHandler(onBack = goBack)
    SecondaryScreen(
        title = stringResource(if (showRates) R.string.exchange_rates else R.string.currency_settings_title), onBack = goBack,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            (prefsError?.let { stringResource(it) } ?: state.error)?.let { ErrorBanner(it, viewModel::clearError, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            if (showRates) RatesPage(state, viewModel)
            else LazyColumn(contentPadding = ScreenContentPadding, verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
                item(key = "display") {
                    ListSection(title = stringResource(R.string.cur_display)) {
                        item(headline = { Text(stringResource(R.string.unified_currency)) }, supporting = { Text(stringResource(R.string.unified_currency_subtitle)) },
                            trailing = { HootbookSwitch(prefs.unifiedDisplayEnabled, null, enabled = enabled) }, toggled = prefs.unifiedDisplayEnabled,
                            onToggle = if (enabled) ({ value -> appearance.update { it.copy(unifiedDisplayEnabled = value, unifiedDisplayCurrency = it.unifiedDisplayCurrency ?: state.mainCurrency.takeIf(String::isNotBlank)) } }) else null)
                        if (prefs.unifiedDisplayEnabled) item(headline = { Text(stringResource(R.string.display_currency)) },
                            trailing = { Text(prefs.unifiedDisplayCurrency ?: state.mainCurrency, color = MaterialTheme.colorScheme.primary) },
                            chevron = true, onClick = if (enabled) ({ picker = "display" }) else null)
                        item(headline = { Text(stringResource(R.string.default_currency)) }, supporting = { Text(stringResource(R.string.default_currency_subtitle)) },
                            trailing = { HootbookSwitch(prefs.defaultEntryEnabled, null, enabled = enabled) }, toggled = prefs.defaultEntryEnabled,
                            onToggle = if (enabled) ({ value -> appearance.update { it.copy(defaultEntryEnabled = value, defaultEntryCurrency = it.defaultEntryCurrency ?: state.mainCurrency.takeIf(String::isNotBlank)) } }) else null)
                        if (prefs.defaultEntryEnabled) item(headline = { Text(stringResource(R.string.default_label)) },
                            trailing = { Text(prefs.defaultEntryCurrency ?: state.mainCurrency, color = MaterialTheme.colorScheme.primary) },
                            chevron = true, onClick = if (enabled) ({ picker = "entry" }) else null)
                    }
                }
                item(key = "rates") {
                    ListSection(title = stringResource(R.string.exchange_rates)) {
                        item(headline = { Text(stringResource(R.string.cur_main)) }, trailing = { Text(state.mainCurrency, style = MaterialTheme.typography.bodyLarge) })
                        item(headline = { Text(stringResource(R.string.exchange_rates)) }, supporting = { Text(stringResource(R.string.rates_info_subtitle)) },
                            leading = { Icon(Icons.AutoMirrored.Filled.ShowChart, null) }, chevron = true, onClick = if (state.loaded) ({ showRates = true }) else null)
                    }
                }
            }
        }
    }
    picker?.let { target ->
        CoreCurrencyPickerSheet(selectedCurrency = if (target == "entry") prefs.defaultEntryCurrency ?: state.mainCurrency else prefs.unifiedDisplayCurrency ?: state.mainCurrency,
            currencies = state.customCurrencies, enabled = enabled, onDismiss = { picker = null }, onSelect = { code ->
                appearance.update { if (target == "entry") it.copy(defaultEntryCurrency = code) else it.copy(unifiedDisplayCurrency = code) }
                picker = null
            })
    }
    RateDialogs(state, viewModel)
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

/** Provider and download, the converter, custom currencies, then every saved rate (searchable). */
@Composable
private fun RatesPage(state: CoreCurrenciesUiState, viewModel: CoreCurrenciesViewModel) {
    val enabled = state.loaded && !state.saving
    var search by rememberSaveable { mutableStateOf("") }
    val rates = state.rates.filter { search.isBlank() || it.from.contains(search, true) || it.to.contains(search, true) }
    LazyColumn(contentPadding = ScreenContentPadding, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (state.navigationBlocked) item(key = "saving") { Text(stringResource(R.string.core_currencies_saving), Modifier.padding(16.dp)) }
        if (state.downloadFailed) item(key = "failed") { Text(stringResource(R.string.core_currency_download_failed), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        item(key = "provider") {
            Column(Modifier.padding(bottom = SectionSpacing), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectField(stringResource(R.string.core_currencies_provider), state.provider?.let { providerLabel(it) } ?: providerLabel(RateProvider.AUTO),
                    RateProvider.entries.map { it to providerLabel(it) }, { viewModel.provider(it) }, enabled = enabled)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { viewModel.download(true) }, enabled = enabled, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.core_currencies_download)) }
                    OutlinedButton(onClick = { viewModel.download(false) }, enabled = enabled, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.core_currencies_download_if_expired)) }
                }
                state.refreshOutcome?.let { outcome -> Text(when (outcome) {
                    RefreshOutcome.NotNeeded -> stringResource(R.string.core_currencies_not_needed)
                    is RefreshOutcome.Updated -> stringResource(R.string.core_currencies_updated, providerLabel(outcome.provider), outcome.count.toString())
                    is RefreshOutcome.Failed -> stringResource(R.string.core_currencies_download_failed, outcome.status?.toString() ?: stringResource(R.string.core_currencies_no_status))
                }, style = MaterialTheme.typography.bodyMedium, color = if (outcome is RefreshOutcome.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item(key = "convert") { Converter(state, viewModel, enabled) }
        item(key = "custom") {
            ListSection(Modifier.padding(vertical = SectionSpacing), title = stringResource(R.string.core_currencies_custom_currencies),
                action = { TooltipIconButton(Icons.Rounded.Add, stringResource(R.string.cur_new_currency), { viewModel.editCurrency() }, enabled = enabled) }) {
                if (state.customCurrencies.isEmpty()) item(headline = { Text(stringResource(R.string.core_plans_none), color = MaterialTheme.colorScheme.onSurfaceVariant) })
                state.customCurrencies.forEach { currency ->
                    item(leading = { Text(currency.symbol, style = MaterialTheme.typography.titleMedium) }, headline = { Text(currency.code) }, supporting = { Text(currency.name) },
                        trailing = { OverflowMenu(enabled = enabled, actions = listOf(
                            MenuAction(stringResource(R.string.core_currencies_edit), Icons.Rounded.Edit) { viewModel.editCurrency(currency) },
                            MenuAction(stringResource(R.string.core_currencies_delete), Icons.Rounded.Delete, destructive = true) { viewModel.clearError(); viewModel.requestDeleteCurrency(currency) },
                        )) })
                }
            }
        }
        item(key = "rates-title") {
            SectionTitle(stringResource(R.string.core_currencies_rates), action = {
                TooltipIconButton(Icons.Rounded.Add, stringResource(R.string.core_currencies_custom_rate), { viewModel.editRate() }, enabled = enabled) })
            SearchBarBox(searchQuery = TextFieldValue(search), onSearchQueryChange = { search = it.text }, label = { Text(stringResource(R.string.cur_search_rates)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, modifier = Modifier.padding(bottom = 8.dp),
                trailingIcon = { if (search.isNotEmpty()) TooltipIconButton(Icons.Rounded.Close, stringResource(R.string.clear_search), { search = "" }) })
        }
        if (!state.loading && rates.isEmpty()) item(key = "no-rates") {
            Text(stringResource(R.string.core_currencies_no_rates), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        itemsIndexed(rates, key = { _, rate -> rate.from + "/" + rate.to }) { index, rate ->
            ListItem(
                headline = { Text("1 ${rate.from} = ${rate.rate.stripTrailingZeros().toPlainString()} ${rate.to}") },
                supporting = { Text(listOfNotNull(if (rate.custom) stringResource(R.string.core_currencies_custom) else rate.provider,
                    stringResource(R.string.cur_updated, rate.updatedAt.display()),
                    if (rate.expired) stringResource(R.string.core_currencies_expired) else null).joinToString(" · "),
                    color = if (rate.expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) },
                trailing = { OverflowMenu(enabled = enabled, actions = listOfNotNull(
                    MenuAction(stringResource(if (rate.custom) R.string.core_currencies_edit else R.string.core_currencies_override), Icons.Rounded.Edit) { viewModel.editRate(rate) },
                    if (rate.custom) MenuAction(stringResource(R.string.core_currencies_delete), Icons.Rounded.Delete, destructive = true) { viewModel.clearError(); viewModel.requestDeleteRate(rate) } else null,
                )) },
                shape = ListItemPosition.from(index, rates.size).toShape(), padding = PaddingValues(0.dp),
            )
        }
    }
}

/** Amount, from and to; the result as money, with the rate it used and how it was found. */
@Composable
private fun Converter(state: CoreCurrenciesUiState, viewModel: CoreCurrenciesViewModel, enabled: Boolean) {
    val draft = state.conversionDraft
    Surface(shape = MaterialTheme.shapes.largeIncreased, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.core_currencies_convert), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            FormTextField(draft.amount, stringResource(R.string.core_currencies_amount), { viewModel.conversionChanged(draft.copy(amount = it)) }, enabled = enabled, decimal = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FormTextField(draft.from, stringResource(R.string.core_currencies_from), { viewModel.conversionChanged(draft.copy(from = it.uppercase())) }, Modifier.weight(1f), enabled = enabled)
                TooltipIconButton(Icons.Rounded.SwapHoriz, stringResource(R.string.txn_swap), { viewModel.conversionChanged(draft.copy(from = draft.to, to = draft.from)) }, enabled = enabled)
                FormTextField(draft.to, stringResource(R.string.core_currencies_to), { viewModel.conversionChanged(draft.copy(to = it.uppercase())) }, Modifier.weight(1f), enabled = enabled)
            }
            FilledTonalButton(onClick = viewModel::convert, enabled = enabled, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.core_currencies_convert)) }
            state.previewError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.preview?.let { preview ->
                if (preview.amount == null) Text(stringResource(R.string.core_currencies_missing_rate, preview.input.from, preview.input.to), color = MaterialTheme.colorScheme.error)
                else Text(CoreMoneyPresentation.format(preview.amount, preview.input.to), style = MaterialTheme.typography.headlineSmall)
                preview.rate?.let { rate ->
                    Text(listOfNotNull("1 ${rate.from} = ${rate.rate.stripTrailingZeros().toPlainString()} ${rate.to}",
                        stringResource(when (rate.kind) { RateKind.DIRECT -> R.string.core_currencies_direct; RateKind.INVERSE -> R.string.core_currencies_inverse; RateKind.CROSS -> R.string.core_currencies_cross }) + rate.via?.let { " · $it" }.orEmpty(),
                        if (rate.custom) stringResource(R.string.core_currencies_custom) else null,
                        if (rate.expired) stringResource(R.string.core_currencies_expired) else null).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = if (rate.expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.core_currencies_convert_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The custom currency and custom rate forms, and their delete confirmations. */
@Composable
private fun RateDialogs(state: CoreCurrenciesUiState, viewModel: CoreCurrenciesViewModel) {
    state.currencyDraft?.let { draft ->
        FormSheet(stringResource(if (draft.originalCode == null) R.string.cur_new_currency else R.string.cur_edit_currency), viewModel::dismissCurrency, viewModel::saveCurrency,
            busy = state.saving, error = state.error) {
            FormTextField(draft.code, stringResource(R.string.core_currencies_code), { viewModel.currencyDraftChanged(draft.copy(code = it.uppercase())) }, enabled = !state.saving && draft.originalCode == null)
            FormTextField(draft.name, stringResource(R.string.core_currencies_name), { viewModel.currencyDraftChanged(draft.copy(name = it)) }, enabled = !state.saving)
            FormTextField(draft.symbol, stringResource(R.string.core_currencies_symbol), { viewModel.currencyDraftChanged(draft.copy(symbol = it)) }, enabled = !state.saving)
        }
    }
    state.rateDraft?.let { draft ->
        FormSheet(stringResource(R.string.core_currencies_custom_rate), viewModel::dismissRate, viewModel::saveRate, busy = state.saving, error = state.error) {
            Text(stringResource(R.string.core_currencies_custom_rate_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormTextField(draft.from, stringResource(R.string.core_currencies_from), { viewModel.rateDraftChanged(draft.copy(from = it.uppercase())) }, Modifier.weight(1f), enabled = !state.saving && draft.original == null)
                FormTextField(draft.to, stringResource(R.string.core_currencies_to), { viewModel.rateDraftChanged(draft.copy(to = it.uppercase())) }, Modifier.weight(1f), enabled = !state.saving && draft.original == null)
            }
            FormTextField(draft.amount, stringResource(R.string.core_currencies_rate), { viewModel.rateDraftChanged(draft.copy(amount = it)) }, enabled = !state.saving, decimal = true)
        }
    }
    state.deletingCurrency?.let { currency -> ConfirmDialog(currency.code, stringResource(R.string.core_currencies_delete_currency_note), stringResource(R.string.core_currencies_delete),
        { viewModel.deleteCurrency(currency) }, viewModel::cancelDelete, destructive = true, enabled = !state.saving) }
    state.deletingRate?.let { rate -> ConfirmDialog("${rate.from} → ${rate.to}", stringResource(R.string.core_currencies_delete_rate_note), stringResource(R.string.core_currencies_delete),
        { viewModel.deleteRate(rate) }, viewModel::cancelDelete, destructive = true, enabled = !state.saving) }
}

@Composable
private fun providerLabel(provider: RateProvider): String = when (provider) {
    RateProvider.AUTO -> stringResource(R.string.core_currencies_auto)
    RateProvider.ER_API -> "ER API"
    RateProvider.FRANKFURTER -> "Frankfurter"
    RateProvider.CURRENCY_API -> "Currency API"
}

private fun LocalDateTime.display(): String = java.time.LocalDateTime.of(year, month.toInt(), day.toInt(), hour.toInt(), minute.toInt()).let {
    DateFormats.shortDate(it.toLocalDate()) + " " + DateFormats.time(it) }
