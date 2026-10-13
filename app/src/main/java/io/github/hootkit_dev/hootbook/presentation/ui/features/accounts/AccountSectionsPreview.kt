package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme
import java.math.BigDecimal

private val previewTime = LocalDateTime(2026, 10u, 11u, 12u, 0u, 0u, 0u)
private val previewAccount = Account("sample", "Bank", "1234", AccountKind.BANK, "CNY",
    listOf(Pocket("CNY", BigDecimal("1250"), null, null), Pocket("USD", BigDecimal("30"), null, null)),
    Money(BigDecimal("1460"), "CNY"), false, null, null, null, null, null, null,
    "type_finance_bank", "#33B5E5", false, true, 0, previewTime)

@Preview(showBackground = true)
@Composable
private fun AccountSectionsPreview() {
    HootbookTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AccountSectionSummary(AccountCategory.BANKS, listOf(previewAccount), expanded = true, onToggle = {})
            CompactAccountCard(previewAccount, true, {})
        }
    }
}
