package io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow

import dev.hootkit.core.*
internal val planDebtTables = setOf(Table.PEOPLE, Table.DEBTS, Table.TRANSACTIONS, Table.ACCOUNTS,
    Table.ACCOUNT_CURRENCIES, Table.CATEGORIES, Table.EXCHANGE_RATES, Table.SETTINGS)
internal fun planToday() = java.time.LocalDate.now().let { LocalDate(it.year, it.monthValue.toUByte(), it.dayOfMonth.toUByte()) }
