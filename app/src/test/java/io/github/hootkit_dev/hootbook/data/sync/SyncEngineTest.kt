package io.github.hootkit_dev.hootbook.data.sync

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.hootkit_dev.hootbook.data.database.HootbookDatabase
import io.github.hootkit_dev.hootbook.data.database.DatabaseCallback
import io.github.hootkit_dev.hootbook.data.database.SyncIds
import io.github.hootkit_dev.hootbook.data.database.SyncTriggers
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import io.github.hootkit_dev.hootbook.data.repository.AccountBalanceRepository
import io.github.hootkit_dev.hootbook.data.repository.SubscriptionRepository
import io.github.hootkit_dev.hootbook.data.repository.TransactionEditor
import io.github.hootkit_dev.hootbook.data.repository.TransactionRepository
import io.github.hootkit_dev.hootbook.domain.usecase.AddTransactionUseCase
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Two devices (in-memory databases) syncing through one in-memory account: what one writes, the
 * other ends up with, records and balances alike, and nothing is written twice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncEngineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val remote = FakeRemoteStore()
    private val key = ByteArray(32) { it.toByte() }
    private val devices = mutableListOf<Device>()
    private val at = "2026-09-01T09:00"

    // A small page by default, so pulls run over several pages and transactions
    inner class Device(val name: String, pageSize: Int = 4) {
        val db: HootbookDatabase = Room.inMemoryDatabaseBuilder(context, HootbookDatabase::class.java)
            .addCallback(SyncTriggers.Callback).allowMainThreadQueries().build()
        val cursor = MemoryCursorStore()
        val engine = SyncEngine(db, remote, key, name, cursor, pageSize = pageSize)
        val sql get() = db.openHelper.writableDatabase
        val balances = AccountBalanceRepository(db.accountBalanceDao(), context)
        val transactions = TransactionRepository(db.transactionDao(), balances)
        val add = AddTransactionUseCase(transactions, SubscriptionRepository(db.subscriptionDao()), balances, db)

        init { devices += this }

        suspend fun sync() { engine.push(); engine.pull() }

        fun insert(table: String, vararg values: Pair<String, Any?>): Long = sql.insert(table, SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
            values.forEach { (k, v) ->
                when (v) {
                    null -> putNull(k)
                    is Long -> put(k, v)
                    is Int -> put(k, v)
                    is Boolean -> put(k, if (v) 1 else 0)
                    else -> put(k, v.toString())
                }
            }
        })

        fun count(table: String, where: String = "1"): Long =
            sql.query("SELECT COUNT(*) FROM `$table` WHERE $where").use { it.moveToFirst(); it.getLong(0) }

        fun string(query: String): String? = sql.query(query).use { if (it.moveToFirst()) it.getString(0) else null }

        /** Every record as its payload, by document id: references appear as sync ids. */
        fun payloads(): Map<String, JsonObject> {
            val store = SyncLocalStore(sql)
            return SyncSchema.TABLES.flatMap { table ->
                store.syncIdsOf(table.name).map { SyncSchema.docId(table.name, it) to store.encode(table, it)!! }
            }.toMap()
        }

        /** Balance per account and currency, by name: local ids differ between devices. */
        suspend fun pockets(): Map<String, String> {
            val accounts = db.accountBalanceDao().observeAccountRows().first().associateBy { it.id }
            return balances.pocketBalances().associate {
                "${accounts[it.accountId]?.name}/${it.currency}" to it.balance.stripTrailingZeros().toPlainString()
            }
        }

        /** Moves this device's autoincrement ids past the other's, so equal ids would be a bug. */
        fun shiftIds() {
            sql.execSQL("UPDATE sync_control SET applying_remote = 1")
            listOf("accounts", "categories", "budgets", "lend_borrow_persons", "transactions").forEach { table ->
                sql.execSQL("INSERT INTO sqlite_sequence (name, seq) SELECT '$table', 100 WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = '$table')")
                sql.execSQL("UPDATE sqlite_sequence SET seq = 100 WHERE name = '$table'")
            }
            sql.execSQL("UPDATE sync_control SET applying_remote = 0")
        }
    }

    @After fun tearDown() = devices.forEach { it.db.close() }

    /** One record in every synced table, references included, and local-only values. */
    private fun fill(d: Device) {
        val cmb = d.insert(
            "accounts", "name" to "招商银行", "last4" to "1234", "main_currency" to "CNY", "is_credit_card" to true,
            "credit_limit" to "50000.00", "icon_res_id" to 2131230000, "icon_name" to "bank_cmb", "color" to "#C8102E",
            "created_at" to at, "statement_day" to 5, "due_day" to 23
        )
        val hsbc = d.insert("accounts", "name" to "HSBC", "last4" to "8888", "main_currency" to "HKD", "created_at" to at)
        d.insert("account_currencies", "account_id" to cmb, "currency" to "CNY", "created_at" to at)
        d.insert("account_currencies", "account_id" to hsbc, "currency" to "HKD", "created_at" to at)
        d.insert("account_currencies", "account_id" to hsbc, "currency" to "USD", "credit_limit" to "1000", "created_at" to at)
        val coffee = d.insert(
            "categories", "name" to "Coffee", "color" to "#6F4E37", "icon_name" to "type_food_hot_beverage",
            "description" to "咖啡", "is_system" to false, "is_income" to false, "display_order" to 40,
            "created_at" to "2026-09-01 09:00:00", "updated_at" to "2026-09-01T09:00:00.123456"
        )
        d.insert(
            "subcategories", "category_id" to coffee, "name" to "Latte", "icon_name" to "x", "color" to "#111111",
            "created_at" to at, "updated_at" to at
        )
        val budget = d.insert(
            "budgets", "name" to "Food", "amount" to "3000", "year" to 2026, "month" to 9, "currency" to "CNY",
            "created_at" to at, "updated_at" to at, "start_date" to at, "end_date" to "2026-10-01T09:00",
            "account_ids" to "招商银行:1234"
        )
        d.insert("budget_category_limits", "budget_id" to budget, "category_name" to "Coffee", "limit_amount" to "300.5", "created_at" to at, "updated_at" to at)
        val alex = d.insert(
            "lend_borrow_persons", "name" to "Alex", "phone_number" to "+852 1234", "color" to "#000000",
            "avatar" to "file:///data/avatars/alex.jpg", "created_at" to at, "updated_at" to at
        )
        d.insert(
            "cards", "card_last4" to "9999", "card_type" to "CREDIT", "bank_name" to "招商银行", "account_last4" to "1234",
            "last_balance" to "-12.5", "last_balance_date" to "2026-09-02T10:00", "created_at" to at, "updated_at" to at, "currency" to "CNY"
        )
        d.insert(
            "subscriptions", "merchant_name" to "Music", "amount" to "15", "next_payment_date" to "2026-10-01", "state" to "ACTIVE",
            "created_at" to at, "updated_at" to at, "currency" to "CNY", "last_paid_date" to "2026-09-01"
        )
        d.insert(
            "quick_templates", "name" to "Coffee", "merchant_name" to "Manner", "category" to "Coffee", "transaction_type" to "EXPENSE",
            "amount" to "18", "prefill_amount" to true, "bank_name" to "招商银行", "account_last4" to "1234", "created_at" to at, "updated_at" to at
        )
        val transfer = d.insert(
            "transactions", "amount" to "1000", "merchant_name" to "Transfer", "category" to "Transfer", "transaction_type" to "TRANSFER",
            "date_time" to "2026-09-03T12:30:15", "bank_name" to "招商银行", "account_number" to "1234", "transaction_hash" to "h1",
            "is_recurring" to false, "created_at" to at, "updated_at" to at, "currency" to "CNY", "to_amount" to "1085.20",
            "account_id" to cmb, "to_account_id" to hsbc, "to_currency" to "HKD", "attachments" to "file:///receipt.jpg"
        )
        d.insert(
            "account_balances", "icon_res_id" to 2131230000, "icon_name" to "bank_cmb", "bank_name" to "招商银行", "account_last4" to "1234",
            "balance" to "-1000", "timestamp" to "2026-09-03T12:30:15", "transaction_id" to transfer, "is_credit_card" to true,
            "source_type" to "TRANSACTION_CALCULATED", "created_at" to at, "currency" to "CNY", "account_id" to cmb
        )
        d.insert(
            "lend_borrow_transactions", "person_id" to alex, "transaction_id" to transfer, "type" to "LENT", "amount" to "200",
            "currency" to "CNY", "title" to "Lunch", "date" to at, "created_at" to at, "updated_at" to at, "account_id" to cmb,
            "attachments" to "[\"file:///x.jpg\"]"
        )
    }

    @Test fun everyTableRoundTripsWithItsReferencesTranslated() = runTest {
        val a = Device("a")
        val b = Device("b").apply { shiftIds() }
        fill(a)
        assertEquals(16, a.engine.push())
        // Nothing local-only and no local id leaves the device
        remote.docs.values.forEach { record ->
            val plain = SyncCrypto.open(key, record.payload!!, record.docId).toString(Charsets.UTF_8)
            listOf("\"id\"", "attachments", "avatar", "icon_res_id", "file:///").forEach { assertFalse("$it in $plain", plain.contains(it)) }
        }
        b.engine.pull()

        assertEquals(a.payloads(), b.payloads())
        SyncSchema.TABLES.forEach { assertEquals(it.name, a.count(it.name), b.count(it.name)) }
        // References point at the other device's own rows
        assertNotEquals(a.string("SELECT account_id FROM transactions"), b.string("SELECT account_id FROM transactions"))
        assertEquals("HSBC", b.string("SELECT a.name FROM transactions t JOIN accounts a ON a.id = t.to_account_id"))
        assertEquals("Coffee", b.string("SELECT c.name FROM subcategories s JOIN categories c ON c.id = s.category_id"))
        assertEquals("Lunch", b.string("SELECT l.title FROM lend_borrow_transactions l JOIN transactions t ON t.id = l.transaction_id JOIN lend_borrow_persons p ON p.id = l.person_id"))
        // Stored values are copied as they are: decimals, both date formats, booleans
        assertEquals("1085.20", b.string("SELECT to_amount FROM transactions"))
        assertEquals("2026-09-01 09:00:00", b.string("SELECT created_at FROM categories"))
        assertEquals("2026-09-01T09:00:00.123456", b.string("SELECT updated_at FROM categories"))
        assertEquals("1", b.string("SELECT is_credit_card FROM accounts WHERE last4 = '1234'"))
        // Local-only columns keep the receiver's own value
        assertEquals("", b.string("SELECT attachments FROM transactions"))
        assertNull(b.string("SELECT avatar FROM lend_borrow_persons"))
        // Applying remote changes queues nothing
        assertEquals(0, b.db.syncDao().pendingCount())
        assertEquals(0, b.db.syncDao().inbox().size)
    }

    @Test fun addsEditsAndDeletesPropagate() = runTest {
        val a = Device("a")
        val b = Device("b").apply { shiftIds() }
        val person = a.insert("lend_borrow_persons", "name" to "Alex", "color" to "#000000", "created_at" to at, "updated_at" to at)
        a.sync(); b.sync()
        assertEquals("Alex", b.string("SELECT name FROM lend_borrow_persons"))

        a.sql.execSQL("UPDATE lend_borrow_persons SET name = 'Alex Chan' WHERE id = $person")
        a.sync(); b.sync()
        assertEquals("Alex Chan", b.string("SELECT name FROM lend_borrow_persons"))

        // And back: an edit on b reaches a
        b.sql.execSQL("UPDATE lend_borrow_persons SET notes = '同事'")
        b.sync(); a.sync()
        assertEquals("同事", a.string("SELECT notes FROM lend_borrow_persons"))

        a.sql.execSQL("DELETE FROM lend_borrow_persons")
        a.sync(); b.sync()
        assertEquals(0, b.count("lend_borrow_persons"))
        // A delete keeps its document, as a tombstone without payload
        val tombstone = remote.docs.values.single()
        assertTrue(tombstone.deleted)
        assertNull(tombstone.payload)
        assertEquals(0, a.db.syncDao().pendingCount() + b.db.syncDao().pendingCount())
    }

    @Test fun balancesEndEqualOnBothDevicesAndAreNotDoubled() = runTest {
        val a = Device("a")
        val b = Device("b").apply { shiftIds() }
        val start = LocalDateTime.of(2026, 9, 1, 9, 0)
        a.balances.insertBalance(
            AccountBalanceEntity(bankName = "CMB", accountLast4 = "1234", balance = BigDecimal("1000"), timestamp = start, sourceType = "MANUAL", currency = "CNY")
        )
        val id = a.add.execute(
            amount = BigDecimal("100"), merchant = "Manner", category = "Food", type = TransactionType.EXPENSE,
            date = start.plusDays(1), bankName = "CMB", accountLast4 = "1234", currency = "CNY"
        )
        a.sync(); b.sync()
        assertEquals(mapOf("CMB/CNY" to "900"), a.pockets())
        assertEquals(a.pockets(), b.pockets())
        assertEquals(a.count("account_balances"), b.count("account_balances"))

        // An edit on a: its balance rows change there and are copied, not re-applied, on b
        val original = a.db.transactionDao().getTransactionById(id)!!
        TransactionEditor(a.db).update(original, original.copy(amount = BigDecimal("150")))
        a.sync(); b.sync()
        assertEquals(mapOf("CMB/CNY" to "850"), b.pockets())
        assertEquals(a.count("account_balances"), b.count("account_balances"))

        // A transaction added on b reaches a with its balance
        b.add.execute(
            amount = BigDecimal("50"), merchant = "Salary", category = "Income", type = TransactionType.INCOME,
            date = start.plusDays(2), bankName = "CMB", accountLast4 = "1234", currency = "CNY"
        )
        b.sync(); a.sync()
        assertEquals(mapOf("CMB/CNY" to "900"), a.pockets())
        assertEquals(a.pockets(), b.pockets())

        // A delete on a
        a.transactions.deleteTransaction(a.db.transactionDao().getTransactionById(id)!!, hardDelete = true)
        a.sync(); b.sync()
        assertEquals(mapOf("CMB/CNY" to "1050"), a.pockets())
        assertEquals(a.pockets(), b.pockets())
        assertEquals(a.payloads(), b.payloads())
        assertEquals(0, a.db.syncDao().pendingCount() + b.db.syncDao().pendingCount())
    }

    @Test fun twoDevicesAddingToOneAccountApartEndOnTheSameRightBalance() = runTest {
        val a = Device("a")
        val b = Device("b").apply { shiftIds() }
        val start = LocalDateTime.of(2026, 9, 1, 12, 0)
        a.balances.insertBalance(
            AccountBalanceEntity(bankName = "ICBC", accountLast4 = "1234", balance = BigDecimal("965"), timestamp = start, sourceType = "MANUAL", currency = "CNY")
        )
        a.sync(); b.sync()

        // Both apart: each works from 965, so a writes 945 at 13:00 and b writes 915 at 14:00
        a.add.execute(
            amount = BigDecimal("20"), merchant = "Lunch", category = "Food", type = TransactionType.EXPENSE,
            date = start.plusHours(1), bankName = "ICBC", accountLast4 = "1234", currency = "CNY"
        )
        b.add.execute(
            amount = BigDecimal("50"), merchant = "Taxi", category = "Transport", type = TransactionType.EXPENSE,
            date = start.plusHours(2), bankName = "ICBC", accountLast4 = "1234", currency = "CNY"
        )

        // Until every device has nothing left to send
        repeat(4) { a.sync(); b.sync() }
        assertEquals(mapOf("ICBC/CNY" to "895"), a.pockets())
        assertEquals(a.pockets(), b.pockets())
        assertEquals(a.payloads(), b.payloads())
        assertEquals(0, a.db.syncDao().pendingCount() + b.db.syncDao().pendingCount())
    }

    @Test fun aChildThatArrivesBeforeItsParentWaitsForIt() = runTest {
        val b = Device("b")
        val accountId = SyncIds.newId()
        val transactionId = SyncIds.newId()
        fun sealed(table: String, syncId: String, json: String) =
            RemoteRecord(table, syncId, false, SyncCrypto.seal(key, json.toByteArray(), SyncSchema.docId(table, syncId)), "ios")
        // The transaction first, as a client that does not order its writes might send it
        remote.put(
            sealed(
                "transactions", transactionId,
                """{"amount":"12.50","merchant_name":"Manner","category":"Food","transaction_type":"EXPENSE",
                   "date_time":"2026-09-03T08:00","transaction_hash":"ios-1","is_recurring":false,"is_deleted":false,
                   "created_at":"$at","updated_at":"$at","currency":"CNY","account_id":"$accountId","sync_updated_at":1,
                   "an_unknown_future_column":"ignored"}"""
            )
        )
        b.engine.pull()
        assertEquals(0, b.count("transactions"))
        assertEquals(1, b.db.syncDao().inbox().size)

        remote.put(
            sealed(
                "accounts", accountId,
                """{"name":"Wallet","last4":"0000","main_currency":"CNY","is_credit_card":false,"is_wallet":true,
                   "icon_name":"","color":"#33B5E5","is_sample":false,"created_at":"$at","sync_updated_at":1}"""
            )
        )
        b.engine.pull()
        assertEquals("Wallet", b.string("SELECT a.name FROM transactions t JOIN accounts a ON a.id = t.account_id"))
        assertEquals(0, b.db.syncDao().inbox().size)
        assertEquals(0, b.db.syncDao().pendingCount())
    }

    @Test fun aParentMissingFromThePageIsFetchedAndADeletedOneIsDropped() = runTest {
        val a = Device("a")
        val b = Device("b")
        val person = a.insert("lend_borrow_persons", "name" to "Alex", "color" to "#000000", "created_at" to at, "updated_at" to at)
        a.insert("lend_borrow_transactions", "person_id" to person, "type" to "LENT", "amount" to "5", "currency" to "CNY", "title" to "Tea", "date" to at, "created_at" to at, "updated_at" to at)
        a.engine.push()
        // b starts after the person: it asks the server for it
        b.cursor.cursor = remote.docs.values.first { it.table == "lend_borrow_persons" }.cursor
        remote.put(remote.docs.values.first { it.table == "lend_borrow_transactions" })
        b.engine.pull()
        assertEquals("Tea", b.string("SELECT title FROM lend_borrow_transactions"))
        assertEquals(1, b.count("lend_borrow_persons"))
    }

    @Test fun aChangeNotSentYetWinsOverTheRemoteOne() = runTest {
        val a = Device("a")
        val b = Device("b")
        a.insert("lend_borrow_persons", "name" to "Alex", "color" to "#000000", "created_at" to at, "updated_at" to at)
        a.sync(); b.sync()
        a.sql.execSQL("UPDATE lend_borrow_persons SET name = 'From A'")
        a.engine.push()
        // b changed it too, offline: its own change is pending and stays
        b.sql.execSQL("UPDATE lend_borrow_persons SET name = 'From B'")
        b.engine.pull()
        assertEquals("From B", b.string("SELECT name FROM lend_borrow_persons"))
        // b's change reaches the server last, so it is everyone's
        b.sync(); a.sync()
        assertEquals("From B", a.string("SELECT name FROM lend_borrow_persons"))
    }

    @Test fun twoRecordsWithOneNaturalKeyBecomeOne() = runTest {
        val a = Device("a")
        val b = Device("b")
        // The same account made on both devices while apart, each with a transaction on it
        listOf(a, b).forEach { d ->
            val account = d.insert("accounts", "name" to "ICBC", "last4" to "5678", "main_currency" to "CNY", "created_at" to at)
            d.insert("account_currencies", "account_id" to account, "currency" to "CNY", "created_at" to at)
            d.insert(
                "transactions", "amount" to "1", "merchant_name" to d.name, "category" to "Food", "transaction_type" to "EXPENSE",
                "date_time" to at, "transaction_hash" to "h-${d.name}", "is_recurring" to false, "created_at" to at,
                "updated_at" to at, "currency" to "CNY", "account_id" to account, "bank_name" to "ICBC", "account_number" to "5678"
            )
        }
        a.sync(); b.sync(); a.sync(); b.sync(); a.sync()
        listOf(a, b).forEach { d ->
            assertEquals(d.name, 1, d.count("accounts"))
            assertEquals(d.name, 1, d.count("account_currencies"))
            assertEquals(d.name, 2, d.count("transactions", "account_id = (SELECT id FROM accounts)"))
        }
        assertEquals(a.payloads(), b.payloads())
    }

    @Test fun aRecordMadeAgainUnderTheSameNaturalKeyReplacesTheDeletedOne() = runTest {
        // Both orders of the two ids, and the new record read before the delete
        for (newId in listOf("0".repeat(32), "f".repeat(32))) {
            remote.docs.clear()
            val a = Device("a$newId")
            // One record per page: the new record is read in a page before the old one's delete
            val b = Device("b$newId", pageSize = 1)
            a.insert("accounts", "name" to "BOC", "last4" to "1111", "main_currency" to "CNY", "created_at" to at, "sync_id" to "8".repeat(32))
            a.sync(); b.sync()
            a.sql.execSQL("UPDATE accounts SET last4 = 'tmp'")
            a.engine.push()
            a.insert("accounts", "name" to "BOC", "last4" to "1111", "main_currency" to "CNY", "created_at" to at, "sync_id" to newId)
            a.engine.push()
            a.sql.execSQL("DELETE FROM accounts WHERE last4 = 'tmp'")
            a.engine.push()
            assertTrue(b.engine.pull().held == 0)
            b.sync(); a.sync()
            listOf(a, b).forEach { d ->
                assertEquals(d.name, listOf(newId), d.sql.query("SELECT sync_id FROM accounts").use { c ->
                    buildList { while (c.moveToNext()) add(c.getString(0)) }
                })
            }
            assertTrue(remote.docs.values.none { it.replacedBy != null })
        }
    }

    @Test fun balanceRowsReplacedByAnEditDoNotClash() = runTest {
        // TransactionEditor may delete a balance row and write one with the same account, currency
        // and time; repeated so both orders of the random ids come up
        repeat(6) { round ->
            remote.docs.clear()
            val a = Device("a$round")
            val b = Device("b$round")
            val start = LocalDateTime.of(2026, 9, 1, 9, 0)
            a.balances.insertBalance(
                AccountBalanceEntity(bankName = "CMB", accountLast4 = "1234", balance = BigDecimal("1000"), timestamp = start, sourceType = "MANUAL", currency = "CNY")
            )
            val id = a.add.execute(
                amount = BigDecimal("100"), merchant = "Manner", category = "Food", type = TransactionType.EXPENSE,
                date = start.plusDays(1), bankName = "CMB", accountLast4 = "1234", currency = "CNY"
            )
            a.sync(); b.sync()
            val original = a.db.transactionDao().getTransactionById(id)!!
            TransactionEditor(a.db).update(original, original.copy(amount = BigDecimal("150")))
            a.sync(); b.sync()
            assertEquals(mapOf("CMB/CNY" to "850"), b.pockets())
            assertEquals(a.payloads(), b.payloads())
        }
    }

    // ---- first sync -------------------------------------------------------------------------

    private fun seed(d: Device) = DatabaseCallback(context).seed(d.sql)

    @Test fun anEmptyCloudGetsEverything() = runTest {
        val a = Device("a")
        seed(a)
        fill(a)
        assertEquals(FirstSyncCase.CLOUD_EMPTY, a.engine.firstSyncCase())
        a.engine.uploadAll()
        assertEquals(a.payloads().keys, remote.docs.keys)
        assertEquals(0, a.db.syncDao().pendingCount())
    }

    @Test fun aFreshDeviceTakesTheCloudWithoutDuplicatingBuiltInCategories() = runTest {
        val a = Device("a")
        val b = Device("b").apply { shiftIds() }
        seed(a); seed(b)
        val seeded = a.count("categories")
        assertTrue(seeded > 10)
        // The same built-in records on both devices
        assertEquals(a.payloads().keys, b.payloads().keys)
        a.sql.execSQL("UPDATE categories SET color = '#000000' WHERE name = 'Travel'")
        a.sql.execSQL("DELETE FROM categories WHERE name = 'Bill'")
        fill(a)
        a.engine.uploadAll()

        assertTrue(b.engine.isLocalPristine())
        assertEquals(FirstSyncCase.LOCAL_PRISTINE, b.engine.firstSyncCase())
        b.engine.replaceLocalWithCloud()
        assertEquals(a.payloads(), b.payloads())
        // The seeded ones once, less the one a deleted, plus a's own
        assertEquals(seeded - 1 + 1, b.count("categories"))
        assertEquals("#000000", b.string("SELECT color FROM categories WHERE name = 'Travel'"))
        assertEquals(0, b.db.syncDao().pendingCount())
    }

    @Test fun mergingKeepsBothSidesAndTheBuiltInCategoriesOnce() = runTest {
        val a = Device("a")
        val b = Device("b").apply { shiftIds() }
        seed(a); seed(b)
        val seeded = a.count("categories")
        val seededSubs = a.count("subcategories")
        fill(a)
        a.engine.uploadAll()
        // b has data of its own: one account like a's (same name and last 4) and one of its own
        b.insert("accounts", "name" to "招商银行", "last4" to "1234", "main_currency" to "CNY", "created_at" to at)
        b.insert("accounts", "name" to "Cash", "last4" to "0000", "main_currency" to "CNY", "is_wallet" to true, "created_at" to at)
        b.insert("categories", "name" to "Pets", "color" to "#123456", "is_system" to false, "is_income" to false, "display_order" to 50, "created_at" to at, "updated_at" to at)
        assertEquals(FirstSyncCase.BOTH_HAVE_DATA, b.engine.firstSyncCase())

        b.engine.merge()
        a.engine.pull()
        a.sync(); b.sync()
        listOf(a, b).forEach { d ->
            assertEquals(d.name, seeded + 2, d.count("categories"))
            assertEquals(d.name, seededSubs + 1, d.count("subcategories"))
            assertEquals(d.name, 3, d.count("accounts"))
            assertEquals(d.name, 1, d.count("accounts", "name = '招商银行'"))
        }
        assertEquals(a.payloads(), b.payloads())
    }

    @Test fun replacingDropsThisDevicesDataForTheCloudsCopy() = runTest {
        val a = Device("a")
        val b = Device("b")
        fill(a)
        a.engine.uploadAll()
        b.insert("accounts", "name" to "Cash", "last4" to "0000", "main_currency" to "CNY", "created_at" to at)
        assertEquals(FirstSyncCase.BOTH_HAVE_DATA, b.engine.firstSyncCase())
        b.engine.replaceLocalWithCloud()
        assertEquals(a.payloads(), b.payloads())
        assertEquals(0, b.count("accounts", "name = 'Cash'"))
        // Replacing deletes nothing in the cloud
        assertEquals(0, b.db.syncDao().pendingCount())
        assertTrue(remote.docs.values.none { it.deleted })
    }

    @Test fun recordsOfANewerVersionWaitAndUnreadableOnesAreCounted() = runTest {
        val b = Device("b")
        remote.put(RemoteRecord("accounts", SyncIds.newId(), false, "AAAA", "x", version = SyncSchema.VERSION + 1))
        remote.put(RemoteRecord("accounts", SyncIds.newId(), false, SyncCrypto.seal(ByteArray(32), "{}".toByteArray(), "accounts_0"), "x"))
        val result = b.engine.pull()
        assertEquals(1, result.newerVersion)
        assertEquals(1, result.unreadable)
        assertEquals(0, b.count("accounts"))
    }

    @Test fun anOfflinePushKeepsTheOutbox() = runTest {
        val a = Device("a")
        a.insert("lend_borrow_persons", "name" to "Alex", "color" to "#000000", "created_at" to at, "updated_at" to at)
        remote.offline = true
        runCatching { a.engine.push() }
        assertEquals(1, a.db.syncDao().pendingCount())
        remote.offline = false
        a.engine.push()
        assertEquals(0, a.db.syncDao().pendingCount())
        assertEquals(JsonPrimitive("Alex"), a.payloads().values.single()["name"])
    }
}
