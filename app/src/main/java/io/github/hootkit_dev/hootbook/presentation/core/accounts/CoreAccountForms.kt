package io.github.hootkit_dev.hootbook.presentation.core.accounts

import dev.hootkit.core.*
import java.math.BigDecimal
import java.time.LocalDateTime as JavaDateTime

/** Text exists only at the input boundary; Core records remain the query state. */
data class CoreAccountDraft(
    val id: String? = null,
    val name: String = "",
    val last4: String = "",
    val kind: AccountKind = AccountKind.BANK,
    val currency: String = "CNY",
    val openingBalance: String = "",
    val openingAt: String = "",
    val creditLimit: String = "",
    val statementDay: String = "",
    val dueDay: String = "",
    val institutionKey: String = "",
    val icon: String = "",
    val color: String = "",
) {
    fun newAccount() = NewAccount(
        name = name.trim(), last4 = last4.trim().takeIf { it.isNotEmpty() && kind != AccountKind.WALLET },
        kind = kind, mainCurrency = currency.trim().uppercase(),
        openingBalance = optionalAmount(openingBalance), openingAt = optionalDateTime(openingAt),
        creditLimit = if (kind == AccountKind.CREDIT_CARD) optionalAmount(creditLimit) else null,
        statementDay = if (kind == AccountKind.CREDIT_CARD) optionalDay(statementDay) else null,
        dueDay = if (kind == AccountKind.CREDIT_CARD) optionalDay(dueDay) else null,
        institutionKey = institutionKey.trim().ifBlank { null }, icon = icon.trim().ifBlank { null },
        color = color.trim().ifBlank { null }, extraCurrencies = emptyList(),
    )

    fun patch() = AccountPatch(
        name = name.trim(), last4 = last4.trim().takeIf { it.isNotEmpty() && kind != AccountKind.WALLET },
        clearLast4 = last4.isBlank() || kind == AccountKind.WALLET,
        kind = kind, mainCurrency = currency.trim().uppercase(),
        institutionKey = institutionKey.trim().ifBlank { null }, clearInstitution = institutionKey.isBlank(),
        icon = icon.trim().ifBlank { null }, color = color.trim().ifBlank { null },
        creditLimit = if (kind == AccountKind.CREDIT_CARD) optionalAmount(creditLimit) else null,
        clearCreditLimit = creditLimit.isBlank(),
        statementDay = if (kind == AccountKind.CREDIT_CARD) optionalDay(statementDay) else null,
        dueDay = if (kind == AccountKind.CREDIT_CARD) optionalDay(dueDay) else null,
        clearBillingDays = true,
    )

    companion object {
        fun from(account: Account) = CoreAccountDraft(
            id = account.id, name = account.name, last4 = account.last4.orEmpty(), kind = account.kind,
            currency = account.mainCurrency, creditLimit = account.creditLimit?.toPlainString().orEmpty(),
            statementDay = account.statementDay?.toString().orEmpty(), dueDay = account.dueDay?.toString().orEmpty(),
            institutionKey = account.institutionKey.orEmpty(), icon = account.icon, color = account.color,
        )
    }
}

internal fun optionalAmount(text: String): BigDecimal? = text.trim().takeIf(String::isNotEmpty)?.let {
    it.toBigDecimalOrNull() ?: throw IllegalArgumentException("请输入有效金额")
}
internal fun requiredAmount(text: String): BigDecimal = optionalAmount(text) ?: throw IllegalArgumentException("请输入金额")
internal fun optionalDay(text: String): UByte? = text.trim().takeIf(String::isNotEmpty)?.let {
    val day = it.toIntOrNull() ?: throw IllegalArgumentException("请输入 1–31 的日期")
    require(day in 1..31) { "请输入 1–31 的日期" }
    day.toUByte()
}
internal fun optionalDateTime(text: String): LocalDateTime? = text.trim().takeIf(String::isNotEmpty)?.let {
    val time = runCatching { JavaDateTime.parse(it) }.getOrElse { throw IllegalArgumentException("日期格式：2026-10-10T12:30:00") }
    LocalDateTime(time.year, time.monthValue.toUByte(), time.dayOfMonth.toUByte(), time.hour.toUByte(),
        time.minute.toUByte(), time.second.toUByte(), time.nano.toUInt())
}
internal fun LocalDateTime.inputText(): String = JavaDateTime.of(year, month.toInt(), day.toInt(), hour.toInt(),
    minute.toInt(), second.toInt(), nanosecond.toInt()).toString()

internal fun coreFailureText(error: Throwable): String = when (error) {
    is CoreException.Invalid -> "请检查 ${error.field}：${error.reason}"
    is CoreException.Conflict -> when (error.reason) {
        ConflictReason.IN_USE -> "仍有交易引用（含回收站）。请先处理交易，或选择归档／连同交易删除。"
        ConflictReason.ACCOUNT_EXISTS -> "同名同尾号账户已存在"
        ConflictReason.CURRENCY_EXISTS -> "账户已有该货币口袋"
        ConflictReason.ANCHOR_EXISTS -> "该时刻已有余额锚点"
        ConflictReason.CATEGORY_EXISTS -> "同类型下已有同名分类"
        ConflictReason.SUBCATEGORY_EXISTS -> "该分类下已有同名子分类"
        else -> "操作冲突：${error.reason}"
    }
    is CoreException.NotFound -> "记录已删除，请刷新"
    else -> "Operation failed; review the input and refresh before retrying"
}
