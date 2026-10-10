package io.github.hootkit_dev.hootbook.presentation.ui.features.add

import io.github.hootkit_dev.hootbook.data.database.entity.QuickTemplateEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime

/** A merchant used before, with the category, subcategory and account of its latest use. */
data class MerchantSuggestion(
    val merchant: String,
    val category: String,
    val subcategory: String?,
    val transactionType: TransactionType,
    val bankName: String?,
    val accountLast4: String?,
    val uses: Int
)

/**
 * A merchant + category pair used often enough to offer as a template. [amount] is set when
 * every use had the same amount (a metro ride, a monthly fee).
 */
data class SuggestedTemplate(
    val merchant: String,
    val category: String,
    val subcategory: String?,
    val transactionType: TransactionType,
    val bankName: String?,
    val accountLast4: String?,
    val currency: String,
    val amount: BigDecimal?,
    val uses: Int
) {
    /** Identifies the suggestion for "don't suggest again". */
    val key: String get() = suggestionKey(merchant, category, transactionType)

    fun toTemplate(): QuickTemplateEntity = QuickTemplateEntity(
        name = merchant,
        merchantName = merchant,
        category = category,
        subcategory = subcategory,
        transactionType = transactionType,
        amount = amount,
        prefillAmount = amount != null,
        bankName = bankName,
        accountLast4 = accountLast4,
        currency = currency
    )
}

fun suggestionKey(merchant: String, category: String, type: TransactionType): String =
    "${merchant.trim().lowercase()}|$category|$type"

/**
 * Everything the Add form suggests, worked out from recent history: the categories used
 * most, merchants to complete, and frequent merchant + category pairs to offer as templates.
 */
object EntrySuggestions {
    const val CATEGORY_DAYS = 90L
    const val TEMPLATE_DAYS = 60L
    const val TEMPLATE_MIN_USES = 3

    // Types the merchant field and templates apply to; transfers and loans have their own flow.
    private val MERCHANT_TYPES = setOf(
        TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.CREDIT, TransactionType.INVESTMENT
    )

    /** The [limit] categories used most for [type] in the last [CATEGORY_DAYS] days, most used first. */
    fun topCategories(
        history: List<TransactionEntity>,
        type: TransactionType,
        now: LocalDateTime,
        limit: Int = 8
    ): List<String> {
        val since = now.minusDays(CATEGORY_DAYS)
        return history.asSequence()
            .filter { !it.isDeleted && it.transactionType == type && it.dateTime.isAfter(since) && it.category.isNotBlank() }
            .groupingBy { it.category }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key }
    }

    /** Merchants by number of uses, then most recent; values come from the latest use. */
    fun merchants(history: List<TransactionEntity>): List<MerchantSuggestion> =
        history.asSequence()
            .filter { !it.isDeleted && it.transactionType in MERCHANT_TYPES && it.merchantName.isNotBlank() }
            .groupBy { it.merchantName.trim().lowercase() }
            .values
            .map { uses ->
                val latest = uses.maxBy { it.dateTime }
                MerchantSuggestion(
                    merchant = latest.merchantName.trim(),
                    category = latest.category,
                    subcategory = latest.subcategory,
                    transactionType = latest.transactionType,
                    bankName = latest.bankName,
                    accountLast4 = latest.accountNumber,
                    uses = uses.size
                ) to latest.dateTime
            }
            .sortedWith(compareByDescending<Pair<MerchantSuggestion, LocalDateTime>> { it.first.uses }
                .thenByDescending { it.second })
            .map { it.first }

    /**
     * Up to [limit] merchants of [type] matching [query]: names starting with it first, then
     * names containing it. A blank query or an exact match suggests nothing.
     */
    fun matchMerchants(
        merchants: List<MerchantSuggestion>,
        query: String,
        type: TransactionType,
        limit: Int = 5
    ): List<MerchantSuggestion> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val ofType = merchants.filter { it.transactionType == type }
        if (ofType.any { it.merchant.equals(q, ignoreCase = true) }) return emptyList()
        val (prefix, rest) = ofType.filter { it.merchant.contains(q, ignoreCase = true) }
            .partition { it.merchant.startsWith(q, ignoreCase = true) }
        return (prefix + rest).take(limit)
    }

    /**
     * Merchant + category pairs used at least [TEMPLATE_MIN_USES] times in the last
     * [TEMPLATE_DAYS] days, most used first. Pairs already saved as a template, or listed in
     * [dismissed], are left out.
     */
    fun frequentTemplates(
        history: List<TransactionEntity>,
        now: LocalDateTime,
        templates: List<QuickTemplateEntity>,
        dismissed: Set<String>,
        limit: Int = 6
    ): List<SuggestedTemplate> {
        val since = now.minusDays(TEMPLATE_DAYS)
        val saved = templates.map { suggestionKey(it.merchantName, it.category, it.transactionType) }.toSet()
        return history.asSequence()
            .filter {
                !it.isDeleted && it.transactionType in MERCHANT_TYPES &&
                    it.merchantName.isNotBlank() && it.dateTime.isAfter(since)
            }
            .groupBy { suggestionKey(it.merchantName, it.category, it.transactionType) }
            .filter { (key, uses) -> uses.size >= TEMPLATE_MIN_USES && key !in saved && key !in dismissed }
            .values
            .map { uses ->
                val latest = uses.maxBy { it.dateTime }
                val amounts = uses.map { it.amount.stripTrailingZeros() }.toSet()
                SuggestedTemplate(
                    merchant = latest.merchantName.trim(),
                    category = latest.category,
                    subcategory = latest.subcategory,
                    transactionType = latest.transactionType,
                    bankName = latest.bankName,
                    accountLast4 = latest.accountNumber,
                    currency = latest.currency,
                    amount = latest.amount.takeIf { amounts.size == 1 },
                    uses = uses.size
                ) to latest.dateTime
            }
            .sortedWith(compareByDescending<Pair<SuggestedTemplate, LocalDateTime>> { it.first.uses }
                .thenByDescending { it.second })
            .take(limit)
            .map { it.first }
    }
}
