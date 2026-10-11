@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.hootkit_dev.hootbook.presentation.ui.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.ui.CorePlatformAssets
import io.github.hootkit_dev.hootbook.presentation.core.home.CoreHomeUiState
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreDisplayedMoney
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreRecordIcon
import io.github.hootkit_dev.hootbook.presentation.common.icons.BrandIcons
import io.github.hootkit_dev.hootbook.presentation.core.analytics.CoreKindLabel
import io.github.hootkit_dev.hootbook.presentation.core.analytics.javaDate
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.*
import io.github.hootkit_dev.hootbook.presentation.ui.theme.moneyColors
import java.time.LocalDate as JavaDate
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.hootkit_dev.hootbook.presentation.ui.components.HeatmapWidget
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.TransactionItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import io.github.hootkit_dev.hootbook.utils.DateFormats
import java.time.format.DateTimeFormatter

@Composable
internal fun HomeSectionHeader(title: String, onViewAll: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        if (onViewAll != null) TextButton(onClick = onViewAll) { Text(stringResource(R.string.home_all)) }
    }
}

@Composable
internal fun AccountCategoryList(state: CoreHomeUiState, onOpen: (AccountKind) -> Unit, modifier: Modifier = Modifier) {
    val accounts = state.overview?.accounts.orEmpty().filterNot { it.archived }
    val kinds = accountSectionOrder.filter { kind -> kind == AccountKind.INVESTMENT || accounts.any { it.kind == kind } }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        kinds.forEachIndexed { index, kind ->
            val rows = accounts.filter { it.kind == kind }
            val top = if (index == 0) 24.dp else 6.dp
            val bottom = if (index == kinds.lastIndex) 24.dp else 6.dp
            Surface(onClick = { onOpen(kind) }, shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.testTag("home_kind_${kind.name}")) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) { Icon(accountKindIcon(kind), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(accountKindTitle(kind)), style = MaterialTheme.typography.bodyLarge)
                        Text(if (rows.isEmpty()) stringResource(R.string.home_connect_hint) else stringResource(R.string.overview_count, rows.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (rows.isEmpty()) Text(stringResource(R.string.home_connect), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    else {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** The latest transactions grouped by day, drawn with the ledger's own rows. */
@Composable
internal fun RecentTransactionsCard(transactions: List<Transaction>, accounts: List<Account>, categories: List<Category>, converted: Map<String, CoreDisplayedMoney>, symbols: Map<String, String>, assets: CorePlatformAssets,
    loading: Boolean, onTransaction: (String) -> Unit, onViewAll: () -> Unit, modifier: Modifier = Modifier,
    loadFailed: Boolean = false) {
    val today = JavaDate.now()
    val revision by assets.revision.collectAsStateWithLifecycle()
    Column(modifier.fillMaxWidth()) {
        HomeSectionHeader(stringResource(R.string.home_recent), onViewAll)
        when {
            loading && transactions.isEmpty() -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(40.dp)) }
            transactions.isEmpty() -> Surface(shape = MaterialTheme.shapes.largeIncreased, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (loadFailed) R.string.home_recent_unavailable else R.string.home_recent_empty), Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                transactions.groupBy { JavaDate.of(it.dateTime.year, it.dateTime.month.toInt(), it.dateTime.day.toInt()) }.forEach { (day, rows) ->
                    Text(when (day) { today -> stringResource(R.string.today_lbl); today.minusDays(1) -> stringResource(R.string.yesterday_lbl); else -> DateFormats.shortDate(day) },
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
                    rows.forEachIndexed { index, transaction ->
                        TransactionItem(transaction, account = accounts.find { it.id == transaction.accountId },
                            category = categories.find { it.id == transaction.categoryId }, targetAccount = accounts.find { it.id == transaction.toAccountId },
                            showDate = false, onClick = { onTransaction(transaction.id) }, convertedMoney = converted[transaction.id], customSymbols = symbols,
                            merchantIcon = remember(transaction.merchant, revision) { assets.merchantIcon(transaction.merchant) },
                            shape = ListItemPosition.from(index, rows.size).toShape())
                    }
                }
            }
        }
    }
}

/** How many transactions were recorded each day of the last six months. */
@Composable
internal fun ActivityHeatmapCard(data: Map<JavaDate, Int>, blurEffects: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        HomeSectionHeader(stringResource(R.string.home_activity), null, Modifier.padding(horizontal = 16.dp))
        HeatmapWidget(data = data, blurEffects = blurEffects)
    }
}

@Composable
internal fun UpcomingSubscriptionsCard(rows: List<Subscription>, summary: SubscriptionSummary?, symbols: Map<String, String>, assets: CorePlatformAssets, onViewAll: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onViewAll, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(stringResource(R.string.subscriptions_count_format, rows.size), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    summary?.let {
                        Text(HomeMoneyPresentation.format(it.monthly, it.currency, symbols), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.per_month).uppercase(), style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 2.dp))
                    }
                }
                if (summary?.complete == false) Text(stringResource(R.string.core_complete_incomplete), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Box(Modifier.width((40 + (rows.take(3).size - 1).coerceAtLeast(0) * 22).dp).height(40.dp)) {
                rows.take(3).forEachIndexed { index, subscription ->
                    Box(Modifier.offset(x = (index * 22).dp).size(40.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.surfaceContainerLow, CircleShape)) { io.github.hootkit_dev.hootbook.presentation.core.profile.CoreMerchantIcon(subscription.name, assets, Modifier.fillMaxSize()) }
                }
            }
        }
    }
}
