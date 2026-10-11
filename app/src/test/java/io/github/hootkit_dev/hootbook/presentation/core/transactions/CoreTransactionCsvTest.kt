package io.github.hootkit_dev.hootbook.presentation.core.transactions

import android.app.Application
import dev.hootkit.core.*
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class CoreTransactionCsvTest {
    @Test fun quotedCsvEscapesCommaQuoteAndNewlineAndNeutralizesSpreadsheetFormula() {
        assertEquals("\"a,\"\"b\"\"\nnext\"", CoreTransactionCsv.quote("a,\"b\"\nnext"))
        listOf("=SUM(A1)", "+1+1", "-CMD", "@SUM(A1)", "  =cmd", "\t=cmd", "\r=cmd").forEach { assertTrue(CoreTransactionCsv.quote(it).startsWith("\"'")) }
        assertEquals("\"-12.340\"", CoreTransactionCsv.quote("-12.340", protectFormula = false))
    }
    @Test fun everyPageIsExportedWithBothTransferSidesAndAttachmentReferences() = runTest {
        val rows = (1..451).map { csvTransaction(it) }
        val pages = mutableListOf<UInt>()
        val api = object : Ledger(NoPointer) {
            override suspend fun list(filter: TransactionFilter, page: PageRequest): TransactionPage {
                pages += page.offset
                return TransactionPage(rows.drop(page.offset.toInt()).take(page.limit.toInt()), rows.size.toUInt())
            }
        }
        val output = ByteArrayOutputStream()
        try {
            assertEquals(451u, CoreTransactionCsv.export(api, CoreTransactionQuery().toFilter(), output, emptyList(), emptyList()))
            assertEquals(listOf(0u, 200u, 400u), pages)
            val csv = output.toString(Charsets.UTF_8)
            assertTrue(csv.startsWith("\uFEFF" + CoreTransactionCsv.HEADER))
            assertTrue(csv.contains("\"to/账户\""))
            assertTrue(csv.contains("\"1.25\",\"USD\""))
            val firstRow = firstCsvRecord(csv.removePrefix("\uFEFF").substringAfter("\r\n"))
            assertEquals("receipts/fixture.png", org.json.JSONArray(firstRow[19]).getString(0))
            assertEquals("to/账户", firstRow[7])
            assertEquals("1.25", firstRow[9])
            assertEquals("two\nlines", firstRow[16])
            assertTrue(csv.contains("\"451\""))
        } finally { api.destroy() }
    }
    @Test fun changingTotalsRejectsPartialExportInsteadOfSilentlySucceeding() = runTest {
        var calls = 0
        val api = object : Ledger(NoPointer) {
            override suspend fun list(filter: TransactionFilter, page: PageRequest) =
                TransactionPage(listOf(csvTransaction(++calls)), if (calls == 1) 2u else 3u)
        }
        try {
            try { CoreTransactionCsv.export(api, CoreTransactionQuery().toFilter(), ByteArrayOutputStream(), emptyList(), emptyList()); fail("changed total accepted") }
            catch (_: IllegalStateException) {}
        } finally { api.destroy() }
    }
    @Test fun repeatedTransactionIdsRejectUnstablePaging() = runTest {
        val api = object : Ledger(NoPointer) {
            override suspend fun list(filter: TransactionFilter, page: PageRequest) = TransactionPage(listOf(csvTransaction(1)), 2u)
        }
        try {
            try { CoreTransactionCsv.export(api, CoreTransactionQuery().toFilter(), ByteArrayOutputStream(), emptyList(), emptyList()); fail("duplicate page accepted") }
            catch (_: IllegalStateException) {}
        } finally { api.destroy() }
    }
    @Test fun revisionChangeBeforeExportRejectsTheRequest() = runTest {
        val api = object : Ledger(NoPointer) {
            override suspend fun list(filter: TransactionFilter, page: PageRequest): TransactionPage = error("must not query stale revision")
        }
        try {
            try { CoreTransactionCsv.export(api, CoreTransactionQuery().toFilter(), ByteArrayOutputStream(), emptyList(), emptyList()) { false }; fail("stale revision accepted") }
            catch (_: IllegalStateException) {}
        } finally { api.destroy() }
    }
}

/** RFC-4180 test reader verifies semantic fields, not an implementation-specific JSON slash spelling. */
private fun firstCsvRecord(csv: String): List<String> {
    val fields = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var index = 0
    while (index < csv.length) {
        val char = csv[index]
        when {
            char == '"' && quoted && csv.getOrNull(index + 1) == '"' -> { field.append('"'); index++ }
            char == '"' -> quoted = !quoted
            char == ',' && !quoted -> { fields += field.toString(); field.setLength(0) }
            char == '\r' && !quoted -> { fields += field.toString(); return fields }
            else -> field.append(char)
        }
        index++
    }
    fields += field.toString()
    return fields
}

private fun csvTransaction(index: Int) = Transaction(index.toString(), TransactionKind.TRANSFER, BigDecimal("10.25"), "CNY",
    LocalDateTime(2026, 10u, 11u, 12u, 0u, 0u, 0u), "source", "to/账户", "USD", BigDecimal("1.25"),
    null, null, "=merchant,\"fixture\"", "two\nlines", null, null, listOf("receipts/fixture.png"), false,
    LocalDateTime(2026, 10u, 11u, 12u, 0u, 0u, 0u), LocalDateTime(2026, 10u, 11u, 12u, 0u, 0u, 0u))
