package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import dev.hootkit.core.Account

internal fun List<Account>.accountSections(): Map<AccountCategory, List<Account>> =
    AccountCategory.entries.associateWith { category ->
        filter { it.category() == category }.sortedWith(compareBy<Account>({ !it.isDefault }, { it.sortOrder }))
    }
