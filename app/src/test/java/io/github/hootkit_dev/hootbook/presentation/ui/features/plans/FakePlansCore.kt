package io.github.hootkit_dev.hootbook.presentation.ui.features.plans

import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.FakeAccountsCore
import java.math.BigDecimal

internal class FakePlansCore : FakeAccountsCore() {
    val today = LocalDate(2026, 10u, 11u)
    val progress = BudgetProgress(today, LocalDate(2026, 11u, 10u), 31u, 21u, BigDecimal("99"), BigDecimal("401"), false,
        BigDecimal("19.09"), BigDecimal("3.19"), false, listOf("EUR"), emptyList(), emptyList())
    var budget = Budget("budget/中文", "Budget", BudgetKind.EXPENSE, BudgetPeriod.MONTHLY, Money(BigDecimal("500"), "GBP"),
        LocalDate(2020, 1u, 1u), null, null, true, "#4CAF50", progress,
        BudgetDisplay("GBP", BigDecimal("500"), BigDecimal("99"), BigDecimal("401"), emptyList(), emptyList()), now, now)
    var subscription = Subscription("sub/中文", "Streaming", Money(BigDecimal("4"), "EUR"), Cycle(3u, CycleUnit.MONTH),
        null, today, null, account.id, null, null, "Notes", false, BigDecimal("1.33"), false, true, -8)
    var subscriptionSummary = SubscriptionSummary("GBP", BigDecimal("55"), BigDecimal("1234"), BigDecimal("12"), 7u, false, listOf("EUR"))
    var template = QuickTemplate("template/中文", "Lunch", TransactionKind.EXPENSE, "Shop", null, null,
        BigDecimal("12"), false, account.id, "GBP", "Note", 0)
    var rows = listOf(template, template.copy(id = "template-two", sortOrder = 1), template.copy(id = "template-three", sortOrder = 2))
    var person = Person("person/中文", "Alice", "123", "Notes", "#4CAF50", null, PersonRelation.FRIEND, false,
        PersonTotals("GBP", BigDecimal("999"), BigDecimal("111"), BigDecimal("888"), true, false, listOf("EUR")), now)
    var people = listOf(person, person.copy(id = "bob", name = "Bob", relation = PersonRelation.FAMILY,
        totals = person.totals.copy(net = BigDecimal("-33"), overdue = false)))
    var debt = DebtEntry("debt/中文", person.id, DebtKind.LENT, Money(BigDecimal("55"), "EUR"), now, today,
        "Lunch", "tx", account.id, listOf("attachment.jpg"))
    var debts = listOf(debt)
    var debtSummary = DebtSummary("GBP", BigDecimal("8765"), BigDecimal("123"), BigDecimal("8642"), 7u, 3u, 4u, 2u, false)
    var onBudgets: suspend () -> List<Budget> = { listOf(budget) }
    var onBudget: suspend (String, LocalDate) -> Budget = { id, date -> budget.copy(id = id, current = budget.current.copy(start = date)) }
    var onHistory: suspend (LocalDate) -> List<BudgetPeriodSummary> = { date -> listOf(BudgetPeriodSummary(date, date, BigDecimal("50"), BigDecimal("12"), false, false)) }
    var onSubscriptions: suspend () -> List<Subscription> = { listOf(subscription).filterNot { it.hidden } }
    var onPeople: suspend () -> List<Person> = { people }
    var onPerson: suspend () -> Person = { person }
    var onTemplates: suspend () -> List<QuickTemplate> = { rows }
    var historyRequests = mutableListOf<LocalDate>()
    var commandCalls = 0
    var newBudget: NewBudget? = null
    var budgetPatch: BudgetPatch? = null
    var debtDraft: DebtDraft? = null
    var settleDraft: SettleDraft? = null
    var templateDraft: QuickTemplateDraft? = null
    var personDraft: PersonDraft? = null
    var hiddenWrites = mutableListOf<Pair<String, Boolean>>()
    var reorderWrites = mutableListOf<List<String>>()
    private suspend fun write() { commandCalls++; onCommand() }
    override fun budgets(): Budgets {
        opened.incrementAndGet()
        return object : Budgets(NoPointer) {
            override suspend fun list(today: LocalDate, homeOnly: Boolean, displayCurrency: String?) = onBudgets()
            override suspend fun get(budgetId: String, today: LocalDate, displayCurrency: String?) = onBudget(budgetId, today)
            override suspend fun history(budgetId: String, today: LocalDate, max: UInt): List<BudgetPeriodSummary> { historyRequests += today; return onHistory(today) }
            override suspend fun create(input: NewBudget): String { newBudget = input; write(); return "created" }
            override suspend fun update(budgetId: String, patch: BudgetPatch) { budgetPatch = patch; write() }
            override suspend fun setLimit(budgetId: String, categoryId: String, amount: BigDecimal?) { write() }
            override suspend fun delete(budgetId: String) { write() }
            override fun destroy() { closed.incrementAndGet(); super.destroy() }
        }
    }
    override fun plans(): Plans {
        opened.incrementAndGet()
        return object : Plans(NoPointer) {
            override suspend fun subscriptions(today: LocalDate, includeHidden: Boolean) = onSubscriptions()
            override suspend fun subscriptionSummary(today: LocalDate) = this@FakePlansCore.subscriptionSummary
            override suspend fun setSubscriptionHidden(subscriptionId: String, hidden: Boolean) {
                hiddenWrites += subscriptionId to hidden; write(); subscription = subscription.copy(hidden = hidden)
            }
            override suspend fun markSubscriptionPaid(subscriptionId: String, today: LocalDate) { write() }
            override suspend fun templates() = onTemplates()
            override suspend fun updateTemplate(templateId: String, draft: QuickTemplateDraft) { templateDraft = draft; write() }
            override suspend fun deleteTemplate(templateId: String) { write() }
            override suspend fun reorderTemplates(templateIds: List<String>) { reorderWrites += templateIds; write() }
            override suspend fun people(includeArchived: Boolean, today: LocalDate) = onPeople()
            override suspend fun person(personId: String, today: LocalDate) = onPerson()
            override suspend fun debtSummary(today: LocalDate) = this@FakePlansCore.debtSummary
            override suspend fun debts(personId: String) = this@FakePlansCore.debts
            override suspend fun createPerson(draft: PersonDraft): String { personDraft = draft; write(); return "created" }
            override suspend fun updatePerson(personId: String, draft: PersonDraft) { personDraft = draft; write() }
            override suspend fun deletePerson(personId: String) { write() }
            override suspend fun addDebt(draft: DebtDraft): String { debtDraft = draft; write(); return "created" }
            override suspend fun updateDebt(debtId: String, draft: DebtDraft) { debtDraft = draft; write() }
            override suspend fun deleteDebt(debtId: String) { write() }
            override suspend fun settle(personId: String, draft: SettleDraft): String { settleDraft = draft; write(); return "settled" }
            override fun destroy() { closed.incrementAndGet(); super.destroy() }
        }
    }
    override fun ledger(): Ledger {
        opened.incrementAndGet()
        return object : Ledger(NoPointer) {
            override suspend fun get(transactionId: String) = transaction.copy(id = transactionId)
            override fun destroy() { closed.incrementAndGet(); super.destroy() }
        }
    }
}
