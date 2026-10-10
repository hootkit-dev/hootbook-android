package io.github.hootkit_dev.hootbook.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.BudgetCategoryLimitEntity
import io.github.hootkit_dev.hootbook.data.database.entity.BudgetEntity
import io.github.hootkit_dev.hootbook.data.database.entity.CategoryEntity
import io.github.hootkit_dev.hootbook.data.database.entity.QuickTemplateEntity
import io.github.hootkit_dev.hootbook.data.database.entity.SyncOutboxEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The sync triggers queue every local change once, and nothing while remote changes are applied. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncCaptureTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val at = LocalDateTime.of(2026, 9, 1, 9, 0)
    private lateinit var db: HootbookDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, HootbookDatabase::class.java)
            .addCallback(SyncTriggers.Callback).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    private fun transaction(hash: String, merchant: String = "Shop") = TransactionEntity(
        amount = BigDecimal("10"), merchantName = merchant, category = "Shopping",
        transactionType = TransactionType.EXPENSE, dateTime = at, transactionHash = hash, currency = "CNY"
    )

    private suspend fun outbox(): List<SyncOutboxEntity> = db.syncDao().pending()

    private fun syncColumns(table: String, id: Long): Pair<String, Long> =
        db.openHelper.writableDatabase.query("SELECT sync_id, sync_updated_at FROM `$table` WHERE id = ?", arrayOf<Any>(id))
            .use { it.moveToFirst(); it.getString(0) to it.getLong(1) }

    @Test fun anInsertGetsASyncIdAndQueuesAnUpsert() = runTest {
        val id = db.transactionDao().insertTransaction(transaction("a"))
        val saved = db.transactionDao().getTransactionById(id)!!
        assertEquals(32, saved.syncId.length)
        assertTrue(saved.syncUpdatedAt > 0)
        assertEquals(
            listOf(Triple("transactions", saved.syncId, SyncTriggers.OP_UPSERT)),
            outbox().map { Triple(it.tableName, it.syncId, it.op) }
        )
    }

    @Test fun updatesKeepOneEntryAndTheSyncId() = runTest {
        val id = db.transactionDao().insertTransaction(transaction("a"))
        val saved = db.transactionDao().getTransactionById(id)!!
        db.transactionDao().updateTransaction(saved.copy(merchantName = "Cafe"))
        // An entity built afresh carries no sync id: the row keeps its own
        db.transactionDao().updateTransaction(saved.copy(merchantName = "Bar", syncId = ""))
        val updated = db.transactionDao().getTransactionById(id)!!
        assertEquals(saved.syncId, updated.syncId)
        assertEquals("Bar", updated.merchantName)
        assertTrue(updated.syncUpdatedAt > saved.syncUpdatedAt)
        assertEquals(listOf(saved.syncId to SyncTriggers.OP_UPSERT), outbox().map { it.syncId to it.op })
    }

    @Test fun aDeleteAfterAnInsertLeavesTheDelete() = runTest {
        val id = db.transactionDao().insertTransaction(transaction("a"))
        val syncId = db.transactionDao().getTransactionById(id)!!.syncId
        db.transactionDao().deleteTransactionById(id)
        assertEquals(listOf(syncId to SyncTriggers.OP_DELETE), outbox().map { it.syncId to it.op })
    }

    @Test fun newRowsAlwaysGetDistinctSyncIds() = runTest {
        val first = db.categoryDao().insertCategory(CategoryEntity(name = "Food", color = "#000000"))
        val original = db.categoryDao().getCategoryById(first)!!
        // A copy of a saved row carries its sync id; the copy gets its own
        val copy = db.categoryDao().insertCategory(original.copy(id = 0, name = "Food 2"))
        (1..20).forEach { db.categoryDao().insertCategory(CategoryEntity(name = "C$it", color = "#000000")) }
        val ids = db.categoryDao().getAllCategories().first().map { it.syncId }
        assertEquals(22, ids.size)
        assertTrue(ids.all { it.length == 32 })
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(original.syncId, db.categoryDao().getCategoryById(first)!!.syncId)
        assertNotEquals(original.syncId, db.categoryDao().getCategoryById(copy)!!.syncId)
    }

    @Test fun aNewBalanceRowCopiedFromTheLatestLeavesTheLatestAlone() = runTest {
        val dao = db.accountBalanceDao()
        dao.insertBalance(
            AccountBalanceEntity(bankName = "Bank", accountLast4 = "1234", balance = BigDecimal("100"),
                timestamp = at, sourceType = "MANUAL", currency = "CNY")
        )
        val latest = dao.getLatestBalance("Bank", "1234")!!
        assertEquals(32, latest.syncId.length)
        // As a balance calibration does: the latest row, copied under id 0 (insert is REPLACE)
        dao.insertBalance(latest.copy(id = 0, balance = BigDecimal("50"), timestamp = at.plusDays(1)))
        val rows = dao.getAllBalances().first()
        assertEquals(2, rows.size)
        assertEquals(2, rows.map { it.syncId }.toSet().size)
        assertTrue(rows.any { it.syncId == latest.syncId && it.balance.compareTo(BigDecimal("100")) == 0 })
        // The account and its currency are synced records too
        val tables = outbox().map { it.tableName }.toSet()
        assertEquals(setOf("accounts", "account_currencies", "account_balances"), tables)
    }

    @Test fun aRowReplacedUnderItsIdQueuesTheOldOnesDelete() = runTest {
        val dao = db.quickTemplateDao()
        val id = dao.insert(QuickTemplateEntity(name = "Coffee", merchantName = "Cafe", category = "Food & Drinks"))
        val old = dao.getById(id)!!.syncId
        // INSERT OR REPLACE of an entity built afresh, under the same id
        dao.insert(QuickTemplateEntity(id = id, name = "Tea", merchantName = "Cafe", category = "Food & Drinks"))
        val new = dao.getById(id)!!.syncId
        assertNotEquals(old, new)
        assertEquals(
            setOf(old to SyncTriggers.OP_DELETE, new to SyncTriggers.OP_UPSERT),
            outbox().map { it.syncId to it.op }.toSet()
        )
    }

    @Test fun cascadedDeletesAreQueued() = runTest {
        val budget = db.budgetDao().insertBudget(BudgetEntity(name = "Month", amount = BigDecimal("100"), year = 2026, month = 9))
        db.budgetDao().insertCategoryLimit(BudgetCategoryLimitEntity(budgetId = budget, categoryName = "Food", limitAmount = BigDecimal("10")))
        val limit = db.budgetDao().getCategoryLimitsForBudgetSync(budget).single().syncId
        db.budgetDao().deleteBudget(budget)
        assertTrue(outbox().any { it.tableName == "budget_category_limits" && it.syncId == limit && it.op == SyncTriggers.OP_DELETE })
    }

    @Test fun nothingIsCapturedWhileApplyingRemoteChanges() = runTest {
        val sql = db.openHelper.writableDatabase
        db.syncDao().setApplyingRemote(true)
        assertEquals(true, db.syncDao().isApplyingRemote())
        sql.execSQL(
            "INSERT INTO lend_borrow_persons (id, name, color, is_archived, created_at, updated_at, sync_id, sync_updated_at) " +
                "VALUES (7, 'Alex', '#000000', 0, '2026-09-01T09:00', '2026-09-01T09:00', 'remote-id', 5)"
        )
        sql.execSQL("UPDATE lend_borrow_persons SET name = 'Alexa', sync_updated_at = 6 WHERE id = 7")
        assertEquals("remote-id" to 6L, syncColumns("lend_borrow_persons", 7))
        sql.execSQL("DELETE FROM lend_borrow_persons WHERE id = 7")
        assertTrue(outbox().isEmpty())

        db.syncDao().setApplyingRemote(false)
        db.transactionDao().insertTransaction(transaction("a"))
        assertEquals(1, outbox().size)
    }

    @Test fun aSentEntryStaysWhenTheRecordChangedAgain() = runTest {
        val id = db.transactionDao().insertTransaction(transaction("a"))
        val sent = outbox().single()
        db.transactionDao().deleteTransactionById(id)
        assertEquals(0, db.syncDao().markSent(sent))
        val delete = outbox().single()
        assertEquals(SyncTriggers.OP_DELETE, delete.op)
        assertEquals(1, db.syncDao().markSent(delete))
        assertTrue(outbox().isEmpty())
    }
}
