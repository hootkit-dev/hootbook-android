package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import java.math.BigDecimal
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class AccountSectionsTest {
    private fun account(name: String, amount: String = "10", currency: String = "CNY", wallet: Boolean = false, credit: Boolean = false) =
        AccountBalanceEntity(bankName = name, accountLast4 = "1234", balance = BigDecimal(amount), currency = currency,
            timestamp = LocalDateTime.of(2026, 9, 7, 12, 0), isWallet = wallet, isCreditCard = credit)

    @Test fun `four sections separate assets from liabilities and investments`() {
        val sections = buildAccountSections(listOf(account("Wallet", wallet = true), account("Bank"), account("Card", credit = true), account("IBKR")))
        assertEquals(AccountSectionKind.entries, sections.visible.map { it.kind })
        assertEquals(listOf("Wallet", "Bank", "Card", "IBKR"), sections.visible.map { it.accounts.single().bankName })
    }
    @Test fun `totals include collapsed members but never mix currencies`() {
        val sections = buildAccountSections(listOf(account("A", "10.25"), account("B", "20.50"), account("C", "30.75"), account("US", "999", "USD")))
        val banks = sections.visible[1]
        assertEquals(BigDecimal("61.50"), banks.totals["CNY"])
        assertEquals(BigDecimal("999"), banks.totals["USD"])
        assertEquals(0, banks.visibleAccounts(false).size)
        assertEquals(4, banks.visibleAccounts(true).size)
    }
    @Test fun `stable keys survive balance updates and distinguish same suffixes`() {
        val original = account("Bank A")
        assertEquals(original.listKey(), original.copy(id = 20, balance = BigDecimal("77")).listKey())
        assertNotEquals(original.listKey(), account("Bank B").listKey())
        assertNotEquals(account("a_b").copy(accountLast4 = "c").listKey(), account("a").copy(accountLast4 = "b_c").listKey())
    }
    @Test fun `overlapping legacy flags appear only in credit cards`() {
        val groups = buildAccountSections(listOf(account("Legacy", wallet = true, credit = true))).visible
        assertEquals(listOf(0, 0, 1, 0), groups.map { it.accounts.size })
    }
    @Test fun `small and empty groups expand without loss`() {
        val empty = buildAccountSections(emptyList())
        assertEquals(4, empty.visible.size)
        assertTrue(empty.visible.all { it.visibleAccounts(false).isEmpty() })
        val banks = buildAccountSections(listOf(account("A"))).visible[1]
        assertTrue(banks.visibleAccounts(false).isEmpty())
        assertEquals(1, banks.visibleAccounts(true).size)
    }
    @Test fun `negative balances retain their sign and exact cents`() {
        val cards = buildAccountSections(listOf(account("Refund", "-12.20", credit = true), account("Debt", "5.15", credit = true))).visible[2]
        assertEquals(BigDecimal("-7.05"), cards.totals["CNY"])
    }

    @Test fun `wallets start summary only and expand all members without changing totals`() {
        val wallets = buildAccountSections(listOf(account("Cash", "10.25", wallet = true), account("WeChat Pay", "20.50", wallet = true), account("Alipay", "30.75", wallet = true))).visible[0]
        assertTrue(wallets.visibleAccounts(false).isEmpty())
        assertEquals(3, wallets.visibleAccounts(true).size)
        assertEquals(BigDecimal("61.50"), wallets.totals["CNY"])
        assertFalse(wallets.showFooterToggle(false))
        assertTrue(wallets.showFooterToggle(true))
        assertTrue(wallets.visibleAccounts(false).isEmpty())
    }
    @Test fun `even a single wallet starts collapsed and remains expandable`() {
        for (count in 1..2) {
            val wallets = buildAccountSections((1..count).map { account("Wallet $it", wallet = true) }).visible[0]
            assertTrue(wallets.visibleAccounts(false).isEmpty())
            assertEquals(count, wallets.visibleAccounts(true).size)
            assertTrue(wallets.showFooterToggle(true))
        }
    }
    @Test fun `banks and credit cards start summary only and expand every row`() {
        val groups = buildAccountSections((1..3).map { account("Bank $it") } + (1..3).map { account("Card $it", credit = true) }).visible
        groups.filter { it.kind == AccountSectionKind.BANKS || it.kind == AccountSectionKind.CREDIT_CARDS }.forEach { group ->
            assertEquals(0, group.visibleAccounts(false).size)
            assertFalse(group.showFooterToggle(false))
            assertTrue(group.showFooterToggle(true))
            assertEquals(3, group.visibleAccounts(true).size)
        }
    }
}
