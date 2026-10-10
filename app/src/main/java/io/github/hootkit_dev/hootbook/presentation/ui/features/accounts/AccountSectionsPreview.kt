package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.preferences.ThemeStyle
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme
import java.math.BigDecimal
import java.time.LocalDateTime

@Preview(name = "Accounts · light", showBackground = true, heightDp = 1100)
@Preview(name = "Accounts · dark", showBackground = true, heightDp = 1100, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun AccountSectionsPreview() {
    val groups = remember {
        val now = LocalDateTime.of(2026, 9, 7, 12, 0)
        buildAccountSections(listOf(
            AccountBalanceEntity(bankName = "Cash", accountLast4 = "wallet", balance = BigDecimal("280"), timestamp = now, currency = "CNY", isWallet = true),
            AccountBalanceEntity(bankName = "Everyday bank", accountLast4 = "8821", balance = BigDecimal("28560"), timestamp = now, currency = "CNY"),
            AccountBalanceEntity(bankName = "Travel bank", accountLast4 = "0138", balance = BigDecimal("8200"), timestamp = now, currency = "HKD"),
            AccountBalanceEntity(bankName = "Savings", accountLast4 = "6208", balance = BigDecimal("16480"), timestamp = now, currency = "CNY"),
            AccountBalanceEntity(bankName = "Visa", accountLast4 = "7712", balance = BigDecimal("2680"), timestamp = now, currency = "CNY", isCreditCard = true)
        )).visible
    }
    var expandedSections by rememberSaveable { mutableStateOf(emptyList<String>()) }
    HootbookTheme(themeStyle = ThemeStyle.DEFAULT, dynamicColor = false, blurEffects = false) {
        Surface {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("Accounts", style = MaterialTheme.typography.headlineLarge) }
                groups.forEach { group ->
                    val expanded = group.kind.name in expandedSections
                    val toggle: () -> Unit = {
                        expandedSections = if (expanded) expandedSections - group.kind.name else expandedSections + group.kind.name
                    }
                    item(key = group.kind.name) {
                        AccountSectionSummary(group, expanded = expanded, onToggle = toggle)
                    }
                    items(group.visibleAccounts(expanded), key = { it.listKey() }) { account ->
                        CompactAccountCard(account, false, {})
                    }
                    if (group.showFooterToggle(expanded)) item {
                        AccountSectionToggle(group, expanded, toggle)
                    }
                }
            }
        }
    }
}
