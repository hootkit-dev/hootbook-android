package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.brokerage.BrokerConnection
import io.github.hootkit_dev.hootbook.domain.brokerage.*
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AccountOverviewTest {
    private fun account(name: String, currency: String = "CNY", value: String = "10", wallet: Boolean = false, credit: Boolean = false) =
        AccountBalanceEntity(bankName = name, accountLast4 = "1234", balance = BigDecimal(value), currency = currency,
            timestamp = LocalDateTime.of(2026,9,7,12,0), isWallet = wallet, isCreditCard = credit)
    private fun connection(value: String? = "120.25") = BrokerConnection("fixture", "ibkr", "Demo", listOf(
        BrokerageAccount("U1", "2026-09-07", listOf(Holding("1", "DEMO", "Demo", "USD", "2", value)))
    ), 0)
    @Test fun `four exclusive categories include manual brokerage accounts`() {
        assertEquals(AccountCategory.entries, listOf(account("Cash", wallet=true), account("ICBC"), account("Visa", credit=true), account("IBKR")).map { it.category() })
        assertEquals(AccountCategory.CREDIT_CARDS, account("IBKR", wallet=true,credit=true).category())
    }
    @Test fun `empty groups keep all four entries and connection is not zero`() = runBlocking {
        val result = buildOverview(emptyList(),emptyList(),"CNY",true,false) { _,_->null }
        assertEquals(AccountCategory.entries,result.map { it.category })
        assertTrue(result.take(3).all { it.amount == BigDecimal("0.00") })
        assertEquals(OverviewStatus.CONNECT,result.last().status);assertNull(result.last().amount)
    }
    @Test fun `converted groups preserve decimals and account counts`() = runBlocking {
        val rows = buildOverview(listOf(account("A",value="1.25"),account("B","USD","2.50")),emptyList(),"CNY",true,false) { _,_->BigDecimal("7.2") }
        assertEquals(BigDecimal("19.25"),rows[1].amount);assertEquals(2,rows[1].count);assertTrue(rows[1].converted)
    }
    @Test fun `missing exchange rate never returns mislabeled total`() = runBlocking {
        assertNull(overviewTotal(mapOf("USD" to BigDecimal.TEN),"CNY") { _,_->null })
    }
    @Test fun `card refunds keep negative sign`() = runBlocking {
        val result = buildOverview(listOf(account("Visa",value="-12.20",credit=true)),emptyList(),"CNY",true,false) { _,_->null }
        assertEquals(BigDecimal("-12.20"),result[2].amount)
    }
    @Test fun `snapshot includes cash and margin debit`() = runBlocking {
        val connection = BrokerConnection("fixture", "ibkr", "Demo", listOf(
            BrokerageAccount(
                "U1",
                "2026-09-07",
                listOf(Holding("1", "DEMO", "Demo", "USD", "2", "100.00")),
                listOf(CashBalance("USD", "-20.00"))
            )
        ), 0)
        val result = buildOverview(emptyList(), listOf(connection), "USD", true, false) { _, _ -> null }.last()
        assertEquals(BigDecimal("80.00"), result.amount)
    }
    @Test fun `snapshot only investment uses actual market value`() = runBlocking {
        val result = buildOverview(emptyList(),listOf(connection()),"USD",true,false) { _,_->null }.last()
        assertEquals(BigDecimal("120.25"),result.amount);assertTrue(result.isSnapshot);assertEquals(1,result.count)
    }
    @Test fun `manual balances and snapshots are both included`() = runBlocking {
        val result = buildOverview(listOf(account("IBKR")),listOf(connection()),"USD",true,false) { _,_->BigDecimal.ONE }.last()
        assertEquals(BigDecimal("130.25"),result.amount);assertTrue(result.hasSnapshots);assertEquals(2,result.count)
    }
    @Test fun `unknown market value is not zero`() = runBlocking {
        assertEquals(OverviewStatus.UNAVAILABLE,buildOverview(emptyList(),listOf(connection(null)),"USD",true,false) { _,_->null }.last().status)
    }
    @Test fun `load failure does not suggest no connection`() = runBlocking {
        assertEquals(OverviewStatus.UNAVAILABLE,buildOverview(emptyList(),emptyList(),"CNY",false,true) { _,_->null }.last().status)
    }
    @Test fun `manual investment stays out of bank total`() = runBlocking {
        val result=buildOverview(listOf(account("IBKR",value="100"),account("ICBC",value="20")),emptyList(),"CNY",true,false) { _,_->null }
        assertEquals(BigDecimal("20.00"),result[1].amount);assertEquals(BigDecimal("100.00"),result[3].amount);assertFalse(result[3].isSnapshot)
    }
    @Test fun `credit contribution deducts debt and adds a refund balance`() {
        val debt=AccountOverviewItem(AccountCategory.CREDIT_CARDS,amount=BigDecimal("4305.22"))
        assertEquals(BigDecimal("-4305.22"),debt.netWorthContribution())
        assertEquals(BigDecimal("12.20"),debt.copy(amount=BigDecimal("-12.20")).netWorthContribution())
    }
    @Test fun `snapshot investments count toward net worth`() {
        val investment=AccountOverviewItem(AccountCategory.INVESTMENTS,amount=BigDecimal("100"),isSnapshot=true)
        assertEquals(BigDecimal("100"),investment.netWorthContribution())
        assertEquals(BigDecimal("100"),investment.copy(isSnapshot=false).netWorthContribution())
    }
    @Test fun `the investments summary counts connected broker accounts and their snapshot`() {
        val manual = buildAccountSections(listOf(account("IBKR", "USD", "5"))).visible[3]
        val merged = manual.withBrokerSnapshots(listOf(connection()))
        assertEquals(1, merged.linkedCount)
        assertEquals(0, BigDecimal("125.25").compareTo(merged.totals["USD"]))
        // A holding without a market value leaves the snapshot out rather than undercounting
        assertEquals(manual.totals, manual.withBrokerSnapshots(listOf(connection(null))).totals)
        assertSame(manual, manual.withBrokerSnapshots(emptyList()))
    }
}
