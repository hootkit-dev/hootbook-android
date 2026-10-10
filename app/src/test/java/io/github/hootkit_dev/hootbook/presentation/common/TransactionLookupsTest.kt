package io.github.hootkit_dev.hootbook.presentation.common

import io.github.hootkit_dev.hootbook.data.currency.Conversions
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.CategoryEntity
import io.github.hootkit_dev.hootbook.data.database.entity.SubcategoryEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import io.github.hootkit_dev.hootbook.domain.model.PersonInfo
import java.math.BigDecimal
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionLookupsTest {
    private val food = CategoryEntity(id = 1, name = "Food", color = "#000000")
    private val transport = CategoryEntity(id = 2, name = "Transport", color = "#000000")
    private val foodOther = SubcategoryEntity(id = 10, categoryId = 1, name = "Other", color = "#111111")
    private val transportOther = SubcategoryEntity(id = 20, categoryId = 2, name = "Other", color = "#222222")
    private val card = AccountBalanceEntity(bankName = "BofA (Checking)", accountLast4 = "2616",
        balance = BigDecimal.TEN, timestamp = LocalDateTime.of(2026, 10, 4, 9, 0), currency = "USD")

    private val lookups = TransactionLookups(
        categories = mapOf("Food" to food, "Transport" to transport),
        subcategories = listOf(foodOther, transportOther).associateBy { it.categoryId to it.name },
        accounts = mapOf(TransactionLookups.accountKey(card.bankName, card.accountLast4) to card),
        persons = mapOf(7L to PersonInfo("Li", "#4CAF50", null))
    )

    private fun txn(id: Long, category: String, subcategory: String?) = TransactionEntity(
        id = id, amount = BigDecimal("12"), merchantName = "x", category = category, subcategory = subcategory,
        transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.of(2026, 10, 4, 9, 0),
        bankName = "BofA (Checking)", accountNumber = "2616", transactionHash = "h$id", currency = "USD"
    )

    @Test fun `same-named subcategories resolve within their own category`() {
        assertEquals(foodOther, lookups.subcategory(txn(1, "Food", "Other")))
        assertEquals(transportOther, lookups.subcategory(txn(2, "Transport", "Other")))
        assertNull(lookups.subcategory(txn(3, "Unknown", "Other")))
    }

    @Test fun `decoration carries account, person and conversion`() {
        val conversions = Conversions(amounts = mapOf(7L to BigDecimal("84.00")), pendingCurrencies = setOf("USD"))
        val decoration = lookups.decorate(txn(7, "Food", null), conversions)
        assertEquals(card, decoration.account)
        assertEquals("Li", decoration.person?.name)
        assertEquals(BigDecimal("84.00"), decoration.convertedAmount)
        assertTrue(decoration.rateLoading)
        assertNull(lookups.decorate(txn(8, "Food", null)).person)
    }
}
