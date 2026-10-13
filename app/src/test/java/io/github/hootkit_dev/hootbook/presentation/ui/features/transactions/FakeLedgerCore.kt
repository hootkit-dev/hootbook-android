package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import dev.hootkit.core.*
import java.math.BigDecimal
import kotlinx.coroutines.test.*
import org.junit.Assert.*

class FakeLedgerCore : HootKitCore(NoPointer) {
    private val now = LocalDateTime(2026, 10u, 10u, 12u, 0u, 0u, 0u)
    var transaction =
        Transaction(
            "tx",
            TransactionKind.EXPENSE,
            BigDecimal("35"),
            "CNY",
            now,
            "account",
            null,
            null,
            null,
            "category",
            null,
            "商户",
            null,
            null,
            null,
            emptyList(),
            false,
            now,
            now,
        )
    var mainCurrency = "CNY"
    var onGet: suspend (String) -> Transaction = { transaction.copy(id = it) }
    var defaultAccountId: String? = null
    var onSettings: suspend () -> Unit = {}
    var onList: suspend (TransactionFilter, PageRequest) -> TransactionPage = { _, _ ->
        TransactionPage(listOf(transaction), 1u)
    }
    var onAdd: suspend (TransactionDraft) -> String = { "tx" }
    val subscription =
        Subscription(
            "sub/?中文&=",
            "会员",
            Money(BigDecimal("1500"), "JPY"),
            Cycle(1u, CycleUnit.MONTH),
            null,
            null,
            null,
            null,
            null,
            null,
            "订阅备注",
            false,
            null,
            false,
            false,
            null,
        )
    var onSubscriptions: suspend () -> List<Subscription> = { listOf(subscription) }
    var onTemplatePrefill: suspend (String) -> TemplatePrefill = {
        TemplatePrefill(TransactionKind.EXPENSE, it, null, null, null, null, null, null)
    }
    val subscriptionSummary =
        SubscriptionSummary(
            "CNY",
            BigDecimal("10"),
            BigDecimal("120"),
            BigDecimal("20"),
            1u,
            false,
            listOf("JPY"),
        )
    val debtSummary =
        DebtSummary("CNY", BigDecimal("8"), BigDecimal("3"), BigDecimal("5"), 2u, 1u, 1u, 1u, false)
    var onSubscriptionSummary: suspend () -> SubscriptionSummary = { subscriptionSummary }
    var planSummaryCalls = 0
    var budgetCalls = 0
    var paidCalls = 0
    var linkCalls = 0
    var linked: Triple<String, String, DebtKind?>? = null
    var onLink: suspend () -> Unit = {}
    var prefillCalls = 0
    var lastDraft: TransactionDraft? = null
    var onUndo: suspend (String) -> Unit = {}
    var lastBulk: Pair<List<String>, TransactionBulkPatch>? = null
    var purgedIds = emptyList<String>()
    var duplicateIds = emptyList<String>()
    var fromTransactionCalls = 0
    var createdSubscriptionFrom: Triple<String, Cycle, LocalDate?>? = null
    var symbols = emptyList<CustomCurrency>()
    var onConvert: suspend (BigDecimal, String, String) -> BigDecimal? = { amount, _, _ -> amount }
    var addCalls = 0
    var listCalls = 0
    var deleteCalls = 0
    var deletedIds = emptyList<String>()
    var undoneToken: String? = null
    var overview =
        AccountsOverview(emptyList(), Money(BigDecimal("999.99"), "CNY"), false, null, null)
    var summary =
        LedgerSummary(
            "CNY",
            BigDecimal("123"),
            BigDecimal("45"),
            BigDecimal("78"),
            false,
            listOf("USD"),
            emptyList(),
            emptyList(),
            emptyList(),
        )
    private val opened = java.util.concurrent.atomic.AtomicInteger()
    private val closed = java.util.concurrent.atomic.AtomicInteger()
    val openedHandles
        get() = opened.get()

    val closedHandles
        get() = closed.get()

    var coreClosed = false

    fun assertHandlesReleased() {
        assertEquals("every temporary service handle must close", openedHandles, closedHandles)
        assertFalse("the application-owned core must remain open", coreClosed)
    }

    var onEdit: suspend (String, TransactionDraft) -> Unit = { _, _ -> }
    var editCalls = 0
    var onTemplates: suspend () -> List<QuickTemplate> = { emptyList() }
    var onCreateTemplate: suspend () -> String = { "template" }
    var createTemplateCalls = 0
    var createSubscriptionCalls = 0
    var updatedSubscription: SubscriptionDraft? = null
    var hiddenSubscription: Pair<String, Boolean>? = null
    var onDelete: suspend () -> Unit = {}
    var onPeople: suspend () -> List<Person> = { emptyList() }
    var onCreatePerson: suspend (PersonDraft) -> String = { "created-person" }
    var createdPerson: PersonDraft? = null
    var createPersonCalls = 0
    var onDebts: suspend (String) -> List<DebtEntry> = { emptyList() }
    var unlinkedDebt: String? = null
    var categoryRows: List<Category> = emptyList()

    override fun accounts(): Accounts {
        opened.incrementAndGet()
        return object : Accounts(NoPointer) {
            override suspend fun overview() = this@FakeLedgerCore.overview

            private var released = false

            // dev.hootkit.core.Disposable.use calls destroy(), not AutoCloseable.close().
            override fun destroy() {
                if (!released) {
                    released = true
                    closed.incrementAndGet()
                }
                super.destroy()
            }
        }
    }

    override fun categories(): Categories {
        opened.incrementAndGet()
        return object : Categories(NoPointer) {
            override suspend fun list(kind: CategoryKind?, includeHidden: Boolean) = categoryRows

            private var released = false

            // dev.hootkit.core.Disposable.use calls destroy(), not AutoCloseable.close().
            override fun destroy() {
                if (!released) {
                    released = true
                    closed.incrementAndGet()
                }
                super.destroy()
            }
        }
    }

    override fun settings(): Settings {
        opened.incrementAndGet()
        return object : Settings(NoPointer) {
            override suspend fun mainCurrency(): String {
                onSettings()
                return this@FakeLedgerCore.mainCurrency
            }

            override suspend fun defaultAccount(): String? = defaultAccountId

            private var released = false

            // dev.hootkit.core.Disposable.use calls destroy(), not AutoCloseable.close().
            override fun destroy() {
                if (!released) {
                    released = true
                    closed.incrementAndGet()
                }
                super.destroy()
            }
        }
    }

    override fun ledger(): Ledger {
        opened.incrementAndGet()
        return object : Ledger(NoPointer) {
            override suspend fun list(
                filter: TransactionFilter,
                page: PageRequest,
            ): TransactionPage {
                listCalls++
                return onList(filter, page)
            }

            override suspend fun get(transactionId: String) = onGet(transactionId)

            override suspend fun summary(filter: TransactionFilter) = this@FakeLedgerCore.summary

            override suspend fun add(draft: TransactionDraft): String {
                addCalls++
                lastDraft = draft
                return onAdd(draft)
            }

            override suspend fun edit(transactionId: String, draft: TransactionDraft) {
                editCalls++
                lastDraft = draft
                onEdit(transactionId, draft)
            }

            override suspend fun delete(transactionIds: List<String>): String {
                deleteCalls++
                onDelete()
                deletedIds = transactionIds
                return "opaque-core-token"
            }

            override suspend fun bulkUpdate(
                transactionIds: List<String>,
                patch: TransactionBulkPatch,
            ) {
                lastBulk = transactionIds to patch
            }

            override suspend fun purge(transactionIds: List<String>) {
                purgedIds = transactionIds
            }

            override suspend fun findDuplicates(draft: TransactionDraft) = duplicateIds

            override suspend fun merchants(prefix: String, limit: UInt) = emptyList<String>()

            override suspend fun undo(undoToken: String) {
                onUndo(undoToken)
                undoneToken = undoToken
            }

            private var released = false

            // dev.hootkit.core.Disposable.use calls destroy(), not AutoCloseable.close().
            override fun destroy() {
                if (!released) {
                    released = true
                    closed.incrementAndGet()
                }
                super.destroy()
            }
        }
    }

    override fun currency(): dev.hootkit.core.Currency {
        opened.incrementAndGet()
        return object : dev.hootkit.core.Currency(NoPointer) {
            override suspend fun customCurrencies() = symbols

            override suspend fun convert(amount: BigDecimal, from: String, to: String) =
                onConvert(amount, from, to)

            private var released = false

            override fun destroy() {
                if (!released) {
                    released = true
                    closed.incrementAndGet()
                }
                super.destroy()
            }
        }
    }

    override fun budgets(): Budgets {
        opened.incrementAndGet()
        return object : Budgets(NoPointer) {
            override suspend fun list(
                today: LocalDate,
                homeOnly: Boolean,
                displayCurrency: String?,
            ): List<Budget> {
                budgetCalls++
                assertTrue(homeOnly)
                return emptyList()
            }

            private var released = false

            override fun destroy() {
                if (!released) {
                    released = true
                    closed.incrementAndGet()
                }
                super.destroy()
            }
        }
    }

    override fun plans(): Plans {
        opened.incrementAndGet()
        return object : Plans(NoPointer) {
            override suspend fun people(includeArchived: Boolean, today: LocalDate) = onPeople()
            override suspend fun createPerson(draft: PersonDraft): String {
                createPersonCalls++
                createdPerson = draft
                return onCreatePerson(draft)
            }

            override suspend fun templates() = onTemplates()

            override suspend fun templateSuggestions(today: LocalDate) =
                emptyList<TemplateSuggestion>()

            override suspend fun createTemplate(draft: QuickTemplateDraft): String {
                createTemplateCalls++
                return onCreateTemplate()
            }

            override suspend fun createSubscription(draft: SubscriptionDraft): String {
                createSubscriptionCalls++
                updatedSubscription = draft
                return "created-sub"
            }

            override suspend fun updateSubscription(
                subscriptionId: String,
                draft: SubscriptionDraft,
            ) {
                updatedSubscription = draft
            }

            override suspend fun setSubscriptionHidden(subscriptionId: String, hidden: Boolean) {
                hiddenSubscription = subscriptionId to hidden
            }

            override suspend fun debts(personId: String) = onDebts(personId)

            override suspend fun unlinkDebt(debtId: String) {
                unlinkedDebt = debtId
            }

            override suspend fun linkTransaction(
                transactionId: String,
                personId: String,
                kind: DebtKind?,
            ): String {
                linkCalls++
                linked = Triple(transactionId, personId, kind)
                onLink()
                return "debt-link"
            }

            override suspend fun subscriptions(
                today: LocalDate,
                includeHidden: Boolean,
            ): List<Subscription> {
                assertTrue(includeHidden)
                return onSubscriptions()
            }

            override suspend fun templatePrefill(templateId: String): TemplatePrefill {
                prefillCalls++
                return onTemplatePrefill(templateId)
            }

            override suspend fun subscriptionSummary(today: LocalDate): SubscriptionSummary {
                planSummaryCalls++
                return onSubscriptionSummary()
            }

            override suspend fun debtSummary(today: LocalDate) = this@FakeLedgerCore.debtSummary

            override suspend fun subscriptionFromTransaction(
                transactionId: String,
                cycle: Cycle,
                endDate: LocalDate?,
            ): String {
                fromTransactionCalls++
                createdSubscriptionFrom = Triple(transactionId, cycle, endDate)
                return "subscription-created"
            }

            override suspend fun markSubscriptionPaid(subscriptionId: String, today: LocalDate) {
                paidCalls++
            }

            private var released = false

            override fun destroy() {
                if (!released) {
                    released = true
                    closed.incrementAndGet()
                }
                super.destroy()
            }
        }
    }

    override fun destroy() {
        coreClosed = true
        super.destroy()
    }

    override suspend fun shutdown() = Unit
}
