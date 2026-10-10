package io.github.hootkit_dev.hootbook.data.preferences

enum class HomeWidget(val titleRes: Int, val defaultOrder: Int) {
    NETWORTH_SUMMARY(io.github.hootkit_dev.hootbook.R.string.home_widget_net_worth, 0),
    LOANS(io.github.hootkit_dev.hootbook.R.string.home_widget_loans, 1),
    // Shown under the net worth from its button; kept so saved layouts still parse
    ACCOUNT_CAROUSEL(io.github.hootkit_dev.hootbook.R.string.home_widget_accounts, 2),
    UPCOMING_SUBSCRIPTIONS(io.github.hootkit_dev.hootbook.R.string.home_widget_subscriptions, 3),
    RECENT_TRANSACTIONS(io.github.hootkit_dev.hootbook.R.string.home_widget_recent, 4),
    BUDGET_CAROUSEL(io.github.hootkit_dev.hootbook.R.string.home_widget_budgets, 5),
    TRANSACTION_HEATMAP(io.github.hootkit_dev.hootbook.R.string.home_widget_heatmap, 6);
    
    companion object {
        fun fromName(name: String): HomeWidget? {
            return entries.find { it.name == name }
        }
    }
}
