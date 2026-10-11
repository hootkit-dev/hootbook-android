package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.currency

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.accounts.CurrencyViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.components.CurrencyBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.ExchangeRatesBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.PreferenceSwitch
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.utils.bottomFade
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CurrencySettingsScreen(
    onNavigateBack: () -> Unit,
    initialCurrency: String? = null,
    currencySettingsViewModel: CurrencySettingsViewModel = hiltViewModel(),
    currencyViewModel: CurrencyViewModel = hiltViewModel()
) {
    val uiState by currencySettingsViewModel.uiState.collectAsStateWithLifecycle()
    val ratesUiState by currencyViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.mainCurrency, initialCurrency) { currencyViewModel.selectCurrency(initialCurrency ?: uiState.mainCurrency.takeIf { it.isNotEmpty() } ?: "CNY") }
    var showMainCurrencyPicker by remember { mutableStateOf(false) }
    var showExchangeRateSheet by remember { mutableStateOf(false) }
    var showUnifiedCurrencyPicker by remember { mutableStateOf(false) }
    var showDefaultCurrencyPicker by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.currency_settings_title),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                navigationContent = { NavigationContent { onNavigateBack() } }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .verticalScroll(rememberScrollState())
                .padding(
                    top = Dimensions.Padding.content + paddingValues.calculateTopPadding()
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                SettingsGridCard(
                    title = stringResource(R.string.currency),
                    subtitle = uiState.mainCurrency,
                    icon = Icons.Rounded.AttachMoney,
                    onClick = { showMainCurrencyPicker = true },
                    modifier = Modifier.weight(1f)
                )
                SettingsGridCard(
                    title = stringResource(R.string.exchange_rates),
                    subtitle = stringResource(R.string.rates_info_subtitle),
                    icon = Icons.AutoMirrored.Filled.ShowChart,
                    onClick = {
                        currencyViewModel.selectCurrency(initialCurrency ?: uiState.mainCurrency)
                        showExchangeRateSheet = true
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                PreferenceSwitch(
                    title = stringResource(R.string.unified_currency),
                    subtitle = stringResource(R.string.unified_currency_subtitle),
                    checked = uiState.unifiedCurrencyEnabled,
                    onCheckedChange = { currencySettingsViewModel.toggleUnifiedCurrency() },
                    isFirst = true
                )

                AnimatedVisibility(visible = uiState.unifiedCurrencyEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md)
                            .background( color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(4.dp))
                            .clickable { showUnifiedCurrencyPicker = true }
                            .padding(vertical = Spacing.sm, horizontal = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.display_currency),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = uiState.unifiedCurrencyCode ?: stringResource(R.string.none),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                PreferenceSwitch(
                    title = stringResource(R.string.default_currency),
                    subtitle = stringResource(R.string.default_currency_subtitle),
                    checked = uiState.defaultCurrencyEnabled,
                    onCheckedChange = { currencySettingsViewModel.toggleDefaultCurrency() },
                    isLast = !uiState.defaultCurrencyEnabled
                )

                AnimatedVisibility(visible = uiState.defaultCurrencyEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(
                                    topStart = 4.dp,
                                    topEnd = 4.dp,
                                    bottomStart = 16.dp,
                                    bottomEnd = 16.dp
                                )
                            )
                            .clickable { showDefaultCurrencyPicker = true }
                            .padding(vertical = Spacing.sm, horizontal = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.default_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = uiState.defaultCurrencyCode ?: stringResource(R.string.none),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(modifier = Modifier.height(Spacing.xl))
        }
    }

    if (showExchangeRateSheet) {
        ExchangeRatesBottomSheet(viewModel = currencyViewModel, onDismiss = { showExchangeRateSheet = false })
    }
    if (showMainCurrencyPicker) {
        CurrencyBottomSheet(selectedCurrency = uiState.mainCurrency, onCurrencySelected = {
            currencySettingsViewModel.setMainCurrency(it); showMainCurrencyPicker = false
        }, onDismiss = { showMainCurrencyPicker = false })
    }

    if (showUnifiedCurrencyPicker) {
        CurrencyBottomSheet(
            selectedCurrency = uiState.unifiedCurrencyCode ?: "CNY",
            onCurrencySelected = { code ->
                currencySettingsViewModel.setUnifiedCurrency(code)
                showUnifiedCurrencyPicker = false
            },
            onDismiss = { showUnifiedCurrencyPicker = false }
        )
    }

    if (showDefaultCurrencyPicker) {
        CurrencyBottomSheet(
            selectedCurrency = uiState.defaultCurrencyCode ?: "CNY",
            onCurrencySelected = { code ->
                currencySettingsViewModel.setDefaultCurrency(code)
                showDefaultCurrencyPicker = false
            },
            onDismiss = { showDefaultCurrencyPicker = false }
        )
    }
}

@Composable
fun SettingsGridCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(130.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
