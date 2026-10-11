package io.github.hootkit_dev.hootbook.presentation.core.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.hootkit.core.Table
import io.github.hootkit_dev.hootbook.R

/** A kind of record as the app names it (账户, 交易…), for sync conflicts and backup summaries. */
@Composable
fun recordKindLabel(table: Table): String = stringResource(when (table) {
    Table.ACCOUNTS -> R.string.record_kind_accounts
    Table.ACCOUNT_CURRENCIES -> R.string.record_kind_account_currencies
    Table.BALANCE_ANCHORS -> R.string.record_kind_anchors
    Table.CATEGORIES -> R.string.record_kind_categories
    Table.SUBCATEGORIES -> R.string.record_kind_subcategories
    Table.TRANSACTIONS -> R.string.record_kind_transactions
    Table.BUDGETS, Table.BUDGET_ACCOUNTS -> R.string.record_kind_budgets
    Table.BUDGET_LIMITS -> R.string.record_kind_budget_limits
    Table.SUBSCRIPTIONS -> R.string.record_kind_subscriptions
    Table.PEOPLE -> R.string.record_kind_people
    Table.DEBTS -> R.string.record_kind_debts
    Table.QUICK_TEMPLATES -> R.string.record_kind_templates
    Table.EXCHANGE_RATES -> R.string.record_kind_rates
    Table.SETTINGS, Table.SYNC_STATE -> R.string.record_kind_settings
    Table.BROKERAGE -> R.string.record_kind_brokerage
})

/** The same for the item names an older backup reports; an unknown name is shown as it is. */
@Composable
fun recordKindLabel(item: String): String = when (item) {
    "accounts" -> recordKindLabel(Table.ACCOUNTS)
    "anchors", "balance_anchors" -> recordKindLabel(Table.BALANCE_ANCHORS)
    "transactions" -> recordKindLabel(Table.TRANSACTIONS)
    "categories" -> recordKindLabel(Table.CATEGORIES)
    "subcategories" -> recordKindLabel(Table.SUBCATEGORIES)
    "budgets" -> recordKindLabel(Table.BUDGETS)
    "budget_limits" -> recordKindLabel(Table.BUDGET_LIMITS)
    "subscriptions" -> recordKindLabel(Table.SUBSCRIPTIONS)
    "templates", "quick_templates" -> recordKindLabel(Table.QUICK_TEMPLATES)
    "people" -> recordKindLabel(Table.PEOPLE)
    "debts" -> recordKindLabel(Table.DEBTS)
    "rates", "exchange_rates", "downloaded_rates" -> recordKindLabel(Table.EXCHANGE_RATES)
    else -> item
}
