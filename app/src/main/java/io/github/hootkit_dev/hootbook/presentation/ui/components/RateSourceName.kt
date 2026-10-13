package io.github.hootkit_dev.hootbook.presentation.ui.components

/** Provider ids are Core storage values; keep their names consistent across the rates sheet. */
internal fun rateSourceName(id: String): String = when (id) {
    "er_api" -> "Exchange Rate API"
    "frankfurter" -> "Frankfurter"
    "currency_api" -> "CurrencyAPI"
    else -> id
}
