package io.github.hootkit_dev.hootbook.data.brokerage

import io.github.hootkit_dev.hootbook.domain.brokerage.*
import java.io.StringReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

/** Parses only summary positions. Lots/totals must never be counted a second time. */
class IbkrFlexParser @Inject constructor() {
    internal fun root(xml: String): Element {
        if (xml.length > MAX_REPORT_BYTES || Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(xml)) invalid()
        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isExpandEntityReferences = false
                // Android and the JVM expose different feature sets; the explicit declaration
                // rejection above and resolver below also forbid all DTD/entity access.
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            return factory.newDocumentBuilder().apply {
                setEntityResolver { _, _ -> throw SAXException("External entity disabled") }
                setErrorHandler(DefaultHandler())
            }.parse(InputSource(StringReader(xml))).documentElement
        } catch (_: Exception) {
            invalid()
        }
    }

    fun reference(xml: String): String {
        val root = root(xml)
        if (root.tagName != "FlexStatementResponse") invalid()
        checkError(root)
        if (root.text("Status") != "Success") invalid()
        return root.text("ReferenceCode").takeIf { it.matches(Regex("[0-9]{1,128}")) } ?: invalid()
    }

    fun parse(xml: String): List<BrokerageAccount> {
        val root = root(xml)
        if (root.tagName == "FlexStatementResponse") {
            checkError(root)
            invalid()
        }
        if (root.tagName != "FlexQueryResponse") invalid()
        val statements = root.children("FlexStatements").singleOrNull()?.children("FlexStatement") ?: invalid()
        if (statements.isEmpty()) invalid()
        return statements.map { statement ->
            val account = statement.required("accountId")
            val statementDate = date(statement.required("toDate"))
            val sections = statement.children("OpenPositions")
            if (sections.size != 1) throw BrokerageException(BrokerageError.INVALID_QUERY)
            val rows = sections.single().children("OpenPosition")
            val summaries = rows.filter { it.getAttribute("levelOfDetail").equals("SUMMARY", ignoreCase = true) }
            if (rows.isNotEmpty() && summaries.isEmpty()) throw BrokerageException(BrokerageError.INVALID_QUERY)
            val dates = summaries.map { date(it.getAttribute("reportDate").ifBlank { statementDate }) }
            val asOf = dates.maxOrNull() ?: statementDate
            val positions = summaries.zip(dates).filter { it.second == asOf }.map { (row, _) ->
                val rowAccount = row.getAttribute("accountId")
                if (rowAccount.isNotEmpty() && rowAccount != account) invalid()
                Holding(
                    instrumentId = row.required("conid"),
                    symbol = row.required("symbol"),
                    description = row.getAttribute("description").ifBlank { row.required("symbol") },
                    currency = row.required("currency").also { if (!it.matches(Regex("[A-Z]{3}"))) invalid() },
                    quantity = row.decimal("position") ?: invalid(),
                    // Use IB's reported value, not quantity * price (options have multipliers).
                    marketValue = row.decimal("positionValue"),
                    costBasis = row.decimal("costBasisMoney"),
                    unrealizedPnl = row.decimal("fifoPnlUnrealized"),
                    assetClass = row.getAttribute("assetCategory"),
                    model = row.getAttribute("model")
                )
            }
            val keys = positions.map { listOf(it.instrumentId, it.currency, it.model) }
            if (keys.distinct().size != keys.size) invalid()
            BrokerageAccount(
                accountId = account,
                asOf = asOf,
                holdings = positions.sortedWith(compareBy({ it.currency }, { it.symbol }, { it.model })),
                cashBalances = parseCash(statement, account)
            )
        }.groupBy { it.accountId }.map { (_, snapshots) ->
            val latest = snapshots.maxOf { it.asOf }
            snapshots.filter { it.asOf == latest }.singleOrNull() ?: invalid()
        }.sortedBy { it.accountId }
    }

    /**
     * Cash Report is optional so older queries still parse. BASE_SUMMARY is a
     * converted total, not a real currency, and must not be stored as cash.
     */
    private fun parseCash(statement: Element, account: String): List<CashBalance> {
        val section = statement.children("CashReport").singleOrNull() ?: return emptyList()
        val byCurrency = linkedMapOf<String, CashBalance>()
        for (row in section.children("CashReportCurrency")) {
            val currency = row.getAttribute("currency").trim()
            if (!currency.matches(Regex("[A-Z]{3}"))) continue
            val rowAccount = row.getAttribute("accountId")
            if (rowAccount.isNotEmpty() && rowAccount != account) invalid()
            val ending = row.decimal("endingCash") ?: continue
            byCurrency[currency] = CashBalance(
                currency = currency,
                endingCash = ending,
                endingSettledCash = row.decimal("endingSettledCash")
            )
        }
        return byCurrency.values.sortedBy { it.currency }
    }

    private fun checkError(root: Element) {
        val code = root.text("ErrorCode")
        if (code.isBlank()) return
        val error = when (code) {
            "1012" -> BrokerageError.EXPIRED_CREDENTIALS
            "1011", "1015", "1016" -> BrokerageError.INVALID_CREDENTIALS
            "1013" -> BrokerageError.IP_RESTRICTED
            "1010", "1014" -> BrokerageError.INVALID_QUERY
            "1018" -> BrokerageError.RATE_LIMITED
            "1001", "1003", "1004", "1005", "1006", "1007", "1008", "1009", "1019", "1021" -> BrokerageError.REPORT_NOT_READY
            else -> BrokerageError.INVALID_REPORT
        }
        throw BrokerageException(error)
    }

    private fun Element.children(name: String): List<Element> = buildList {
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element && child.tagName == name) add(child)
        }
    }
    private fun Element.text(name: String) = children(name).singleOrNull()?.textContent?.trim().orEmpty()
    private fun Element.required(name: String) = getAttribute(name).trim().takeIf { it.isNotEmpty() } ?: invalid()
    private fun Element.decimal(name: String): String? {
        val raw = getAttribute(name).trim()
        if (raw.isEmpty()) return null
        if (raw.length > 80 || !raw.matches(Regex("[+-]?[0-9]+(\\.[0-9]+)?"))) invalid()
        return raw.toBigDecimalOrNull()?.toPlainString() ?: invalid()
    }
    private fun date(raw: String): String = try {
        LocalDate.parse(raw, if (raw.contains('-')) DateTimeFormatter.ISO_LOCAL_DATE else DateTimeFormatter.BASIC_ISO_DATE).toString()
    } catch (_: Exception) { invalid() }
    private fun invalid(): Nothing = throw BrokerageException(BrokerageError.INVALID_REPORT)

    companion object { const val MAX_REPORT_BYTES = 5 * 1024 * 1024 }
}
