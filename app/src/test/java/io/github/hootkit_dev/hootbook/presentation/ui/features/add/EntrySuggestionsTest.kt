package io.github.hootkit_dev.hootbook.presentation.ui.features.add

import io.github.hootkit_dev.hootbook.data.database.entity.QuickTemplateEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import io.github.hootkit_dev.hootbook.utils.displayTitle
import java.math.BigDecimal
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntrySuggestionsTest {
    private val now = LocalDateTime.of(2026, 10, 3, 12, 0)
    private var nextId = 1L

    private fun txn(
        merchant: String,
        category: String,
        daysAgo: Long = 1,
        amount: String = "10",
        type: TransactionType = TransactionType.EXPENSE,
        subcategory: String? = null,
        note: String? = null,
        account: String = "1234"
    ) = TransactionEntity(
        id = nextId++,
        amount = BigDecimal(amount),
        merchantName = merchant,
        category = category,
        subcategory = subcategory,
        transactionType = type,
        dateTime = now.minusDays(daysAgo),
        description = note,
        bankName = "BOC",
        accountNumber = account,
        transactionHash = "h$nextId",
        currency = "CNY"
    )

    @Test fun `top categories count only the type and the window, most used first`() {
        val history = listOf(
            txn("A", "Food"), txn("B", "Food"), txn("C", "Transport"),
            txn("D", "Shopping", daysAgo = 120),
            txn("Pay", "Salary", type = TransactionType.INCOME)
        )
        assertEquals(listOf("Food", "Transport"), EntrySuggestions.topCategories(history, TransactionType.EXPENSE, now))
        assertEquals(listOf("Salary"), EntrySuggestions.topCategories(history, TransactionType.INCOME, now))
    }

    @Test fun `merchants merge case and take category and account from the latest use`() {
        val history = listOf(
            txn("Luckin", "Food", daysAgo = 5, account = "1111"),
            txn("luckin ", "Coffee", daysAgo = 1, account = "2222"),
            txn("Didi", "Transport"),
            txn("", "Food")
        )
        val merchants = EntrySuggestions.merchants(history)
        assertEquals(listOf("luckin", "Didi"), merchants.map { it.merchant })
        assertEquals("Coffee", merchants[0].category)
        assertEquals("2222", merchants[0].accountLast4)
        assertEquals(2, merchants[0].uses)
    }

    @Test fun `merchant matches put prefixes first and stop at an exact match`() {
        val merchants = EntrySuggestions.merchants(listOf(
            txn("Starbucks", "Food"), txn("Bar Star", "Food"), txn("Star Market", "Food")
        ))
        val matches = EntrySuggestions.matchMerchants(merchants, "star", TransactionType.EXPENSE)
        assertEquals(listOf("Starbucks", "Star Market", "Bar Star"), matches.map { it.merchant })
        assertEquals(emptyList<MerchantSuggestion>(), EntrySuggestions.matchMerchants(merchants, "starbucks", TransactionType.EXPENSE))
        assertEquals(emptyList<MerchantSuggestion>(), EntrySuggestions.matchMerchants(merchants, " ", TransactionType.EXPENSE))
        assertEquals(emptyList<MerchantSuggestion>(), EntrySuggestions.matchMerchants(merchants, "star", TransactionType.INCOME))
    }

    @Test fun `frequent pairs become templates unless saved or dismissed`() {
        val history = listOf(
            txn("Metro", "Transport", 1, "4"), txn("Metro", "Transport", 2, "4.00"), txn("Metro", "Transport", 3, "4"),
            txn("Luckin", "Food", 1, "15"), txn("Luckin", "Food", 2, "18"), txn("Luckin", "Food", 3, "15"),
            txn("Hema", "Food", 1), txn("Hema", "Food", 2),
            txn("Didi", "Transport", 1), txn("Didi", "Transport", 2), txn("Didi", "Transport", 90)
        )
        val suggested = EntrySuggestions.frequentTemplates(history, now, emptyList(), emptySet())
        assertEquals(setOf("Metro", "Luckin"), suggested.map { it.merchant }.toSet())
        val metro = suggested.first { it.merchant == "Metro" }
        assertEquals(BigDecimal("4"), metro.amount?.stripTrailingZeros())
        assertTrue(metro.toTemplate().prefillAmount)
        assertNull(suggested.first { it.merchant == "Luckin" }.amount)

        val saved = listOf(QuickTemplateEntity(name = "Coffee", merchantName = "luckin", category = "Food"))
        val dismissed = setOf(metro.key)
        assertEquals(emptyList<SuggestedTemplate>(), EntrySuggestions.frequentTemplates(history, now, saved, dismissed))
    }

    @Test fun `entries without a merchant are listed by category and note`() {
        assertEquals("Didi", txn("Didi", "Transport").displayTitle())
        assertEquals("Food · lunch with Li", txn("", "Food", note = "\n lunch with Li \nsecond line").displayTitle())
        assertEquals("Coffee", txn(" ", "Food", subcategory = "Coffee").displayTitle())
    }
}
