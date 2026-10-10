package io.github.hootkit_dev.hootbook.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.hootkit_dev.hootbook.data.database.entity.QuickTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickTemplateDao {

    @Query("SELECT * FROM quick_templates ORDER BY sort_order ASC, created_at ASC")
    fun getAll(): Flow<List<QuickTemplateEntity>>

    @Query("SELECT * FROM quick_templates WHERE id = :id")
    suspend fun getById(id: Long): QuickTemplateEntity?

    @Query("SELECT COUNT(*) FROM quick_templates")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: QuickTemplateEntity): Long

    @Update
    suspend fun update(template: QuickTemplateEntity)

    @Query("DELETE FROM quick_templates WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE quick_templates SET bank_name = :newBankName WHERE bank_name = :oldBankName AND account_last4 = :accountLast4")
    suspend fun renameAccountBank(oldBankName: String, accountLast4: String, newBankName: String): Int
}
