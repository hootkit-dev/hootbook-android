package io.github.hootkit_dev.hootbook.utils

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Utility functions for currency formatting
 */
object CurrencyUtils {
    
    private val defaultLocale = Locale.CHINA
    /**
     * Formats a BigDecimal amount as Chinese yuan
     * @param amount The amount to format
     * @return Formatted string like "¥1,234" or "¥123,456"
     */
    fun formatCurrency(amount: BigDecimal): String {
        // For amounts with decimals, show them
        return if (amount.stripTrailingZeros().scale() > 0) {
            val formatter = NumberFormat.getCurrencyInstance(defaultLocale).apply {
                currency = Currency.getInstance("CNY")
                maximumFractionDigits = 2
                minimumFractionDigits = 1
            }
            formatter.format(amount)
        } else {
            NumberFormat.getCurrencyInstance(defaultLocale).apply {
                currency = Currency.getInstance("CNY")
                minimumFractionDigits = 0
                maximumFractionDigits = 0
            }.format(amount)
        }
    }
    
    /**
     * Formats a Double amount as Chinese yuan
     */
    fun formatCurrency(amount: Double): String {
        return formatCurrency(BigDecimal.valueOf(amount))
    }
    
    /**
     * Formats an Int amount as Chinese yuan
     */
    fun formatCurrency(amount: Int): String {
        return formatCurrency(BigDecimal(amount))
    }
    
    /**
     * Formats an amount with a custom number of decimal places
     */
    fun formatCurrency(amount: BigDecimal, decimalPlaces: Int): String {
        val formatter = NumberFormat.getCurrencyInstance(defaultLocale).apply {
            currency = Currency.getInstance("CNY")
            maximumFractionDigits = decimalPlaces
            minimumFractionDigits = decimalPlaces
        }
        return formatter.format(amount)
    }

    /**
     * Sorts a list of currency codes with CNY prioritized first, then alphabetically.
     * This is the standard sorting for currency lists throughout the app.
     *
     * @param currencies List of currency codes to sort
     * @return Sorted list with CNY first (if present), then alphabetically
     *
     * Example:
     * ```
     * sortCurrencies(listOf("USD", "EUR", "CNY", "GBP"))
     * // Returns: ["CNY", "EUR", "GBP", "USD"]
     * ```
     */
    fun sortCurrencies(currencies: List<String>): List<String> {
        return currencies.sortedWith { a, b ->
            when {
                a == b -> 0
                a == "CNY" -> -1 // CNY first
                b == "CNY" -> 1
                else -> a.compareTo(b) // Alphabetical for others
            }
        }
    }
}