package io.github.hootkit_dev.hootbook.presentation.core.plans

import dev.hootkit.core.*
import java.math.BigDecimal
import java.time.LocalDate as JavaDate

internal fun JavaDate.corePlanDate() = LocalDate(year, monthValue.toUByte(), dayOfMonth.toUByte())
internal fun LocalDate.planDateText() = JavaDate.of(year, month.toInt(), day.toInt()).toString()
internal fun optionalPlanDate(text: String) = text.trim().takeIf(String::isNotEmpty)?.let { JavaDate.parse(it).corePlanDate() }
internal fun planText(text: String) = text.trim().takeIf(String::isNotEmpty)

/** Only editable input and display choices. Core owns all cycle progression and money calculations. */
data class CoreSubscriptionForm(
    val id: String? = null,
    val name: String = "",
    val amount: String = "",
    val currency: String = "CNY",
    val every: String = "1",
    val unit: CycleUnit = CycleUnit.MONTH,
    val endDate: String = "",
    val nextDue: String = JavaDate.now().toString(),
    val accountId: String? = null,
    val categoryId: String? = null,
    val subcategoryId: String? = null,
    val notes: String = "",
) {
    fun toDraft() = SubscriptionDraft(name.trim(), Money(BigDecimal(amount.trim()), currency.trim()),
        Cycle(every.trim().toUInt(), unit), optionalPlanDate(endDate), optionalPlanDate(nextDue),
        accountId, categoryId, subcategoryId, planText(notes))
    companion object {
        fun from(row: Subscription) = CoreSubscriptionForm(row.id, row.name, row.amount.amount.toPlainString(),
            row.amount.currency, row.cycle.every.toString(), row.cycle.unit, row.endDate?.planDateText().orEmpty(),
            row.nextDue?.planDateText().orEmpty(), row.accountId, row.categoryId, row.subcategoryId, row.notes.orEmpty())
    }
}

data class CoreTemplateForm(
    val id: String? = null,
    val name: String = "",
    val kind: TransactionKind = TransactionKind.EXPENSE,
    val merchant: String = "",
    val categoryId: String? = null,
    val subcategoryId: String? = null,
    val amount: String = "",
    val prefillAmount: Boolean = false,
    val accountId: String? = null,
    val currency: String = "",
    val notes: String = "",
) {
    fun toDraft() = QuickTemplateDraft(name.trim(), kind, merchant.trim(), categoryId, subcategoryId,
        planText(amount)?.let(::BigDecimal), prefillAmount, accountId, planText(currency), planText(notes))
    companion object {
        fun from(row: QuickTemplate) = CoreTemplateForm(row.id, row.name, row.kind, row.merchant, row.categoryId,
            row.subcategoryId, row.amount?.toPlainString().orEmpty(), row.prefillAmount, row.accountId,
            row.currency.orEmpty(), row.notes.orEmpty())
        fun from(row: TemplateSuggestion) = CoreTemplateForm(name = row.name, kind = row.kind, merchant = row.merchant,
            categoryId = row.categoryId, subcategoryId = row.subcategoryId, amount = row.amount?.toPlainString().orEmpty(),
            prefillAmount = row.amount != null, accountId = row.accountId, currency = row.currency.orEmpty())
    }
}

/** Generated records only; no shadow account/category model or old ID lookup. */
data class CorePlanReferences(
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val mainCurrency: String = "CNY",
)
internal suspend fun HootKitCoreInterface.planReferences() = CorePlanReferences(
    accounts().use { it.overview().accounts }, categories().use { it.list(null, true) },
    settings().use { it.mainCurrency() },
)
internal fun moveTemplateIds(rows: List<QuickTemplate>, id: String, direction: Int): List<String>? {
    val ids = rows.map { it.id }.toMutableList()
    val source = ids.indexOf(id)
    val target = source + direction
    if (source < 0 || target !in ids.indices) return null
    val other = ids[target]; ids[target] = id; ids[source] = other
    return ids
}
