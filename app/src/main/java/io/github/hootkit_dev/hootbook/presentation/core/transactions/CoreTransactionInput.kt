package io.github.hootkit_dev.hootbook.presentation.core.transactions

import dev.hootkit.core.*
import java.math.BigDecimal
import java.time.LocalDateTime as JavaDateTime
import java.time.LocalDate as JavaDate

/** Parsing is an input boundary only. Core owns validation and all ledger calculations. */
internal fun JavaDateTime.toCoreDateTime() = LocalDateTime(
    year, monthValue.toUByte(), dayOfMonth.toUByte(), hour.toUByte(), minute.toUByte(), second.toUByte(), nano.toUInt(),
)
internal fun LocalDateTime.inputText(): String = JavaDateTime.of(
    year, month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), second.toInt(), nanosecond.toInt(),
).toString()
internal fun parseCoreDateTime(text: String): LocalDateTime = JavaDateTime.parse(text.trim()).toCoreDateTime()
internal fun optionalId(text: String) = text.takeIf { it.isNotBlank() }

fun TransactionKind.chineseLabel(): String = when (this) {
    TransactionKind.EXPENSE -> "支出"
    TransactionKind.INCOME -> "收入"
    TransactionKind.TRANSFER -> "转账"
    TransactionKind.INVESTMENT -> "投资"
    TransactionKind.LOAN_OUT -> "借出／还出"
    TransactionKind.LOAN_IN -> "借入／还入"
    TransactionKind.UNKNOWN -> "未知类型"
}

/** UI draft, not a shadow business model. IDs and preserved links remain Core IDs. */
data class CoreTransactionForm(
    val editingId: String? = null,
    val kind: TransactionKind = TransactionKind.EXPENSE,
    val amount: String = "",
    val currency: String = "CNY",
    val dateTime: String = JavaDateTime.now().withNano(0).toString(),
    val accountId: String? = null,
    val toAccountId: String? = null,
    val toCurrency: String = "",
    val toAmount: String = "",
    val categoryId: String? = null,
    val subcategoryId: String? = null,
    val merchant: String = "",
    val notes: String = "",
    val subscriptionId: String? = null,
    val attachments: List<String> = emptyList(),
) {
    fun toDraft(): TransactionDraft = TransactionDraft(
        kind = kind,
        amount = BigDecimal(amount.trim()),
        currency = currency.trim().ifBlank { null },
        dateTime = parseCoreDateTime(dateTime),
        accountId = accountId,
        toAccountId = toAccountId.takeIf { kind == TransactionKind.TRANSFER },
        toCurrency = toCurrency.trim().ifBlank { null }.takeIf { kind == TransactionKind.TRANSFER },
        // No local FX calculation: Core receives the user's explicit incoming amount.
        toAmount = toAmount.trim().takeIf { it.isNotEmpty() && kind == TransactionKind.TRANSFER }?.let(::BigDecimal),
        categoryId = categoryId.takeIf { kind == TransactionKind.EXPENSE || kind == TransactionKind.INCOME },
        subcategoryId = subcategoryId.takeIf { kind == TransactionKind.EXPENSE || kind == TransactionKind.INCOME },
        merchant = merchant,
        notes = notes.ifBlank { null },
        subscriptionId = subscriptionId,
        attachments = attachments,
    )

    /** Choosing an account is not an FX conversion of an already entered amount. */
    fun withAccount(id: String?, suggestedCurrency: String) = copy(accountId = id,
        currency = if (amount.isNotBlank()) currency else suggestedCurrency)

    /** Core template nulls clear labels, but retain optional numeric/account fields. */
    fun withTemplate(prefill: TemplatePrefill): CoreTransactionForm {
        check(editingId == null) { "模板仅用于新增交易" }
        return copy(kind = prefill.kind, merchant = prefill.merchant,
            categoryId = prefill.categoryId, subcategoryId = prefill.subcategoryId,
            notes = prefill.notes.orEmpty(), amount = prefill.amount?.toPlainString() ?: amount,
            accountId = prefill.accountId ?: accountId, currency = prefill.currency ?: currency)
    }

    /** Payment is one ledger command; Core performs subscription updates atomically. */
    fun withSubscription(subscription: Subscription): CoreTransactionForm {
        check(editingId == null) { "订阅付款仅用于新增交易" }
        return copy(kind = TransactionKind.EXPENSE, amount = subscription.amount.amount.toPlainString(),
            currency = subscription.amount.currency, accountId = subscription.accountId,
            categoryId = subscription.categoryId, subcategoryId = subscription.subcategoryId,
            merchant = subscription.name, notes = subscription.notes.orEmpty(), subscriptionId = subscription.id,
            toAccountId = null, toCurrency = "", toAmount = "")
    }

    companion object {
        fun from(transaction: Transaction) = CoreTransactionForm(
            editingId = transaction.id, kind = transaction.kind, amount = transaction.amount.toPlainString(),
            currency = transaction.currency, dateTime = transaction.dateTime.inputText(),
            accountId = transaction.accountId, toAccountId = transaction.toAccountId,
            toCurrency = transaction.toCurrency.orEmpty(), toAmount = transaction.toAmount?.toPlainString().orEmpty(),
            categoryId = transaction.categoryId, subcategoryId = transaction.subcategoryId,
            merchant = transaction.merchant, notes = transaction.notes.orEmpty(),
            subscriptionId = transaction.subscriptionId, attachments = transaction.attachments,
        )
    }
}

data class CoreTransactionQuery(
    val text: String = "",
    val currency: String = "",
    val accountId: String? = null,
    val categoryId: String? = null,
    val kind: TransactionKind? = null,
    val fromDate: String = "",
    val toDateExclusive: String = "",
    val deleted: DeletedFilter = DeletedFilter.EXCLUDE,
    val sort: SortOrder = SortOrder.NEWEST_FIRST,
    val accountIds: List<String> = emptyList(),
    val categoryIds: List<String> = emptyList(),
    val kinds: List<TransactionKind> = emptyList(),
) {
    fun toFilter() = TransactionFilter(
        from = fromDate.trim().takeIf(String::isNotEmpty)?.let { JavaDate.parse(it).atStartOfDay().toCoreDateTime() },
        to = toDateExclusive.trim().takeIf(String::isNotEmpty)?.let { JavaDate.parse(it).atStartOfDay().toCoreDateTime() },
        kinds = (kinds + listOfNotNull(kind)).distinct(), accountIds = (accountIds + listOfNotNull(accountId)).distinct(), categoryIds = (categoryIds + listOfNotNull(categoryId)).distinct(),
        text = text.trim().ifBlank { null }, currency = currency.trim().ifBlank { null }, deleted = deleted, sort = sort,
    )
}

data class CoreTransactionBulkForm(
    val date: String = "", val time: String = "", val amount: String = "",
    val categoryId: String? = null, val subcategoryId: String? = null,
    val replaceNotes: Boolean = false, val notes: String = "",
) {
    fun toPatch(): TransactionBulkPatch {
        val dateValue = date.trim().takeIf(String::isNotEmpty)?.let(JavaDate::parse)
        val timeValue = time.trim().takeIf(String::isNotEmpty)?.let(java.time.LocalTime::parse)
        return TransactionBulkPatch(date = dateValue?.let { LocalDate(it.year, it.monthValue.toUByte(), it.dayOfMonth.toUByte()) },
            time = timeValue?.let { LocalTime(it.hour.toUByte(), it.minute.toUByte(), it.second.toUByte(), it.nano.toUInt()) },
            categoryId = categoryId, subcategoryId = subcategoryId,
            amount = amount.trim().takeIf(String::isNotEmpty)?.let(::BigDecimal), notes = if (replaceNotes) notes else null)
    }
}
