package io.github.hootkit_dev.hootbook.presentation.ui.features.add

import dev.hootkit.core.LedgerInterface

/** Core controls merchant matching and ordering. */
internal suspend fun merchantCompletions(ledger: LedgerInterface, prefix: String): List<String> = ledger.merchants(prefix, 20u)
