package io.github.hootkit_dev.hootbook.presentation.ui.features.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import io.github.hootkit_dev.hootbook.data.cloud.security.CloudCredentialStore
import io.github.hootkit_dev.hootbook.data.database.HootbookDatabase
import io.github.hootkit_dev.hootbook.data.preferences.UserPreferencesRepository
import io.github.hootkit_dev.hootbook.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    userPreferencesRepository: UserPreferencesRepository,
    transactionRepository: TransactionRepository,
    private val database: HootbookDatabase,
    cloudCredentialStore: CloudCredentialStore
) : ViewModel() {

    val databaseVersion: Int
        get() = database.openHelper.readableDatabase.version

    val userPreferences = userPreferencesRepository.userPreferences

    val totalTransactions = transactionRepository.getTransactionCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val googleDriveEmail: StateFlow<String?> = cloudCredentialStore.googleDriveConfigFlow
        .map { it.accountEmail.ifBlank { null } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * Empties the ledger: transactions, accounts and their balances, cards, budgets,
     * subscriptions, loans and templates. Categories, rates and settings stay.
     */
    fun deleteAllData() {
        viewModelScope.launch {
            try {
                database.withTransaction {
                    val db = database.openHelper.writableDatabase
                    LEDGER_TABLES.forEach { db.execSQL("DELETE FROM `$it`") }
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error deleting data", e)
            }
        }
    }

    private companion object {
        // Children before parents, so no foreign key stands in the way
        val LEDGER_TABLES = listOf(
            "lend_borrow_transactions", "lend_borrow_persons", "budget_category_limits", "budgets",
            "subscriptions", "quick_templates", "account_balances", "transactions", "cards",
            "account_currencies", "accounts"
        )
    }
}
