package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import dev.hootkit.core.Account
import dev.hootkit.core.AccountKind
import io.github.hootkit_dev.hootbook.R

enum class AccountCategory(val titleRes: Int) {
    WALLETS(R.string.section_wallets), BANKS(R.string.section_bank_accounts),
    CREDIT_CARDS(R.string.section_credit_cards), INVESTMENTS(R.string.overview_investments)
}

fun Account.category(): AccountCategory = when (kind) {
    AccountKind.WALLET -> AccountCategory.WALLETS
    AccountKind.CREDIT_CARD -> AccountCategory.CREDIT_CARDS
    AccountKind.INVESTMENT -> AccountCategory.INVESTMENTS
    else -> AccountCategory.BANKS
}
