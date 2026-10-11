package io.github.hootkit_dev.hootbook.presentation.format

import dev.hootkit.core.CurrencyInterface
import kotlinx.coroutines.CancellationException
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import java.util.Optional

data class CoreDisplayedMoney(
    val originalAmount: BigDecimal,
    val originalCurrency: String,
    val amount: BigDecimal?,
    val currency: String,
    val complete: Boolean,
    val failed: Boolean = false,
)

/** Original compact amount typography for restored UI. Presentation only; Core owns values. */
object CoreMoneyPresentation {
    private val symbols =
        mapOf(
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
            "KES" to "KSh",
        )

    private val INDIAN_LOCALE = Locale("en", "IN")

    /** Locale mapping for different currencies */
    private val CURRENCY_LOCALES =
        mapOf(
            "INR" to INDIAN_LOCALE,
            "USD" to Locale.US,
            "EUR" to Locale.GERMANY,
            "GBP" to Locale.UK,
            "AED" to Locale.Builder().setLanguage("en").setRegion("AE").build(),
            "SGD" to Locale.Builder().setLanguage("en").setRegion("SG").build(),
            "CAD" to Locale.CANADA,
            "AUD" to Locale.Builder().setLanguage("en").setRegion("AU").build(),
            "JPY" to Locale.JAPAN,
            "CNY" to Locale.CHINA,
            "HKD" to Locale("en", "HK"),
            "TWD" to Locale.TAIWAN,
            "MOP" to Locale("zh", "MO"),
            "NPR" to Locale.Builder().setLanguage("ne").setRegion("NP").build(),
            "ETB" to Locale.Builder().setLanguage("am").setRegion("ET").build(),
            "THB" to Locale.Builder().setLanguage("th").setRegion("TH").build(),
            "MYR" to Locale.Builder().setLanguage("ms").setRegion("MY").build(),
            "KWD" to Locale.Builder().setLanguage("en").setRegion("KW").build(),
            "KRW" to Locale.KOREA,
            "SEK" to Locale.Builder().setLanguage("sv").setRegion("SE").build(),
            "CHF" to Locale.Builder().setLanguage("de").setRegion("CH").build(),
            "NZD" to Locale.Builder().setLanguage("en").setRegion("NZ").build(),
            "MXN" to Locale.Builder().setLanguage("es").setRegion("MX").build(),
            "TRY" to Locale("tr", "TR"),
            "RUB" to Locale("ru", "RU"),
            "ZAR" to Locale("en", "ZA"),
            "BRL" to Locale("pt", "BR"),
            "PLN" to Locale("pl", "PL"),
            "NOK" to Locale("nb", "NO"),
            "DKK" to Locale("da", "DK"),
            "CZK" to Locale("cs", "CZ"),
            "HUF" to Locale("hu", "HU"),
            "ILS" to Locale("he", "IL"),
            "PHP" to Locale("en", "PH"),
            "IDR" to Locale("in", "ID"),
            "SAR" to Locale("ar", "SA"),
            "COP" to Locale("es", "CO"),
            "KES" to Locale("sw", "KE"),
        )

    private val DEFAULT_LOCALE = Locale.US

    private fun localeFor(currencyCode: String): Locale =
        CURRENCY_LOCALES[currencyCode]
            ?: if (currencyCode == "INR" || currencyCode == "NPR") INDIAN_LOCALE else DEFAULT_LOCALE

    // Building a DecimalFormat/NumberFormat loads locale data and allocates; these run several
    // times per transaction row while scrolling. java.text formatters are not thread safe, so
    // the caches are per-thread rather than shared.
    private val amountFormats =
        object : ThreadLocal<MutableMap<String, DecimalFormat>>() {
            override fun initialValue() = mutableMapOf<String, DecimalFormat>()
        }

    private val currencyFormats =
        object : ThreadLocal<MutableMap<String, Optional<NumberFormat>>>() {
            override fun initialValue() = mutableMapOf<String, Optional<NumberFormat>>()
        }

    private fun amountFormat(currencyCode: String): DecimalFormat =
        amountFormats.get()!!.getOrPut(currencyCode) {
            val pattern =
                if (currencyCode == "INR" || currencyCode == "NPR") "#,##,##0.00" else "#,##0.00"
            DecimalFormat(pattern, DecimalFormatSymbols(localeFor(currencyCode)))
        }

    /**
     * Returns null when the platform has no currency data for [currencyCode]. A [whole] amount
     * drops the decimals (¥178); any other shows them all (¥187.10, never ¥187.1).
     */
    private fun currencyFormat(currencyCode: String, whole: Boolean): NumberFormat? =
        currencyFormats
            .get()!!
            .getOrPut("$currencyCode|$whole") {
                runCatching {
                        NumberFormat.getCurrencyInstance(localeFor(currencyCode)).apply {
                            currency = Currency.getInstance(currencyCode)
                            val digits =
                                currency!!.defaultFractionDigits.coerceAtLeast(0).coerceAtMost(2)
                            minimumFractionDigits = if (whole) 0 else digits
                            maximumFractionDigits = digits
                        }
                    }
                    .map { Optional.of(it) }
                    .getOrDefault(Optional.empty())
            }
            .orElse(null)

    /** Formats a BigDecimal amount as currency with the specified currency code */
    fun formatCurrency(amount: BigDecimal, currencyCode: String = "CNY"): String {
        return try {
            val locale = localeFor(currencyCode)

            // Get our custom symbol
            val customSymbol = getCurrencySymbol(currencyCode)

            // If currency not supported, use symbol mapping
            val formatter =
                currencyFormat(
                    currencyCode,
                    whole = amount.signum() == 0 || amount.stripTrailingZeros().scale() <= 0,
                ) ?: return "$customSymbol${formatAmount(amount)}"

            val formatted = formatter.format(amount)

            // If the formatted string doesn't contain our custom symbol, or contains the ISO code,
            // we override it to ensure the custom symbol is used.
            if (formatted.contains(currencyCode) || !formatted.contains(customSymbol)) {
                val cleanAmount = formatAmount(amount, currencyCode)
                return if (
                    locale == Locale.US ||
                        locale == INDIAN_LOCALE ||
                        locale == Locale.UK ||
                        currencyCode in setOf("CNY", "HKD", "SGD", "TWD", "MOP", "JPY")
                ) {
                    "$customSymbol$cleanAmount"
                } else {
                    "$cleanAmount $customSymbol"
                }
            }

            formatted
        } catch (e: Exception) {
            // Fallback to symbol + amount
            val symbol = getCurrencySymbol(currencyCode)
            "$symbol${formatAmount(amount, currencyCode)}"
        }
    }

    /** Formats a Double amount as currency with the specified currency code */
    fun formatCurrency(amount: Double, currencyCode: String = "CNY"): String {
        return formatCurrency(amount.toBigDecimal(), currencyCode)
    }

    /**
     * Formats an amount with proper grouping and decimals Uses Indian grouping (#,##,##0.00) for
     * INR and NPR, standard (#,##0.00) otherwise
     */
    fun formatAmount(amount: BigDecimal, currencyCode: String = "CNY"): String =
        amountFormat(currencyCode).format(amount)

    /** Formats a double amount with proper grouping and decimals */
    fun formatAmount(amount: Double, currencyCode: String = "CNY"): String {
        return formatAmount(amount.toBigDecimal(), currencyCode)
    }

    /** Get symbol for a currency code */
    fun getCurrencySymbol(currencyCode: String): String {
        return symbols[currencyCode.uppercase(Locale.ROOT)] ?: currencyCode
    }
    /** Nullable Core values and custom symbols share the original compact display style. */
    fun format(amount: BigDecimal?, currency: String, customSymbolMap: Map<String, String> = emptyMap()): String {
        if (amount == null) return "— $currency"
        val symbol = customSymbolMap[currency] ?: return formatCurrency(amount, currency)
        return "${symbol.ifBlank { currency }}${formatAmount(amount, currency)}"
    }

    suspend fun convert(coreCurrency: CurrencyInterface, amount: BigDecimal, from: String, to: String): CoreDisplayedMoney {
        return try {
            val converted = coreCurrency.convert(amount, from, to)
            CoreDisplayedMoney(amount, from, converted, to, complete = converted != null)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { CoreDisplayedMoney(amount, from, null, to, complete = false, failed = true) }
    }
}
