package io.github.hootkit_dev.hootbook.data.brokerage

import io.github.hootkit_dev.hootbook.domain.brokerage.*
import org.junit.Assert.*
import org.junit.Test

internal fun flexReport(rows: String = position(), account: String = "TEST_ACCOUNT", date: String = "20260904") =
    """<FlexQueryResponse><FlexStatements count="1"><FlexStatement accountId="$account" toDate="$date"><OpenPositions>$rows</OpenPositions></FlexStatement></FlexStatements></FlexQueryResponse>"""
internal fun position(extra: String = "", id: String = "100", currency: String = "USD", quantity: String = "1.125", value: String = "1234.56", level: String = "SUMMARY") =
    """<OpenPosition conid="$id" symbol="TEST" description="Test holding" currency="$currency" position="$quantity" positionValue="$value" costBasisMoney="1000.12" fifoPnlUnrealized="234.44" levelOfDetail="$level" $extra/>"""
internal fun flexError(code: String) = """<FlexStatementResponse><Status>Fail</Status><ErrorCode>$code</ErrorCode><ErrorMessage>PRIVATE RESPONSE</ErrorMessage></FlexStatementResponse>"""
internal fun assertBrokerError(expected: BrokerageError, action: () -> Unit) {
    try { action(); fail("Expected $expected") } catch (e: BrokerageException) { assertEquals(expected, e.error) }
}

class IbkrFlexParserTest {
    private val parser = IbkrFlexParser()
    @Test fun parsesFractionalHoldingsWithoutFloatingPointLoss() {
        val account = parser.parse(flexReport()).single()
        assertEquals("2026-09-04", account.asOf)
        assertEquals("1.125", account.holdings.single().quantity)
        assertEquals("1234.56", account.holdings.single().marketValue)
        assertEquals("234.44", account.holdings.single().unrealizedPnl)
    }
    @Test fun ignoresLotsWhenSummaryExists() {
        assertEquals(1, parser.parse(flexReport(position() + position(level = "LOT"))).single().holdings.size)
    }
    @Test fun rejectsLotOnlyQueryRatherThanSilentlyShowingNoPositions() {
        assertBrokerError(BrokerageError.INVALID_QUERY) { parser.parse(flexReport(position(level = "LOT"))) }
    }
    @Test fun acceptsEmptyPositionsButRejectsMissingSection() {
        assertTrue(parser.parse(flexReport("")).single().holdings.isEmpty())
        assertBrokerError(BrokerageError.INVALID_QUERY) { parser.parse(flexReport("").replace("<OpenPositions></OpenPositions>", "")) }
    }
    @Test fun handlesMultipleAccountsAndCurrencies() {
        val xml = flexReport(position() + position(id = "200", currency = "HKD"))
            .replace("</FlexStatements>", """<FlexStatement accountId="SECOND_TEST_ACCOUNT" toDate="20260904"><OpenPositions/></FlexStatement></FlexStatements>""")
        val accounts = parser.parse(xml)
        assertEquals(2, accounts.size)
        assertEquals(setOf("USD", "HKD"), accounts.first { it.accountId == "TEST_ACCOUNT" }.holdings.map { it.currency }.toSet())
    }
    @Test fun preservesShortPositionsAndReportedOptionValue() {
        val h = parser.parse(flexReport(position(quantity = "-2", value = "-500", extra = "multiplier=\"100\" markPrice=\"2.5\""))).single().holdings.single()
        assertEquals("-2", h.quantity); assertEquals("-500", h.marketValue)
    }
    @Test fun missingOptionalAmountsStayUnknownNotZero() {
        val row = """<OpenPosition conid="100" symbol="TEST" currency="USD" position="1" levelOfDetail="SUMMARY"/>"""
        val h = parser.parse(flexReport(row)).single().holdings.single()
        assertNull(h.marketValue); assertNull(h.costBasis); assertNull(h.unrealizedPnl)
    }
    @Test fun rejectsMissingIdentifiersAndMalformedAmounts() {
        listOf(position().replace("conid=\"100\"", ""), position(quantity = "NaN"), position(value = "1,234.56"), position(currency = "BASE_SUMMARY")).forEach { row ->
            assertBrokerError(BrokerageError.INVALID_REPORT) { parser.parse(flexReport(row)) }
        }
    }
    @Test fun selectsLatestReportDateWithoutDoubleCountingHistory() {
        val rows = position(extra = "reportDate=\"20260903\"") + position(extra = "reportDate=\"20260904\"", quantity = "2")
        val a = parser.parse(flexReport(rows)).single()
        assertEquals("2", a.holdings.single().quantity)
    }
    @Test fun rejectsDuplicateSummariesButKeepsDifferentModels() {
        assertBrokerError(BrokerageError.INVALID_REPORT) { parser.parse(flexReport(position() + position())) }
        assertEquals(2, parser.parse(flexReport(position(extra = "model=\"A\"") + position(extra = "model=\"B\""))).single().holdings.size)
    }
    @Test fun rejectsCrossAccountRowsInvalidDatesAndUnexpectedRoots() {
        assertBrokerError(BrokerageError.INVALID_REPORT) { parser.parse(flexReport(position(extra = "accountId=\"OTHER\""))) }
        assertBrokerError(BrokerageError.INVALID_REPORT) { parser.parse(flexReport(date = "20269999")) }
        assertBrokerError(BrokerageError.INVALID_REPORT) { parser.parse("<html>login</html>") }
    }
    @Test fun rejectsDtdEntitiesAndOversizedReports() {
        assertBrokerError(BrokerageError.INVALID_REPORT) { parser.parse("<!DOCTYPE x [<!ENTITY secret SYSTEM 'file:///not-read'>]>" + flexReport()) }
        assertBrokerError(BrokerageError.INVALID_REPORT) { parser.parse(" ".repeat(IbkrFlexParser.MAX_REPORT_BYTES + 1)) }
    }
    @Test fun parsesReferenceWithoutUsingResponseUrl() {
        assertEquals("123", parser.reference("<FlexStatementResponse><Status>Success</Status><ReferenceCode>123</ReferenceCode><url>https://invalid.example</url></FlexStatementResponse>"))
        assertBrokerError(BrokerageError.INVALID_REPORT) { parser.reference("<FlexStatementResponse><Status>Success</Status><ReferenceCode>bad</ReferenceCode></FlexStatementResponse>") }
    }
    @Test fun parsesCashAndNegativeMarginDebit() {
        val xml = flexReport().replace(
            "</OpenPositions></FlexStatement>",
            """</OpenPositions><CashReport>
                <CashReportCurrency accountId="TEST_ACCOUNT" currency="USD" endingCash="-250.5" endingSettledCash="-200"/>
                <CashReportCurrency currency="HKD" endingCash="80.00"/>
                <CashReportCurrency currency="BASE_SUMMARY" endingCash="1"/>
            </CashReport></FlexStatement>"""
        )
        val cash = parser.parse(xml).single().cashBalances
        assertEquals(listOf("HKD", "USD"), cash.map { it.currency })
        assertEquals("-250.5", cash.single { it.currency == "USD" }.endingCash)
        assertEquals("-200", cash.single { it.currency == "USD" }.endingSettledCash)
        assertEquals("80.00", cash.single { it.currency == "HKD" }.endingCash)
    }
    @Test fun missingCashReportLeavesCashEmpty() {
        assertTrue(parser.parse(flexReport()).single().cashBalances.isEmpty())
    }
    @Test fun rejectsCashRowsFromAnotherAccount() {
        val xml = flexReport().replace(
            "</OpenPositions></FlexStatement>",
            """</OpenPositions><CashReport>
                <CashReportCurrency accountId="OTHER" currency="USD" endingCash="1"/>
            </CashReport></FlexStatement>"""
        )
        assertBrokerError(BrokerageError.INVALID_REPORT) { parser.parse(xml) }
    }
    @Test fun classifiesErrorsWithoutExposingServerText() {
        mapOf("1012" to BrokerageError.EXPIRED_CREDENTIALS, "1013" to BrokerageError.IP_RESTRICTED,
            "1014" to BrokerageError.INVALID_QUERY, "1015" to BrokerageError.INVALID_CREDENTIALS,
            "1018" to BrokerageError.RATE_LIMITED, "1019" to BrokerageError.REPORT_NOT_READY,
            "1020" to BrokerageError.INVALID_REPORT).forEach { (code, error) ->
            assertBrokerError(error) { parser.parse(flexError(code)) }
        }
        try { parser.reference(flexError("1012")) } catch (e: BrokerageException) {
            assertFalse(e.toString().contains("PRIVATE")); assertNull(e.cause)
        }
    }
}
