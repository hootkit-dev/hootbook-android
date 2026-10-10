package io.github.hootkit_dev.hootbook.utils

import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.CardEntity
import io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity

/**
 * Extension functions for entity currency formatting
 * These functions automatically use the entity's currency for formatting
 */

/**
 * Formats the transaction amount with its currency
 */
fun TransactionEntity.formatAmount(): String =
    CurrencyFormatter.formatCurrency(amount, currency)

/**
 * Formats the subscription amount with its currency
 */
fun SubscriptionEntity.formatAmount(): String =
    CurrencyFormatter.formatCurrency(amount, currency)

/**
 * Formats the card's last balance with its currency
 */
fun CardEntity.formatLastBalance(): String =
    CurrencyFormatter.formatCurrency(lastBalance ?: java.math.BigDecimal.ZERO, currency)

/**
 * Formats the account's credit limit with its currency (for credit card accounts)
 */
fun AccountBalanceEntity.formatCreditLimit(): String =
    CurrencyFormatter.formatCurrency(creditLimit ?: java.math.BigDecimal.ZERO, currency)
/**
 * What a transaction is listed as: its merchant, or for an entry without one (the merchant
 * is optional) its category and the first line of its note, e.g. "Food · lunch with Li".
 */
fun TransactionEntity.displayTitle(): String {
    if (merchantName.isNotBlank()) return merchantName
    val note = description?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }
    return listOfNotNull(io.github.hootkit_dev.hootbook.presentation.common.CategoryNames.display(subcategory?.takeIf { it.isNotBlank() } ?: category), note).joinToString(" · ")
}
