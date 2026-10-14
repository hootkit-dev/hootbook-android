package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import dev.hootkit.core.*
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger

internal open class FakeAccountsCore : HootKitCore(NoPointer) {
    val now = LocalDateTime(2026, 10u, 11u, 12u, 0u, 0u, 0u)
    var account = Account("opaque/中文?&=", "Wallet", null, AccountKind.WALLET, "GBP",
        listOf(Pocket("GBP", BigDecimal("10"), null, null, null), Pocket("USD", BigDecimal("20"), null, null, null)),
        Money(BigDecimal("123.45"), "GBP"), false, null, null, null, null, null, null,
        "type_finance_bank", "#33B5E5", false, false, 0, now)
    var connections = emptyList<BrokerageConnection>()
    var overview = AccountsOverview(listOf(account), Money(BigDecimal("987.65"), "GBP"), false, null, null)
    var onOverview: suspend () -> AccountsOverview = { overview }
    var onGet: suspend (String) -> Account = { account.copy(id = it) }
    var onSeries: suspend (String) -> List<BalanceDay> = { listOf(BalanceDay(LocalDate(2026, 10u, 11u), BigDecimal("555"))) }
    var anchors = listOf(BalanceAnchor("anchor/中文", now, BigDecimal("111"), AnchorSource.MANUAL, null, BigDecimal("4")))
    var onCommand: suspend () -> Unit = {}
    var creates = 0
    var calibrations = 0
    var anchorUpdates = 0
    var anchorDeletions = 0
    var newAccount: NewAccount? = null
    var patch: Pair<String, AccountPatch>? = null
    var addedPockets = mutableListOf<Pair<String, String>>()
    var defaultId: String? = "initial"
    var deleted: Pair<String, Boolean>? = null
    var lastCalibration: Triple<String, String, BigDecimal>? = null
    var lastAnchorPatch: Pair<String, AnchorPatch>? = null
    var onList: suspend (TransactionFilter, PageRequest) -> TransactionPage = { _, _ -> TransactionPage(emptyList(), 0u) }
    var onSummary: suspend (TransactionFilter) -> LedgerSummary = { summary }
    var summary = LedgerSummary("GBP", BigDecimal("300"), BigDecimal("11"), BigDecimal("289"),
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false,
        listOf("USD"), emptyList(), emptyList(), emptyList())
    val transaction = Transaction("tx", TransactionKind.EXPENSE, BigDecimal("7"), "GBP", now, account.id,
        null, null, null, null, null, "Merchant", null, null, null, null, emptyList(), null, emptyList(), false, now, now)
    var institutions = listOf(Institution("bank", "银行", "Bank", listOf("alias"), InstitutionKind.BANK, "GB", "GBP", "type_finance_bank", "#123456"))
    val opened = AtomicInteger()
    val closed = AtomicInteger()
    override fun brokerage(): Brokerage {
        opened.incrementAndGet()
        return object : Brokerage(NoPointer) {
            override suspend fun connections() = this@FakeAccountsCore.connections
            override fun destroy() { closed.incrementAndGet(); super.destroy() }
        }
    }
    override fun accounts(): Accounts {
        opened.incrementAndGet()
        return object : Accounts(NoPointer) {
            override suspend fun overview() = onOverview()
            override suspend fun get(accountId: String) = onGet(accountId)
            override suspend fun transactionCount(accountId: String, currency: String?) = 0u
            override suspend fun anchors(accountId: String, currency: String) = this@FakeAccountsCore.anchors
            override suspend fun balanceSeries(accountId: String, currency: String, from: LocalDate, to: LocalDate) = onSeries(currency)
            override suspend fun netWorthHistory(from: LocalDate, to: LocalDate, step: HistoryStep) = NetWorthHistory("GBP", emptyList(), emptyList(), null, null)
            override suspend fun cardStatus(accountId: String, today: LocalDate): CardStatus? = null
            override suspend fun create(input: NewAccount): String { creates++; newAccount = input; onCommand(); return "created/中文" }
            override suspend fun update(accountId: String, patch: AccountPatch) { this@FakeAccountsCore.patch = accountId to patch; onCommand() }
            override suspend fun addCurrency(accountId: String, currency: String, openingBalance: BigDecimal?, openingAt: LocalDateTime?) { addedPockets += accountId to currency; onCommand() }
            override suspend fun delete(accountId: String, deleteTransactions: Boolean) { deleted = accountId to deleteTransactions; onCommand() }
            override suspend fun calibrate(accountId: String, currency: String, balance: BigDecimal, at: LocalDateTime?): String {
                calibrations++; lastCalibration = Triple(accountId, currency, balance); onCommand(); return "anchor"
            }
            override suspend fun updateAnchor(anchorId: String, patch: AnchorPatch) { anchorUpdates++; lastAnchorPatch = anchorId to patch; onCommand() }
            override suspend fun deleteAnchor(anchorId: String) { anchorDeletions++; onCommand() }
            override fun destroy() { closed.incrementAndGet(); super.destroy() }
        }
    }
    override fun settings(): Settings {
        opened.incrementAndGet()
        return object : Settings(NoPointer) {
            override suspend fun mainCurrency() = "GBP"
            override suspend fun setDefaultAccount(accountId: String?) { defaultId = accountId; onCommand() }
            override fun destroy() { closed.incrementAndGet(); super.destroy() }
        }
    }
    override fun catalog(): Catalog {
        opened.incrementAndGet()
        return object : Catalog(NoPointer) {
            override fun all() = institutions
            override fun destroy() { closed.incrementAndGet(); super.destroy() }
        }
    }
    override fun currency(): Currency {
        opened.incrementAndGet()
        return object : Currency(NoPointer) {
            override suspend fun customCurrencies() = emptyList<CustomCurrency>()
            override suspend fun convert(amount: BigDecimal, from: String, to: String): BigDecimal? = BigDecimal("42")
            override fun destroy() { closed.incrementAndGet(); super.destroy() }
        }
    }
    override fun ledger(): Ledger {
        opened.incrementAndGet()
        return object : Ledger(NoPointer) {
            override suspend fun list(filter: TransactionFilter, page: PageRequest) = onList(filter, page)
            override suspend fun summary(filter: TransactionFilter) = onSummary(filter)
            override fun destroy() { closed.incrementAndGet(); super.destroy() }
        }
    }
    override fun categories(): Categories {
        opened.incrementAndGet()
        return object : Categories(NoPointer) {
            override suspend fun list(kind: CategoryKind?, includeHidden: Boolean) = emptyList<Category>()
            override fun destroy() { closed.incrementAndGet(); super.destroy() }
        }
    }
    override fun plans(): Plans {
        opened.incrementAndGet()
        return object : Plans(NoPointer) {
            override suspend fun people(includeArchived: Boolean, today: LocalDate) = emptyList<Person>()
            override fun destroy() { closed.incrementAndGet(); super.destroy() }
        }
    }
}
