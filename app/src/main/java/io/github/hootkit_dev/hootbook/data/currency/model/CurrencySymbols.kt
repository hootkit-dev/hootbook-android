package io.github.hootkit_dev.hootbook.data.currency.model

import java.util.Locale

object CurrencySymbols {
    private val symbols = mapOf(
        "USD" to "$",
        "EUR" to "€",
        "GBP" to "£",
        "JPY" to "¥",
        "CNY" to "¥",
        "AUD" to "A$",
        "CAD" to "C$",
        "CHF" to "CHF",
        "HKD" to "HK$",
        "TWD" to "NT$",
        "MOP" to "MOP$",
        "SGD" to "S$",
        "INR" to "₹",
        "RUB" to "₽",
        "ZAR" to "R",
        "TRY" to "₺",
        "BRL" to "R$",
        "THB" to "฿",
        "KRW" to "₩",
        "MXN" to "$",
        "MYR" to "RM",
        "PLN" to "zł",
        "SEK" to "kr",
        "NOK" to "kr",
        "DKK" to "kr",
        "CZK" to "Kč",
        "HUF" to "Ft",
        "ILS" to "₪",
        "PHP" to "₱",
        "IDR" to "Rp",
        "AED" to "د.إ",
        "SAR" to "﷼",
        "NZD" to "NZ$",
        "BTC" to "₿",
        "ETH" to "Ξ",
        "LTC" to "Ł",
        "BCH" to "BCH",
        "XRP" to "XRP",
        "NPR" to "₨",
        "ETB" to "ብር",
        "KWD" to "د.ك",
        "COP" to "$",
        "KES" to "KSh"
    )

    private var customSymbols = mapOf<String, String>()

    fun setCustomSymbols(newSymbols: Map<String, String>) {
        customSymbols = newSymbols.mapKeys { it.key.uppercase(Locale.ROOT) }
    }

    fun getSymbol(currencyCode: String): String {
        val code = currencyCode.uppercase(Locale.ROOT)
        return customSymbols[code] ?: symbols[code] ?: code
    }

}
