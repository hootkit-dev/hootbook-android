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

/** A kind of record an older (Cashiro) database or backup reports, as the app names it; unknown kinds keep their own name. */
@Composable
fun legacyItemLabel(item: String): String {
    val resource = when (item) {
        "accounts" -> R.string.legacy_upgrade_count_accounts
        "anchors" -> R.string.legacy_upgrade_count_anchors
        "categories" -> R.string.legacy_upgrade_count_categories
        "subcategories" -> R.string.legacy_upgrade_count_subcategories
        "transactions" -> R.string.legacy_upgrade_count_transactions
        "budgets" -> R.string.legacy_upgrade_count_budgets
        "budget_limits" -> R.string.legacy_upgrade_count_budget_limits
        "subscriptions" -> R.string.legacy_upgrade_count_subscriptions
        "people" -> R.string.legacy_upgrade_count_people
        "debts" -> R.string.legacy_upgrade_count_debts
        "custom_rates" -> R.string.legacy_upgrade_count_custom_rates
        "attachments" -> R.string.legacy_upgrade_count_attachments
        "cards" -> R.string.legacy_upgrade_count_cards
        "balance_updates" -> R.string.legacy_upgrade_count_balance_updates
        "unknown_transactions" -> R.string.legacy_upgrade_count_unknown_transactions
        "deleted_transactions" -> R.string.legacy_upgrade_count_deleted_transactions
        "deleted_debts" -> R.string.legacy_upgrade_count_deleted_debts
        "invalid_records" -> R.string.legacy_upgrade_count_invalid_records
        "brokerage_connections" -> R.string.legacy_upgrade_count_brokerage_connections
        "loan_transactions_without_account" -> R.string.legacy_upgrade_count_unlinked_loans
        "downloaded_rates" -> R.string.legacy_upgrade_count_downloaded_rates
        else -> null
    }
    return resource?.let { stringResource(it) } ?: item
}
