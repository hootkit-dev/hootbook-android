package io.github.hootkit_dev.hootbook.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.hootkit_dev.hootbook.data.database.entity.LendBorrowPersonEntity
import io.github.hootkit_dev.hootbook.data.database.entity.LendBorrowTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LendBorrowDao {

    @Query("SELECT * FROM lend_borrow_persons ORDER BY name ASC")
    fun getAllPersons(): Flow<List<LendBorrowPersonEntity>>

    @Query("SELECT * FROM lend_borrow_persons WHERE id = :id")
    fun getPersonById(id: Long): Flow<LendBorrowPersonEntity?>

    @Query("SELECT * FROM lend_borrow_persons WHERE id = :id")
    suspend fun getPersonByIdSync(id: Long): LendBorrowPersonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: LendBorrowPersonEntity): Long

    @Update
    suspend fun updatePerson(person: LendBorrowPersonEntity)

    @Delete
    suspend fun deletePerson(person: LendBorrowPersonEntity)

    @Query("DELETE FROM lend_borrow_persons WHERE id = :id")
    suspend fun deletePersonById(id: Long)

    // Transaction queries
    @Query("SELECT * FROM lend_borrow_transactions WHERE person_id = :personId AND (transaction_id IS NULL OR transaction_id NOT IN (SELECT id FROM transactions WHERE is_deleted = 1)) ORDER BY date DESC")
    fun getTransactionsForPerson(personId: Long): Flow<List<LendBorrowTransactionEntity>>

    @Query("SELECT * FROM lend_borrow_transactions WHERE person_id = :personId AND (transaction_id IS NULL OR transaction_id NOT IN (SELECT id FROM transactions WHERE is_deleted = 1)) ORDER BY date DESC")
    suspend fun getTransactionsForPersonSync(personId: Long): List<LendBorrowTransactionEntity>

    @Query("SELECT * FROM lend_borrow_transactions WHERE (transaction_id IS NULL OR transaction_id NOT IN (SELECT id FROM transactions WHERE is_deleted = 1)) ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<LendBorrowTransactionEntity>>

    @Query("SELECT * FROM lend_borrow_transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): LendBorrowTransactionEntity?

    /** Returns the lend/borrow entry linked to a wallet transaction, if any. */
    @Query("SELECT * FROM lend_borrow_transactions WHERE transaction_id = :transactionId LIMIT 1")
    suspend fun getTransactionByWalletId(transactionId: Long): LendBorrowTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: LendBorrowTransactionEntity): Long

    @Update
    suspend fun updateTransaction(transaction: LendBorrowTransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: LendBorrowTransactionEntity)

    @Query("DELETE FROM lend_borrow_transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("DELETE FROM lend_borrow_transactions")
    suspend fun deleteAllTransactions()

    @Query("DELETE FROM lend_borrow_persons")
    suspend fun deleteAllPersons()

    @Query("SELECT * FROM lend_borrow_persons")
    suspend fun getAllPersonsSync(): List<LendBorrowPersonEntity>
}
