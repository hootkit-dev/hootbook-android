package io.github.hootkit_dev.hootbook.presentation.ui.components

import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.AccountCategory
import java.math.BigDecimal
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountDisplayTest {
    private fun account(name: String, last4: String = "1234", balance: String = "10", wallet: Boolean = false, credit: Boolean = false) =
        AccountBalanceEntity(bankName = name, accountLast4 = last4, balance = BigDecimal(balance),
            timestamp = LocalDateTime.of(2026, 10, 3, 12, 0), isWallet = wallet, isCreditCard = credit)

    @Test fun `account numbers are masked one way and wallets have none`() {
        assertEquals("•••• 2616", account("BofA (Checking)", "2616").maskedNumber())
        assertNull(account("Cash", "wallet", wallet = true).maskedNumber())
        assertNull(maskAccountNumber("wallet"))
        assertNull(maskAccountNumber(""))
        assertNull(maskAccountNumber(null))
    }

    @Test fun `accounts of one bank resolve to the same institution despite suffixes`() {
        assertEquals(account("BofA (Checking)").institutionKey(), account("BofA (Savings)").institutionKey())
        assertEquals(account("BofA (Checking)").institutionKey(), institutionKeyOf("Bank of America"))
        // Names outside the catalog fall back to the part before the suffix.
        assertEquals(account("Local Credit Union (Joint)").institutionKey(), institutionKeyOf("Local Credit Union"))
    }

    @Test fun `same bank accounts are adjacent, the main account and its bank first`() {
        val accounts = listOf(
            account("中国银行", "0451", balance = "9000"),
            account("BofA (Savings)", "7731", balance = "5000"),
            account("Agricultural Bank of China", "1111", balance = "8000"),
            account("BofA (Checking)", "2616", balance = "100")
        )
        val sorted = accounts.sortedForDisplay(mainKey = "BofA (Checking)_2616")
        assertEquals(listOf("BofA (Checking)", "BofA (Savings)"), sorted.take(2).map { it.bankName })
        // Without a main account the order no longer follows balances.
        val names = accounts.sortedForDisplay(null).map { it.bankName }
        assertEquals(names.indexOf("BofA (Checking)") + 1, names.indexOf("BofA (Savings)"))
    }

    @Test fun `groups follow the Accounts screen order and skip empty types`() {
        val groups = listOf(
            account("BofA Customized", "8888", credit = true),
            account("BofA (Checking)", "2616"),
            account("Cash", "wallet", wallet = true)
        ).groupedForDisplay(null)
        assertEquals(listOf(AccountCategory.WALLETS, AccountCategory.BANKS, AccountCategory.CREDIT_CARDS), groups.map { it.first })
    }
}
