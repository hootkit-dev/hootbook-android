package io.github.hootkit_dev.hootbook.domain.usecase

import io.github.hootkit_dev.hootbook.data.database.entity.LendBorrowPersonEntity
import io.github.hootkit_dev.hootbook.data.database.entity.LendBorrowTransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.LendBorrowType
import io.github.hootkit_dev.hootbook.data.repository.LendBorrowRepository
import io.github.hootkit_dev.hootbook.domain.model.LendBorrowPerson
import io.github.hootkit_dev.hootbook.domain.model.LendBorrowSummary
import io.github.hootkit_dev.hootbook.domain.model.LendBorrowTransactionItem
import io.github.hootkit_dev.hootbook.domain.model.PersonCategory
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject

class GetLendBorrowSummaryUseCase @Inject constructor(
    private val repository: LendBorrowRepository
) {
    operator fun invoke(): Flow<LendBorrowSummary> = repository.getSummary()
}

class GetLendBorrowPersonsUseCase @Inject constructor(
    private val repository: LendBorrowRepository
) {
    operator fun invoke(): Flow<List<LendBorrowPerson>> = repository.getPersons()
}

class GetPersonDetailUseCase @Inject constructor(
    private val repository: LendBorrowRepository
) {
    fun getPerson(personId: Long): Flow<LendBorrowPerson?> = repository.getPersonById(personId)
    fun getTransactions(personId: Long): Flow<List<LendBorrowTransactionItem>> = repository.getTransactionsForPerson(personId)
}

class AddEditLendBorrowPersonUseCase @Inject constructor(
    private val repository: LendBorrowRepository
) {
    suspend fun addPerson(
        name: String,
        phoneNumber: String? = null,
        notes: String? = null,
        color: String = "#4CAF50",
        avatar: String? = null,
        category: PersonCategory? = null
    ): Long {
        val entity = LendBorrowPersonEntity(
            name = name.trim(),
            phoneNumber = phoneNumber?.trim()?.ifBlank { null },
            notes = notes?.trim()?.ifBlank { null },
            color = color,
            avatar = avatar,
            category = category?.name
        )
        return repository.insertPerson(entity)
    }

    suspend fun updatePerson(
        id: Long,
        name: String,
        phoneNumber: String? = null,
        notes: String? = null,
        color: String = "#4CAF50",
        avatar: String? = null,
        category: PersonCategory? = null,
        isArchived: Boolean = false
    ) {
        val entity = LendBorrowPersonEntity(
            id = id,
            name = name.trim(),
            phoneNumber = phoneNumber?.trim()?.ifBlank { null },
            notes = notes?.trim()?.ifBlank { null },
            color = color,
            avatar = avatar,
            category = category?.name,
            isArchived = isArchived
        )
        repository.updatePerson(entity)
    }

    suspend fun deletePerson(personId: Long) {
        repository.deletePerson(personId)
    }
}

class AddEditLendBorrowTransactionUseCase @Inject constructor(
    private val repository: LendBorrowRepository
) {
    suspend fun addTransaction(
        personId: Long,
        type: LendBorrowType,
        amount: BigDecimal,
        title: String,
        dueDate: LocalDateTime? = null,
        date: LocalDateTime = LocalDateTime.now(),
        transactionId: Long? = null,
        accountId: Long? = null,
        category: String? = null,
        merchant: String? = null,
        attachments: List<String> = emptyList()
    ): Long {
        val entity = LendBorrowTransactionEntity(
            personId = personId,
            type = type,
            amount = amount,
            title = title.trim(),
            dueDate = dueDate,
            date = date,
            transactionId = transactionId,
            accountId = accountId,
            category = category,
            merchant = merchant,
            attachments = attachments
        )
        return repository.insertTransaction(entity)
    }

    suspend fun updateTransaction(
        id: Long,
        personId: Long,
        type: LendBorrowType,
        amount: BigDecimal,
        title: String,
        dueDate: LocalDateTime? = null,
        isSettled: Boolean = false,
        date: LocalDateTime = LocalDateTime.now(),
        transactionId: Long? = null,
        accountId: Long? = null,
        category: String? = null,
        merchant: String? = null,
        attachments: List<String> = emptyList()
    ) {
        val entity = LendBorrowTransactionEntity(
            id = id,
            personId = personId,
            type = type,
            amount = amount,
            title = title.trim(),
            dueDate = dueDate,
            isSettled = isSettled,
            date = date,
            transactionId = transactionId,
            accountId = accountId,
            category = category,
            merchant = merchant,
            attachments = attachments
        )
        repository.updateTransaction(entity)
    }

    suspend fun deleteTransaction(transactionId: Long) {
        repository.deleteTransaction(transactionId)
    }
}

class SettleLendBorrowUseCase @Inject constructor(
    private val repository: LendBorrowRepository
) {
    suspend fun settle(
        personId: Long,
        amount: BigDecimal,
        title: String = "Settlement",
        isLentSettlement: Boolean,
        accountId: Long? = null
    ): Long {
        return repository.settlePerson(
            personId = personId,
            amount = amount,
            title = title,
            isLentSettlement = isLentSettlement,
            accountId = accountId
        )
    }
}

class GetLendBorrowEntryForTransactionUseCase @Inject constructor(
    private val repository: LendBorrowRepository
) {
    /** Returns the lend/borrow entry linked to a wallet transaction, if any. */
    suspend operator fun invoke(transactionId: Long): LendBorrowTransactionItem? =
        repository.getEntryForTransaction(transactionId)
}

class MarkTransactionAsLoanUseCase @Inject constructor(
    private val repository: LendBorrowRepository
) {
    /**
     * Marks an existing wallet transaction as a loan by linking it to a new
     * lend/borrow entry. The wallet transaction itself is left untouched.
     */
    suspend operator fun invoke(
        transactionId: Long,
        personId: Long,
        type: LendBorrowType,
        amount: BigDecimal,
        currency: String,
        title: String,
        dueDate: LocalDateTime? = null,
        date: LocalDateTime = LocalDateTime.now(),
        accountId: Long? = null,
        category: String? = null,
        merchant: String? = null,
        attachments: List<String> = emptyList()
    ): Long = repository.createEntryFromTransaction(
        transactionId = transactionId,
        personId = personId,
        type = type,
        amount = amount,
        currency = currency,
        title = title,
        dueDate = dueDate,
        date = date,
        accountId = accountId,
        category = category,
        merchant = merchant,
        attachments = attachments
    )
}

class UnmarkTransactionAsLoanUseCase @Inject constructor(
    private val repository: LendBorrowRepository
) {
    /** Removes the loan link from a wallet transaction, keeping the transaction. */
    suspend operator fun invoke(transactionId: Long) =
        repository.deleteEntryForTransaction(transactionId)
}
