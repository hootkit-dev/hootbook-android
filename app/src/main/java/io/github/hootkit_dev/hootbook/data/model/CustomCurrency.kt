package io.github.hootkit_dev.hootbook.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CustomCurrency(
    val code: String,
    val name: String,
    val symbol: String
) {
    fun toCurrency() = Currency(code = code.uppercase(), name = name, symbol = symbol)
}
