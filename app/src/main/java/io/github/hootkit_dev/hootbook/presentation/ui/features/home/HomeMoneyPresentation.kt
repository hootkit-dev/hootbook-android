package io.github.hootkit_dev.hootbook.presentation.ui.features.home

import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import java.math.BigDecimal
import java.util.Locale

/** Home's name for [CoreMoneyPresentation.format]; one formatter serves every screen. */
internal object HomeMoneyPresentation {
    fun format(
        amount: BigDecimal?,
        currency: String,
        symbols: Map<String, String> = emptyMap(),
        locale: Locale = Locale.getDefault(),
        useCode: Boolean = false,
    ): String = CoreMoneyPresentation.format(amount, currency, symbols, locale, useCode)
}
