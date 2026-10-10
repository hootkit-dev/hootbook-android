package io.github.hootkit_dev.hootbook.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.hootkit_dev.hootbook.data.database.HootbookDatabase
import io.github.hootkit_dev.hootbook.data.database.DatabaseCallback
import io.github.hootkit_dev.hootbook.data.database.SyncIds
import io.github.hootkit_dev.hootbook.data.database.SyncTriggers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The wire protocol's column list (SyncSchema, docs/sync.md) matches the database: a column
 * added to a synced table must be given a kind there, or it would silently not sync.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncSchemaTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, HootbookDatabase::class.java)
        .addCallback(SyncTriggers.Callback).allowMainThreadQueries().build()

    @After fun tearDown() = db.close()

    @Test fun everySyncedTableAndColumnIsDescribed() {
        assertEquals(SyncTriggers.TABLES.toSet(), SyncSchema.TABLES.map { it.name }.toSet())
        val sql = db.openHelper.writableDatabase
        SyncSchema.TABLES.forEach { table ->
            val columns = sql.query("PRAGMA table_info(`${table.name}`)").use { c ->
                buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
            }
            assertEquals(table.name, (columns - "id" - "sync_id").toSet(), table.columns.map { it.name }.toSet())
            table.refs.forEach { assertTrue("${table.name}.${it.name}", SyncSchema.table(it.ref!!)!!.level < table.level) }
        }
    }

    @Test fun builtInCategoriesAreSeededWithTheirFixedIds() {
        DatabaseCallback(context).seed(db.openHelper.writableDatabase)
        val sql = db.openHelper.writableDatabase
        val rows = sql.query("SELECT name, sync_id FROM categories").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
        }
        assertTrue(rows.size > 10)
        rows.forEach { (name, id) -> assertEquals(name, SyncIds.seededCategory(name), id) }
        // The examples of docs/sync.md (computed with Python's hashlib)
        assertEquals("0c96cfa31dcca7a206528958678f18ed", SyncIds.seededCategory("Food & Drinks"))
        assertEquals("f03fe7db8f086c901d7a5c9c5a8792d4", SyncIds.seededSubcategory("Food & Drinks", "Eating out"))
        val subs = sql.query(
            "SELECT c.name, s.name, s.sync_id FROM subcategories s JOIN categories c ON c.id = s.category_id"
        ).use { c -> buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.getString(1), c.getString(2))) } }
        assertTrue(subs.isNotEmpty())
        subs.forEach { (category, name, id) -> assertEquals("$category/$name", SyncIds.seededSubcategory(category, name), id) }
        assertEquals(subs.size, subs.map { it.third }.toSet().size)
    }
}
