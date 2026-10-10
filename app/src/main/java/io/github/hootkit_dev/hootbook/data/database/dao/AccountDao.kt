package io.github.hootkit_dev.hootbook.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.hootkit_dev.hootbook.data.database.entity.AccountCurrencyEntity
import io.github.hootkit_dev.hootbook.data.database.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE name = :name AND last4 = :last4")
    suspend fun findAccount(name: String, last4: String): AccountEntity?

    @Query("SELECT * FROM accounts ORDER BY name, last4")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY name, last4")
    suspend fun getAccounts(): List<AccountEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAccount(account: AccountEntity): Long

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE name = :name AND last4 = :last4")
    suspend fun deleteAccount(name: String, last4: String)

    @Query("SELECT * FROM account_currencies WHERE account_id = :accountId ORDER BY created_at, currency")
    suspend fun getCurrencies(accountId: Long): List<AccountCurrencyEntity>

    @Query("SELECT * FROM account_currencies ORDER BY account_id, created_at, currency")
    suspend fun getAllCurrencies(): List<AccountCurrencyEntity>

    /** Adds a currency to an account; one it already holds is left as it is. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCurrency(currency: AccountCurrencyEntity)

    @Update
    suspend fun updateCurrency(currency: AccountCurrencyEntity)
}
