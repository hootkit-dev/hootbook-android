package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.data.model.Currency
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencySelectionBottomSheet(
    selectedCurrency: String,
    availableCurrencies: List<String>,
    onCurrencySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    HootbookModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp)
        ) {
            Text(
                text = stringResource(R.string.select_currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth()
            )

            availableCurrencies.forEachIndexed { index, currency ->
                ListItem(
                    headline = {
                        Text(
                            text = Currency.getByCode(currency)?.let { "${it.localizedName()} (${it.code})" } ?: currency
                        )
                    },
                    trailing = {
                        RadioButton(
                            selected = currency == selectedCurrency,
                            onClick = null
                        )
                    },
                    selected = currency == selectedCurrency,
                    onClick = {
                        onCurrencySelected(currency)
                        onDismiss()
                    },
                    shape = ListItemPosition.from(index, availableCurrencies.size).toShape()
                )
            }
        }
    }
}
