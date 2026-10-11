package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import dev.hootkit.core.*
import java.math.BigDecimal
import java.time.LocalDate

data class TransactionDetailUiState(
    val transaction: Transaction? = null,
    val editingSubscriptionId: String? = null,
    val editableTransaction: Transaction? = null,
    val categories: Map<String, Category> = emptyMap(),
    val accounts: Map<String, Account> = emptyMap(),
    val people: Map<String, Person> = emptyMap(),
    val subscription: Subscription? = null,
    val linkedLendBorrow: DebtEntry? = null,
    val primaryCurrency: String = "",
    val convertedAmount: BigDecimal? = null,
    val isEditMode: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null,
    val showDeleteDialog: Boolean = false,
    val deleteSuccess: Boolean = false,
    val duplicateSuccess: Boolean = false,
    val showMarkAsLoanSheet: Boolean = false,
    val showUnmarkLoanConfirm: Boolean = false,
    val markAsLoanSuccess: Boolean = false,
    val markAsLoanError: String? = null,
    val recurring: Boolean = false,
    val cycle: Cycle = Cycle(1u, CycleUnit.MONTH),
    val isCustomCycle: Boolean = false,
    val customCycleCount: Int = 1,
    val customCycleUnit: String = "month",
    val customCycleEndDate: LocalDate? = null,
)
