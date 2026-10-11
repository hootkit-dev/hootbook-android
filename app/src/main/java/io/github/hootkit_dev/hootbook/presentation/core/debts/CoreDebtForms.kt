package io.github.hootkit_dev.hootbook.presentation.core.debts

import dev.hootkit.core.*
import java.math.BigDecimal
import java.time.LocalDate as JavaDate
import java.time.LocalDateTime as JavaDateTime

internal fun JavaDate.toDebtDate() = LocalDate(year, monthValue.toUByte(), dayOfMonth.toUByte())
internal fun JavaDateTime.toDebtDateTime() = LocalDateTime(year, monthValue.toUByte(), dayOfMonth.toUByte(), hour.toUByte(), minute.toUByte(), second.toUByte(), nano.toUInt())
internal fun LocalDateTime.debtText() = JavaDateTime.of(year, month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), second.toInt(), nanosecond.toInt()).toString()
internal fun LocalDate.debtText() = JavaDate.of(year, month.toInt(), day.toInt()).toString()

/** Input-only state. Avatar and attachment paths are retained, never silently erased. */
data class CorePersonForm(
    val id: String? = null, val name: String = "", val phone: String = "", val notes: String = "",
    val color: String = "#6750A4", val avatar: String? = null, val relation: PersonRelation? = null,
) {
    fun toDraft() = PersonDraft(name, phone.ifBlank { null }, notes.ifBlank { null }, color, avatar, relation)
    companion object {
        fun from(person: Person) = CorePersonForm(person.id, person.name, person.phone.orEmpty(), person.notes.orEmpty(), person.color, person.avatar, person.relation)
    }
}

data class CoreDebtForm(
    val id: String? = null, val personId: String, val kind: DebtKind = DebtKind.LENT,
    val amount: String = "", val currency: String = "", val date: String = JavaDateTime.now().withNano(0).toString(),
    val dueDate: String = "", val title: String = "", val accountId: String? = null,
    val attachments: List<String> = emptyList(),
) {
    fun toDraft() = DebtDraft(personId, kind, BigDecimal(amount.trim()), currency.trim().ifBlank { null },
        JavaDateTime.parse(date.trim()).toDebtDateTime(), dueDate.trim().takeIf(String::isNotEmpty)?.let { JavaDate.parse(it).toDebtDate() },
        title, accountId, attachments)
    companion object {
        fun from(debt: DebtEntry) = CoreDebtForm(debt.id, debt.personId, debt.kind, debt.amount.amount.toPlainString(), debt.amount.currency,
            debt.date.debtText(), debt.dueDate?.debtText().orEmpty(), debt.title, debt.accountId, debt.attachments)
    }
}

data class CoreSettlementForm(
    val personId: String, val fullAmount: Boolean = true, val amount: String = "", val currency: String,
    val accountId: String? = null, val title: String, val date: String = JavaDateTime.now().withNano(0).toString(),
) {
    // Net direction, amount conversion and validation all belong to Core.
    fun toDraft() = SettleDraft(if (fullAmount) null else Money(BigDecimal(amount.trim()), currency.trim()), accountId,
        title, JavaDateTime.parse(date.trim()).toDebtDateTime())
}
