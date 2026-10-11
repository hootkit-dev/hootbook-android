@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun CurrencyCalculatorDialog(
    currencies: List<String>, initialFrom: String, initialTo: String,
    converted: BigDecimal?, resolvedRate: ResolvedRate?, lastUpdated: LocalDateTime? = null,
    onInputsChange: (String, String, String) -> Unit, onDismiss: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateTimeFormatter.ofPattern(if (locale.language == "zh") "M月d日 HH:mm" else "MMM d, HH:mm", locale) }
    var from by remember { mutableStateOf(initialFrom) }
    var to by remember { mutableStateOf(initialTo) }
    var amountText by remember { mutableStateOf("100") }

    LaunchedEffect(amountText, from, to) { onInputsChange(amountText, from, to) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rate_calculator)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { text -> amountText = text.filter { it.isDigit() || it == '.' } },
                    label = { Text(stringResource(R.string.rate_calculator_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    trailingIcon = { CurrencyPicker(from, currencies) { from = it } },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = { val f = from; from = to; to = f }) {
                        Icon(Icons.Rounded.SwapVert, contentDescription = stringResource(R.string.rate_calculator_swap))
                    }
                    Text(
                        text = CoreMoneyPresentation.format(converted, to),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    CurrencyPicker(to, currencies) { to = it }
                }
                Text(
                    text = resolvedRate?.let { "1 $from = ${it.rate.stripTrailingZeros().toPlainString()} $to" + (lastUpdated?.let { date -> " · " + date.coreDateText(dateFormat) } ?: "") }
                        ?: stringResource(R.string.rate_calculator_no_rate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
private fun CurrencyPicker(selected: String, currencies: List<String>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(shapes = ButtonDefaults.shapes(), onClick = { open = true }) {
            Text(selected, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            currencies.forEach { code ->
                DropdownMenuItem(text = { Text(code) }, onClick = { open = false; onSelect(code) })
            }
        }
    }
}

@Composable
private fun providerLabel(provider: RateProvider): String = when (provider) {
    RateProvider.AUTO -> stringResource(R.string.rate_server_auto)
    RateProvider.ER_API -> "ExchangeRate-API"
    RateProvider.FRANKFURTER -> "Frankfurter"
    RateProvider.CURRENCY_API -> "CurrencyAPI"
}

@Composable
fun RateSyncCard(storedRates: List<ExchangeRate>, provider: RateProvider?, outcome: RefreshOutcome?,
    isSyncing: Boolean, onSelectServer: (RateProvider) -> Unit, onSyncNow: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateTimeFormatter.ofPattern(if (locale.language == "zh") "M月d日 HH:mm" else "MMM d, HH:mm", locale) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.rate_sync_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val last = storedRates.filter { !it.custom }.maxByOrNull { it.updatedAt.coreDateText() }
            if (last != null) Text(stringResource(R.string.rate_sync_updated, last.updatedAt.coreDateText(dateFormat)) + " · " + stringResource(R.string.rate_sync_source, last.provider),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Text(stringResource(R.string.rate_sync_never), style = MaterialTheme.typography.bodySmall)
            if (isSyncing) {
                Text(stringResource(R.string.rate_sync_syncing), style = MaterialTheme.typography.bodySmall)
                LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
            } else if (outcome is RefreshOutcome.Failed) {
                Text(stringResource(R.string.rate_sync_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                outcome.status?.let { Text(stringResource(R.string.rate_failure_http, it.toInt()), style = MaterialTheme.typography.labelSmall) }
            }
            Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                var open by remember { mutableStateOf(false) }
                Box(Modifier.weight(1f)) {
                    TextButton(onClick = { open = true }, shapes = ButtonDefaults.shapes()) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(stringResource(R.string.rate_server_label), style = MaterialTheme.typography.labelSmall)
                            provider?.let { Text(providerLabel(it), style = MaterialTheme.typography.bodyMedium, maxLines = 1) }
                        }
                        Icon(Icons.Rounded.KeyboardArrowDown, null)
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        RateProvider.entries.forEach { choice -> DropdownMenuItem(text = { Text(providerLabel(choice)) }, onClick = { open = false; onSelectServer(choice) }) }
                    }
                }
                FilledTonalButton(onClick = onSyncNow, enabled = !isSyncing, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.rate_sync_now)) }
            }
        }
    }
}
