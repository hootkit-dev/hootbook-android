package io.github.hootkit_dev.hootbook.data.repository

import android.content.Context
import androidx.core.content.edit
import androidx.room.withTransaction
import io.github.hootkit_dev.hootbook.data.database.HootbookDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Renames an account's institution everywhere it is referenced. Accounts have no id of their
 * own: transactions, balances, cards, templates, budgets and the account preferences all find
 * an account by bank name and last 4, so renaming only the balance rows used to orphan its
 * transactions.
 */
@Singleton
class AccountRenamer @Inject constructor(
    private val database: HootbookDatabase,
    @ApplicationContext private val context: Context
) {
    suspend fun rename(oldBankName: String, accountLast4: String, newBankName: String) {
        if (oldBankName == newBankName || newBankName.isBlank()) return
        database.withTransaction {
            val balances = database.accountBalanceDao()
            // Subscriptions name only the institution: move them only if no other account shares it
            val othersWithOldName = balances.getAllLatestBalances().first()
                .any { it.bankName == oldBankName && it.accountLast4 != accountLast4 }

            balances.updateAccountBankName(oldBankName, accountLast4, newBankName)
            database.transactionDao().updateAccountForTransactions(oldBankName, accountLast4, newBankName, accountLast4)
            database.cardDao().renameAccountBank(oldBankName, accountLast4, newBankName)
            database.quickTemplateDao().renameAccountBank(oldBankName, accountLast4, newBankName)
            if (!othersWithOldName) database.subscriptionDao().renameBank(oldBankName, newBankName)

            val oldBudgetId = "$oldBankName:$accountLast4"
            database.budgetDao().getAllBudgets().first()
                .filter { oldBudgetId in it.accountIds }
                .forEach { budget ->
                    database.budgetDao().updateBudget(
                        budget.copy(accountIds = budget.accountIds.map { if (it == oldBudgetId) "$newBankName:$accountLast4" else it })
                    )
                }
        }
        // The default account is kept by key in preferences
        val prefs = context.getSharedPreferences("account_prefs", Context.MODE_PRIVATE)
        val oldKey = "${oldBankName}_$accountLast4"
        val newKey = "${newBankName}_$accountLast4"
        prefs.edit {
            if (prefs.getString("main_account", null) == oldKey) putString("main_account", newKey)
        }
    }
}
