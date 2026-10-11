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
import java.time.format.DateTimeFormatter

@Composable
internal fun HomeSectionHeader(title: String, onViewAll: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onViewAll) { Text(stringResource(R.string.home_all)) }
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

@Composable
internal fun RecentTransactionsCard(transactions: List<Transaction>, accounts: List<Account>, categories: List<Category>, converted: Map<String, CoreDisplayedMoney>, symbols: Map<String, String>, assets: CorePlatformAssets,
    loading: Boolean, onTransaction: (String) -> Unit, onViewAll: () -> Unit, modifier: Modifier = Modifier,
    loadFailed: Boolean = false, referenceCurrency: String? = null) {
    val locale = LocalConfiguration.current.locales[0]
    val today = JavaDate.now()
    Column(modifier.fillMaxWidth()) {
        HomeSectionHeader(stringResource(R.string.home_recent), onViewAll)
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            when {
                loading && transactions.isEmpty() -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(40.dp)) }
                loadFailed && transactions.isEmpty() -> Text(stringResource(R.string.home_recent_unavailable), Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                transactions.isEmpty() -> Text(stringResource(R.string.home_recent_empty), Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Column(Modifier.padding(bottom = 8.dp)) {
                    transactions.groupBy { JavaDate.of(it.dateTime.year, it.dateTime.month.toInt(), it.dateTime.day.toInt()) }.forEach { (day, rows) ->
                        Text(when (day) { today -> stringResource(R.string.today_lbl); today.minusDays(1) -> stringResource(R.string.yesterday_lbl); else -> day.format(DateTimeFormatter.ofPattern(if (locale.language == "zh") "M月d日" else "MMM d", locale)) }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
                        rows.forEach { transaction ->
                            Surface(onClick = { onTransaction(transaction.id) }, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.testTag("home_transaction_${transaction.id}")) {
                                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    val account = accounts.find { it.id == transaction.accountId }
                                    val category = categories.find { it.id == transaction.categoryId }
                                    val subcategory = category?.subcategories?.find { it.id == transaction.subcategoryId }
                                    val target = accounts.find { it.id == transaction.toAccountId }
                                    HomeMerchantIcon(transaction.merchant, assets, subcategory?.icon ?: category?.icon ?: account?.icon)
                                    Column(Modifier.weight(1f)) {
                                        Text(transaction.merchant.ifBlank { CoreKindLabel(transaction.kind) }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val subtitle = if (transaction.kind == TransactionKind.TRANSFER && account != null && target != null) "${account.name} → ${target.name}"
                                            else listOfNotNull(subcategory?.displayName ?: category?.displayName, account?.name).joinToString("　")
                                        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    val income = transaction.kind == TransactionKind.INCOME || transaction.kind == TransactionKind.LOAN_IN
                                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 160.dp)) {
                                        Text((if (income) "+" else if (transaction.kind == TransactionKind.TRANSFER) "" else "−") + HomeMoneyPresentation.format(transaction.amount, transaction.currency, symbols, useCode = transaction.currency != referenceCurrency), style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), color = if (income) moneyColors.income else if (transaction.kind == TransactionKind.TRANSFER) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                                        if (transaction.kind == TransactionKind.TRANSFER && transaction.toAmount != null) Text(stringResource(R.string.home_received, HomeMoneyPresentation.format(transaction.toAmount, transaction.toCurrency ?: transaction.currency, symbols, useCode = (transaction.toCurrency ?: transaction.currency) != referenceCurrency)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        else converted[transaction.id]?.let { Text(HomeMoneyPresentation.format(it.amount, it.currency, symbols), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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
                    Box(Modifier.offset(x = (index * 22).dp).size(40.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.surfaceContainerLow, CircleShape)) { HomeMerchantIcon(subscription.name, assets) }
                }
            }
        }
    }
}

@Composable
private fun HomeMerchantIcon(name: String, assets: CorePlatformAssets, fallback: String? = null) {
    val revision by assets.revision.collectAsStateWithLifecycle()
    val image = remember(name, revision) { assets.merchantIcon(name) }
    val brand = remember(name) { BrandIcons.getIconResource(name) }
    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        if (image != null || brand != null) AsyncImage(image ?: brand, null, Modifier.fillMaxSize())
        else if (!fallback.isNullOrBlank()) CoreRecordIcon(fallback, Modifier.size(26.dp))
        else Icon(Icons.Rounded.ReceiptLong, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
