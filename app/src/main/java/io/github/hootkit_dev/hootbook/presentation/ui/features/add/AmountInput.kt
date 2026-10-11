package io.github.hootkit_dev.hootbook.presentation.ui.features.add

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Render the exact draft string. Never animate money through Float/Double. */
@Composable
fun AmountInput(amount: String, currencySymbol: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    Column(modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 28.dp)
        .testTag("transaction_amount"), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(currencySymbol, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(amount.ifBlank { "0" }, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, maxLines = 2)
    }
}

/** A decimal input pad edits text only; ledger and FX arithmetic belong to Core. */
@Composable
internal fun DecimalAmountPad(value: String, change: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "⌫")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key -> FilledTonalButton(onClick = {
                    change(when (key) { "⌫" -> value.dropLast(1); "." -> if ('.' in value) value else value.ifBlank { "0" } + "."; else -> value + key })
                }, modifier = Modifier.weight(1f).height(56.dp)) { Text(key, style = MaterialTheme.typography.headlineSmall) } }
            }
        }
    }
}
