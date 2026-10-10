@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.data.currency.RateFailure
import io.github.hootkit_dev.hootbook.data.currency.RateServerChoice
import io.github.hootkit_dev.hootbook.data.currency.RateSyncStatus
import io.github.hootkit_dev.hootbook.data.currency.model.CurrencyConversion
import io.github.hootkit_dev.hootbook.data.currency.model.CurrencySymbols
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ISO 4217 codes that are not money anyone keeps an account in: metals, test and fund codes.
private val NON_MONEY = setOf(
    "XAU", "XAG", "XPT", "XPD", "XDR", "XTS", "XXX", "XBA", "XBB", "XBC", "XBD", "XSU", "XUA",
    "BOV", "CHE", "CHW", "CLF", "COU", "MXV", "USN", "USS", "UYI", "UYW"
)

private val FIAT: Set<String> by lazy {
    java.util.Currency.getAvailableCurrencies().map { it.currencyCode }.toSet() - NON_MONEY
}

/** Currencies a person might hold: ISO 4217 money, not crypto tokens or metals. */
fun isFiat(code: String): Boolean = code.uppercase() in FIAT

/**
 * The rate list in three parts: the accounts' currencies, other fiat, and the rest (crypto and
 * the like), which the sheet folds away.
 */
data class RateGroups(
    val accounts: List<CurrencyConversion>,
    val fiat: List<CurrencyConversion>,
    val other: List<CurrencyConversion>
)

fun groupRates(conversions: List<CurrencyConversion>, accountCurrencies: Set<String>): RateGroups {
    val (accounts, rest) = conversions.partition { it.currencyCode.uppercase() in accountCurrencies }
    val (fiat, other) = rest.partition { isFiat(it.currencyCode) }
    return RateGroups(accounts, fiat, other)
}

/** "10月4日 08:12" in Chinese, "Oct 4, 08:12" otherwise. */
@Composable
fun rateTime(epochMillis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofPattern(if (locale.language == "zh") "M月d日 HH:mm" else "MMM d, HH:mm", locale)
    }
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}

@Composable
private fun failureText(failure: RateFailure): String = when (failure) {
    RateFailure.Timeout -> stringResource(R.string.rate_failure_timeout)
    RateFailure.Unreachable -> stringResource(R.string.rate_failure_unreachable)
    is RateFailure.Http -> stringResource(R.string.rate_failure_http, failure.code)
    RateFailure.BadResponse -> stringResource(R.string.rate_failure_bad_response)
    is RateFailure.Other -> stringResource(R.string.rate_failure_other, failure.message)
}

@Composable
private fun choiceLabel(choice: RateServerChoice): String = when (choice) {
    RateServerChoice.AUTO -> stringResource(R.string.rate_server_auto)
    else -> choice.servers.first().displayName.substringBefore(" (")
}

/**
 * When rates were last updated and from where; while syncing, a progress bar; after a failed
 * sync, each server tried and why it gave nothing. Below, the server choice and "Sync now".
 */
@Composable
fun RateSyncCard(
    lastUpdatedEpochSeconds: Long,
    lastSync: RateSyncStatus?,
    isSyncing: Boolean,
    isOffline: Boolean,
    serverChoice: RateServerChoice,
    onSelectServer: (RateServerChoice) -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(stringResource(R.string.rate_sync_title), style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            val supporting = MaterialTheme.colorScheme.onSurfaceVariant
            if (lastUpdatedEpochSeconds > 0) {
                val source = lastSync?.succeededWith?.displayName
                Text(
                    text = listOfNotNull(
                        stringResource(R.string.rate_sync_updated, rateTime(lastUpdatedEpochSeconds * 1000)),
                        source?.let { stringResource(R.string.rate_sync_source, it) }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = supporting
                )
            } else {
                Text(stringResource(R.string.rate_sync_never), style = MaterialTheme.typography.bodySmall, color = supporting)
            }
            when {
                isSyncing -> {
                    Text(stringResource(R.string.rate_sync_syncing), style = MaterialTheme.typography.bodySmall, color = supporting)
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                lastSync != null && !lastSync.succeeded -> {
                    Text(
                        text = stringResource(R.string.rate_sync_failed) + " · " + rateTime(lastSync.attemptedAtMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    lastSync.failures.forEach { (server, failure) ->
                        Text(
                            text = stringResource(R.string.rate_failure_line, server.displayName, failureText(failure)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
                isOffline -> Text(stringResource(R.string.rate_sync_offline), style = MaterialTheme.typography.bodySmall, color = supporting)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                var menuOpen by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = { menuOpen = true }) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(stringResource(R.string.rate_server_label), style = MaterialTheme.typography.labelSmall,
                                color = supporting)
                            Text(choiceLabel(serverChoice), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        }
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        RateServerChoice.entries.forEach { choice ->
                            DropdownMenuItem(
                                text = { Text(choiceLabel(choice)) },
                                onClick = { menuOpen = false; onSelectServer(choice) }
                            )
                        }
                    }
                }
                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onSyncNow, enabled = !isSyncing) {
                    Text(stringResource(R.string.rate_sync_now))
                }
            }
        }
    }
}

/**
 * Converts an amount between two currencies with the stored rates. [conversions] are relative
 * to [baseCode] (1 base = rate × code), so any pair goes through the base.
 */
@Composable
fun CurrencyCalculatorDialog(
    baseCode: String,
    conversions: List<CurrencyConversion>,
    currencies: List<String>,
    initialFrom: String,
    initialTo: String,
    lastUpdatedEpochSeconds: Long,
    onDismiss: () -> Unit
) {
    val rates = remember(conversions, baseCode) {
        conversions.associate { it.currencyCode.uppercase() to BigDecimal.valueOf(it.rate) } + (baseCode.uppercase() to BigDecimal.ONE)
    }
    var from by remember { mutableStateOf(initialFrom) }
    var to by remember { mutableStateOf(initialTo) }
    var amountText by remember { mutableStateOf("100") }

    val rate = convertRate(rates, from, to)
    val amount = amountText.toBigDecimalOrNull()

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
                        text = if (rate != null && amount != null) {
                            CurrencySymbols.getSymbol(to) + amount.multiply(rate).setScale(2, RoundingMode.HALF_UP).toPlainString()
                        } else "—",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    CurrencyPicker(to, currencies) { to = it }
                }
                Text(
                    text = if (rate != null) {
                        "1 $from = ${rate.round(MathContext(6)).stripTrailingZeros().toPlainString()} $to" +
                            if (lastUpdatedEpochSeconds > 0) " · " + rateTime(lastUpdatedEpochSeconds * 1000) else ""
                    } else stringResource(R.string.rate_calculator_no_rate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

/** How many [to] one [from] buys, through the base the rates are relative to. */
internal fun convertRate(rates: Map<String, BigDecimal>, from: String, to: String): BigDecimal? {
    val fromRate = rates[from.uppercase()]?.takeIf { it.signum() > 0 } ?: return null
    val toRate = rates[to.uppercase()] ?: return null
    return toRate.divide(fromRate, MathContext(12))
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
