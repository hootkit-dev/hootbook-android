@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.currencies

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R

@Composable
fun CoreCurrenciesScreen(onBack: () -> Unit, initialCurrency: String? = null,
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
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.core_currencies_title)) },
        navigationIcon = { TextButton(onClick = goBack, enabled = !state.saving) { Text(stringResource(R.string.core_currencies_back)) } },
        actions = { TextButton(onClick = { viewModel.clearError(); viewModel.refresh() }, enabled = !state.saving) { Text(stringResource(R.string.core_currencies_reload)) } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.loading || state.saving) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
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
                if (state.previewLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
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
