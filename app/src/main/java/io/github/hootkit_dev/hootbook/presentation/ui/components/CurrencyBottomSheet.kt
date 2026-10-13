@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import io.github.hootkit_dev.hootbook.presentation.effects.BlurredAnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hootkit.core.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hootkit_dev.hootbook.presentation.accounts.CurrencyViewModel
import androidx.compose.material.icons.outlined.Info
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import java.math.BigDecimal
import kotlinx.coroutines.launch
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Search
import io.github.hootkit_dev.hootbook.presentation.ui.icons.CloseCircle
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Information

@Composable
fun CurrencyBottomSheet(
    selectedCurrency: String,
    onCurrencySelected: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: CurrencyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(selectedCurrency) { viewModel.selectCurrency(selectedCurrency) }
    val customCurrencies = state.customCurrencies
    val saving = state.saving
    var showRateInfo by rememberSaveable { mutableStateOf(false) }
    var showRates by rememberSaveable { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    var showAllCurrencies by rememberSaveable { mutableStateOf(false) }
    var showCustomCurrency by rememberSaveable { mutableStateOf(false) }
    val originalCodes = listOf("CNY", "HKD", "TWD", "MOP", "INR", "USD", "EUR", "GBP", "AED", "SGD", "CAD", "AUD", "JPY", "NPR", "ETB", "THB", "MYR", "KWD", "KRW", "SAR", "BYN", "COP", "KES", "CHF", "SEK", "NZD", "MXN")
    val codes = remember(customCurrencies) {
        (originalCodes + customCurrencies.map { it.code }).distinct()
    }
    val common = listOf("CNY", "HKD", "USD", "SGD", "TWD", "MOP", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "INR", "SEK", "NZD", "MXN", "AED", "KRW")
    val custom = customCurrencies.associateBy { it.code }
    fun name(code: String) = custom[code]?.name ?: runCatching { java.util.Currency.getInstance(code).displayName }.getOrDefault(code)
    val filtered = codes.sortedWith(compareBy<String> { common.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }.thenBy { name(it) }).filter { code ->
        code.contains(searchQuery.text, true) || name(code).contains(searchQuery.text, true) ||
            (custom[code]?.symbol ?: CoreMoneyPresentation.getCurrencySymbol(code)).contains(searchQuery.text, true)
    }
    val displayed = if (showAllCurrencies || searchQuery.text.isNotBlank()) filtered else filtered.filter { it in common }.take(15)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    HootbookModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background, dragHandle = { BottomSheetDefaults.DragHandle() }) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.currencies), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth())
                SearchBarBox(searchQuery = searchQuery, onSearchQueryChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.search_currencies)) },
                    leadingIcon = { Icon(Iconax.Search, stringResource(R.string.search), tint = MaterialTheme.colorScheme.onSurface.copy(0.5f)) },
                    trailingIcon = {
                        if (searchQuery.text.isNotEmpty()) IconButton(onClick = { searchQuery = TextFieldValue("") }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Iconax.CloseCircle, stringResource(R.string.clear), tint = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                        } else IconButton(onClick = { showRateInfo = true }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Iconax.Information, "Info", tint = MaterialTheme.colorScheme.onSurface.copy(0.5f), modifier = Modifier.size(24.dp))
                        }
                    }, modifier = Modifier.padding(horizontal = Spacing.md).padding(bottom = 16.dp))
                state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (filtered.isEmpty()) Text(stringResource(R.string.currency_not_available), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    displayed.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            row.forEach { code ->
                                CurrencyCard(code = code, symbol = custom[code]?.symbol ?: CoreMoneyPresentation.getCurrencySymbol(code),
                                    name = custom[code]?.name ?: runCatching { java.util.Currency.getInstance(code).displayName }.getOrDefault(code),
                                    modifier = Modifier.weight(1f), isSelected = code == selectedCurrency,
                                    onCurrencyCardClick = { scope.launch { sheetState.hide(); onCurrencySelected(code) } })
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                if (!showAllCurrencies && searchQuery.text.isBlank() && filtered.size > 15) TextButton(onClick = { showAllCurrencies = true }, shapes = ButtonDefaults.shapes(), colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.inverseSurface.copy(0.5f)), modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp)) {
                    Text(stringResource(R.string.view_all_currencies), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(80.dp))
                }
            }
            Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface))).padding(16.dp)) {
                Button(onClick = { showCustomCurrency = true }, modifier = Modifier.height(56.dp), shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_custom_currency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            // Exchange Rate Info Dialog
            BlurredAnimatedVisibility(
                visible = showRateInfo,
                enter = fadeIn() + scaleIn(
                    animationSpec = tween(durationMillis = 300),
                    initialScale = 0f,
                ),
                exit = fadeOut() + scaleOut(
                    animationSpec = tween(durationMillis = 100),
                    targetScale = 0f,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showRateInfo = false }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .shadow(
                                elevation = 25.dp,
                                RoundedCornerShape(15.dp),
                                clip = true,
                                spotColor = Color.Black,
                                ambientColor = Color.Black
                            )
                            .fillMaxWidth(0.85f)
                            .clip(RoundedCornerShape(15.dp))
                            .background(
                                MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(15.dp)
                            )
                            .clickable(onClick = {})
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(15.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "Info",
                                tint = MaterialTheme.colorScheme.primary.copy(0.5f),
                                modifier = Modifier.size(50.dp)
                            )
                            Text(
                                text = stringResource(R.string.exchange_rates_notice_title),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.inverseSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = stringResource(R.string.exchange_rates_notice_text),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.inverseSurface.copy(0.8f),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    shapes = ButtonDefaults.shapes(),
                                    onClick = { showRateInfo = false },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                        contentColor = MaterialTheme.colorScheme.inverseSurface
                                    ),
                                ) {
                                    Text(text = stringResource(R.string.ok))
                                }
                                Button(
                                    shapes = ButtonDefaults.shapes(),
                                    onClick = {
                                        showRates = true
                                        showRateInfo = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = Color.White
                                    ),
                                ) {
                                    Text(text = stringResource(R.string.exchange_rates), color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                    }
                }
            }


        }
    }
    if (showCustomCurrency) AddCustomCurrencyBottomSheet(saving = saving, onDismiss = { if (!saving) showCustomCurrency = false },
        onSave = { name, symbol, code, rate -> viewModel.addCustomCurrency(CustomCurrency(code, name, symbol), rate) { showCustomCurrency = false } })
    if (showRates) ExchangeRatesBottomSheet(viewModel, onDismiss = { showRates = false })

}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddCustomCurrencyBottomSheet(
    saving: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (name: String, symbol: String, code: String, rate: BigDecimal) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    var countryName by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    HootbookModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(
                        state = scrollState,
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.custom_currency),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )


                TextField(
                    value = countryName,
                    onValueChange = { countryName = it },
                    shape = RoundedCornerShape(Spacing.md),
                    label = { Text(stringResource(R.string.currency_country_name))},
                    placeholder = { Text(stringResource(R.string.currency_name_hint)) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            0.7f
                        ),
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextField(
                        value = code,
                        onValueChange = { if (it.length <= 4) code = it.uppercase() },
                        label = { Text(stringResource(R.string.currency_code))},
                        placeholder = { Text(stringResource(R.string.currency_code_hint)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                0.7f
                            ),
                            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                        ),
                        shape = RoundedCornerShape(Spacing.md),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    TextField(
                        value = symbol,
                        onValueChange = { if (it.length <= 3) symbol = it },
                        label = { Text(stringResource(R.string.currency_symbol))},
                        placeholder = { Text(stringResource(R.string.currency_symbols_hint)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                0.7f
                            ),
                            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                        ),
                        shape = RoundedCornerShape(Spacing.md),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                TextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = { Text(stringResource(R.string.exchange_rate))},
                    placeholder = { Text(stringResource(R.string.exchange_rate_hint)) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            0.7f
                        ),
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                    ),
                    shape = RoundedCornerShape(Spacing.md),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Text(
                    text = stringResource(R.string.base_currency_hint),
                    color = MaterialTheme.colorScheme.onSurface.copy(0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 12.dp)
                )

                if (showError) {
                    Text(
                        text = stringResource(R.string.please_fill_all_fields),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(80.dp))
            }
            // Action Buttons at Bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Save button
                Button(
                    enabled = !saving,
                    onClick = {
                        val rateValue = rate.toBigDecimalOrNull()
                        if (countryName.isNotBlank() && symbol.isNotBlank() && code.isNotBlank() && rateValue != null && rateValue > BigDecimal.ZERO) {
                            onSave(countryName, symbol, code, rateValue)
                        } else {
                            showError = true
                        }
                    },
                    modifier = Modifier
                        .height(56.dp),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(
                        text = stringResource(R.string.save_currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
