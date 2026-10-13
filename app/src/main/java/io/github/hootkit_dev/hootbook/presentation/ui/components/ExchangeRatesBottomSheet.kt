@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Search
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.accounts.CurrencyViewModel
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing

@Composable
fun ExchangeRatesBottomSheet(viewModel: CurrencyViewModel, onDismiss: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf(TextFieldValue("")) }
    var editTarget by remember { mutableStateOf<ResolvedRate?>(null) }
    var editRate by remember { mutableStateOf("") }
    var calculatorPair by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showOthers by remember { mutableStateOf(false) }
    LaunchedEffect(state.commandRevision) { editTarget = null }
    val rows = state.rates.values.filterNotNull().filter { it.to != state.base && (it.to.contains(search.text, true) || CoreMoneyPresentation.getCurrencySymbol(it.to).contains(search.text, true)) }
    val accountRows = rows.filter { it.to in state.accountCurrencies }
    val fiatRows = rows.filter { it.to !in state.accountCurrencies && runCatching { java.util.Currency.getInstance(it.to) }.isSuccess }
    val others = rows.filter { it.to !in state.accountCurrencies && runCatching { java.util.Currency.getInstance(it.to) }.isFailure }
    HootbookModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background, dragHandle = { BottomSheetDefaults.DragHandle() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.exchange_rates), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = { calculatorPair = (state.accountCurrencies - state.base).firstOrNull().let { it ?: "USD" } to state.base },
                    shapes = IconButtonDefaults.shapes(), modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.Rounded.Calculate, stringResource(R.string.rate_calculator))
                }
            }
            Spacer(Modifier.height(12.dp))
            RateSyncCard(state.storedRates, state.provider, state.outcome, state.refreshing, viewModel::selectProvider, viewModel::syncNow, Modifier.padding(bottom = 12.dp))
            SearchBarBox(searchQuery = search, onSearchQueryChange = { search = it }, label = { Text(stringResource(R.string.search_currencies)) },
                leadingIcon = { Icon(Iconax.Search, "Search", tint = MaterialTheme.colorScheme.onSurface.copy(0.5f)) }, modifier = Modifier.padding(bottom = 12.dp))
            state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                item { Text(stringResource(R.string.rates_relative_to, state.base) + "\n" + stringResource(R.string.rate_custom_hint),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp)) }
                listOf(R.string.rate_section_accounts to accountRows, R.string.rate_section_other to (fiatRows + if (showOthers || search.text.isNotBlank()) others else emptyList())).forEach { (title, section) ->
                    if (section.isNotEmpty()) {
                        item { Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)) }
                        itemsIndexed(section, key = { _, row -> row.to }) { index, row ->
                            ExchangeRateItem(row, index == 0, index == section.lastIndex,
                                onClick = { calculatorPair = row.to to state.base }, onLongClick = { editTarget = row; editRate = row.rate.toPlainString() })
                        }
                    }
                }
                if (others.isNotEmpty() && search.text.isBlank()) item {
                    TextButton(onClick = { showOthers = !showOthers }, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                        Text(if (showOthers) stringResource(R.string.rate_show_less) else stringResource(R.string.rate_show_more, others.size))
                    }
                }
                item {
                    val uri = androidx.compose.ui.platform.LocalUriHandler.current
                    Text(stringResource(R.string.rates_attribution), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { uri.openUri("https://www.exchangerate-api.com") }).padding(vertical = 12.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    calculatorPair?.let { (from, to) -> CurrencyCalculatorDialog(state.rates.keys.toList(), from, to,
        converted = state.converted, resolvedRate = state.conversionRate,
        lastUpdated = state.storedRates.filter { !it.custom }.maxByOrNull { it.updatedAt.coreDateText() }?.updatedAt, onInputsChange = viewModel::convert,
        onDismiss = { calculatorPair = null }) }
    editTarget?.let { row ->
        AlertDialog(onDismissRequest = { if (!state.saving) editTarget = null },
            title = { Text("${row.to} (${CoreMoneyPresentation.getCurrencySymbol(row.to)})", fontWeight = FontWeight.Bold) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.exchange_rate_format, row.from, row.rate.toPlainString(), CoreMoneyPresentation.getCurrencySymbol(row.to)), style = MaterialTheme.typography.bodySmall)
                TextField(editRate, { editRate = it }, label = { Text(stringResource(R.string.custom_rate)) }, singleLine = true, shape = RoundedCornerShape(Spacing.md), enabled = !state.saving)
                if (row.custom) Text(stringResource(R.string.custom_rate_set), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            } },
            confirmButton = { DialogConfirmButton(stringResource(R.string.save), enabled = !state.saving, onClick = { editRate.toBigDecimalOrNull()?.let { viewModel.saveCustomRate(row.to, it) } }) },
            dismissButton = {
                DialogDismissButton(stringResource(R.string.cancel), enabled = !state.saving, onClick = { if (!state.saving) editTarget = null })
                if (row.custom) DialogDismissButton(stringResource(R.string.reset), enabled = !state.saving, onClick = { viewModel.resetCustomRate(row.to) })
            }, containerColor = HootbookDialogDefaults.containerColor)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExchangeRateItem(
    conversion: ResolvedRate,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null
) {
    val baseSymbol = CoreMoneyPresentation.getCurrencySymbol(conversion.from)

    ListItem(
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${conversion.to} (${CoreMoneyPresentation.getCurrencySymbol(conversion.to)})",
                    fontWeight = FontWeight.Bold,
                    fontFamily = MaterialTheme.typography.bodyLarge.fontFamily
                )
                if (conversion.custom) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.custom_label),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        supportingContent = {
            Text(
                text = "1 $baseSymbol = ${conversion.rate.stripTrailingZeros().toPlainString()} ${CoreMoneyPresentation.getCurrencySymbol(conversion.to)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .clip(
                RoundedCornerShape(
                    topStart = if (isFirst) 16.dp else 0.dp,
                    topEnd = if (isFirst) 16.dp else 0.dp,
                    bottomStart = if (isLast) 16.dp else 0.dp,
                    bottomEnd = if (isLast) 16.dp else 0.dp
                )
            )
    )
    if (!isLast) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}
