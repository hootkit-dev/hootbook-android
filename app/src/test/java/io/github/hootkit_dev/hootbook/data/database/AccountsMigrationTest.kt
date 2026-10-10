package io.github.hootkit_dev.hootbook.data.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Builds a version 66 database from its exported schema, fills it the way the app did, then lets
 * Room open it at the current version (through 67 and 68): Room checks the migrated tables against the entities, and the test checks
 * what accounts, currencies and links came out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountsMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    private fun createVersion66(fill: SupportSQLiteDatabase.() -> Unit) {
        context.deleteDatabase(name)
        val schema = Json.parseToJsonElement(
            File("schemas/io.github.hootkit_dev.hootbook.data.database.HootbookDatabase/66.json").readText()
        ).jsonObject["database"]!!.jsonObject
        val statements = buildList {
            schema["entities"]!!.jsonArray.forEach { entity ->
                val table = entity.jsonObject["tableName"]!!.jsonPrimitive.content
                add(entity.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity.jsonObject["indices"]?.jsonArray?.forEach {
                    add(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema["views"]?.jsonArray?.forEach {
                val view = it.jsonObject["viewName"]!!.jsonPrimitive.content
                add("CREATE VIEW `$view` AS " + it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${VIEW_NAME}", view))
            }
            schema["setupQueries"]!!.jsonArray.forEach { add(it.jsonPrimitive.content) }
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(66) {
                    override fun onCreate(db: SupportSQLiteDatabase) = statements.forEach(db::execSQL)
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }
            ).build()
        )
        helper.writableDatabase.fill()
        helper.close()
    }

    private fun SupportSQLiteDatabase.balance(
        id: Long, bank: String, last4: String, balance: String, time: String, source: String,
        currency: String, transactionId: Long? = null, card: Int = 0, icon: String = ""
    ) = execSQL(
        "INSERT INTO account_balances (id, bank_name, account_last4, balance, timestamp, transaction_id, " +
            "is_credit_card, source_type, created_at, currency, icon_name) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
        arrayOf<Any?>(id, bank, last4, balance, time, transactionId, card, source, time, currency, icon)
    )

    private fun SupportSQLiteDatabase.transaction(
        id: Long, type: String, amount: String, bank: String, last4: String, currency: String, toLast4: String? = null
    ) = execSQL(
        "INSERT INTO transactions (id, amount, merchant_name, category, transaction_type, date_time, bank_name, " +
            "account_number, to_account, transaction_hash, is_recurring, created_at, updated_at, currency) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,0,?,?,?)",
        arrayOf<Any?>(id, amount, "m", "c", type, "2026-09-02T10:00", bank, last4, toLast4, "h$id", "2026-09-02T10:00", "2026-09-02T10:00", currency)
    )

    @Test fun accountsCurrenciesAndLinksComeOutOfTheOldRows() = runTest {
        createVersion66 {
            // A CNY credit card with a purchase
            balance(1, "招商银行", "1234", "0", "2026-09-01T09:00", "MANUAL", "CNY", card = 1, icon = "ic_cmb")
            transaction(10, "EXPENSE", "100", "招商银行", "1234", "CNY")
            balance(2, "招商银行", "1234", "100", "2026-09-02T10:00", "TRANSACTION_CALCULATED", "CNY", transactionId = 10, card = 1, icon = "ic_cmb")
            // A HKD bank and a USD broker; the old transfer stamped HKD on the broker's row
            balance(3, "汇丰香港", "8888", "20000", "2026-09-01T09:00", "MANUAL", "HKD")
            balance(4, "盈透证券", "0001", "250", "2026-09-01T09:00", "MANUAL", "USD")
            transaction(11, "TRANSFER", "5000", "汇丰香港", "8888", "HKD", toLast4 = "0001")
            balance(5, "汇丰香港", "8888", "15000", "2026-09-02T10:00", "TRANSACTION_CALCULATED", "HKD", transactionId = 11)
            balance(6, "盈透证券", "0001", "5250", "2026-09-02T10:00", "TRANSACTION_CALCULATED", "HKD", transactionId = 11)
        }

        val db = Room.databaseBuilder(context, HootbookDatabase::class.java, name)
            .addMigrations(*HootbookDatabase.MIGRATIONS)
            .addCallback(SyncTriggers.Callback).allowMainThreadQueries()
            .build()
        try {
            val accounts = db.accountDao().getAccounts().associateBy { it.name }
            assertEquals(setOf("招商银行", "汇丰香港", "盈透证券"), accounts.keys)
            val card = accounts.getValue("招商银行")
            assertEquals(true, card.isCreditCard)
            assertEquals("ic_cmb", card.iconName)
            // The broker keeps the currency it was set up with, and only that one
            val broker = accounts.getValue("盈透证券")
            assertEquals("USD", broker.mainCurrency)
            assertEquals(listOf("USD"), db.accountDao().getCurrencies(broker.id).map { it.currency })

            val rows = db.accountBalanceDao().getBalanceHistoryForAccount("盈透证券", "0001")
            assertEquals(setOf("USD"), rows.map { it.currency }.toSet())
            assertEquals(setOf(broker.id), rows.map { it.accountId }.toSet())

            val transfer = db.transactionDao().getTransactionById(11)!!
            assertEquals(accounts.getValue("汇丰香港").id, transfer.accountId)
            assertEquals(broker.id, transfer.toAccountId)
            assertEquals("USD", transfer.toCurrency)
            val purchase = db.transactionDao().getTransactionById(10)!!
            assertEquals(card.id, purchase.accountId)
            assertNull(purchase.toAccountId)

            // Accounts read the way the screens read them
            val latest = db.accountBalanceDao().getAllLatestBalances().first().associateBy { it.bankName }
            assertEquals("100", latest.getValue("招商银行").balance.stripTrailingZeros().toPlainString())
            assertEquals("USD", latest.getValue("盈透证券").currency)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
