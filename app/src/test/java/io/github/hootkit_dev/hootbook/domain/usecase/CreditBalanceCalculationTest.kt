package io.github.hootkit_dev.hootbook.domain.usecase

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests the mathematical formula used for credit limit inference from SMS.
 *
 * The SMS parser extracts the *available* limit (remaining credit from the bank SMS).
 * The app knows the *new outstanding* balance after processing the transaction.
 * Total credit limit = availableLimit + newOutstanding
 *
 * These tests verify this formula with realistic values.
 */
class CreditBalanceCalculationTest {

    @Test
    fun `total limit inferred from available limit and new outstanding`() {
        val availableLimit = BigDecimal("49500")
        val newOutstanding = BigDecimal("2500")
        val totalLimit = availableLimit.add(newOutstanding)
        assertEquals(BigDecimal("52000"), totalLimit)
    }

    @Test
    fun `first time credit card setup from SMS`() {
        val availableLimit = BigDecimal("50000")
        val newOutstanding = BigDecimal("500")  // first purchase
        val totalLimit = availableLimit.add(newOutstanding)
        assertEquals(BigDecimal("50500"), totalLimit)
    }

    @Test
    fun `multiple purchases before SMS with limit`() {
        val previousOutstanding = BigDecimal("3200")
        val newPurchase = BigDecimal("800")
        val newOutstanding = previousOutstanding.add(newPurchase)
        val availableLimit = BigDecimal("46000")
        val totalLimit = availableLimit.add(newOutstanding)
        assertEquals(BigDecimal("50000"), totalLimit)
    }

    @Test
    fun `nearly maxed out credit card`() {
        val previousOutstanding = BigDecimal("45000")
        val newPurchase = BigDecimal("3000")
        val newOutstanding = previousOutstanding.add(newPurchase)
        val availableLimit = BigDecimal("2000")
        val totalLimit = availableLimit.add(newOutstanding)
        assertEquals(BigDecimal("50000"), totalLimit)
    }

    @Test
    fun `credit card payment reduces outstanding`() {
        val previousOutstanding = BigDecimal("15000")
        val payment = BigDecimal("5000")
        val newOutstanding = previousOutstanding.subtract(payment).max(BigDecimal.ZERO)
        val availableLimit = BigDecimal("40000")
        val totalLimit = availableLimit.add(newOutstanding)
        assertEquals(BigDecimal("50000"), totalLimit)
    }

    @Test
    fun `no available limit in SMS keeps existing credit limit`() {
        val existingCreditLimit = BigDecimal("30000")
        val availableLimit: BigDecimal? = null
        val result = availableLimit?.add(BigDecimal("1500")) ?: existingCreditLimit
        assertEquals(BigDecimal("30000"), result)
    }

    @Test
    fun `available limit with zero outstanding gives total limit`() {
        val availableLimit = BigDecimal("100000")
        val newOutstanding = BigDecimal.ZERO
        val totalLimit = availableLimit.add(newOutstanding)
        assertEquals(BigDecimal("100000"), totalLimit)
    }

    @Test
    fun `large numbers precision`() {
        val availableLimit = BigDecimal("999999.99")
        val newOutstanding = BigDecimal("123456.78")
        val totalLimit = availableLimit.add(newOutstanding)
        assertEquals(BigDecimal("1123456.77"), totalLimit)
    }

    @Test
    fun `small transaction precision`() {
        val availableLimit = BigDecimal("49999.50")
        val newOutstanding = BigDecimal("0.50")
        val totalLimit = availableLimit.add(newOutstanding)
        assertEquals(BigDecimal("50000.00"), totalLimit)
    }
}
