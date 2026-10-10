package io.github.hootkit_dev.hootbook.data.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every exported schema, built empty and opened at the current version through the app's
 * migrations: Room fails the open when a step is missing or leaves a table unlike its entity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationChainTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val schemas = File("schemas/io.github.hootkit_dev.hootbook.data.database.HootbookDatabase")

    private fun create(name: String, version: Int) {
        context.deleteDatabase(name)
        val schema = Json.parseToJsonElement(File(schemas, "$version.json").readText())
            .jsonObject["database"]!!.jsonObject
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
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) = statements.forEach(db::execSQL)
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }
            ).build()
        ).apply { writableDatabase; close() }
    }

    @Test fun everyExportedSchemaMigratesToTheCurrentVersion() {
        val versions = schemas.listFiles()!!.mapNotNull { it.nameWithoutExtension.toIntOrNull() }.sorted()
        val current = versions.last()
        assertTrue("schemas from 27 on are exported", versions.first() <= 27)
        val failures = versions.dropLast(1).mapNotNull { version ->
            val name = "chain-$version.db"
            create(name, version)
            val db = Room.databaseBuilder(context, HootbookDatabase::class.java, name)
                .addMigrations(*HootbookDatabase.MIGRATIONS)
                .addCallback(SyncTriggers.Callback).allowMainThreadQueries()
                .build()
            try {
                assertEquals(current, db.openHelper.writableDatabase.version)
                null
            } catch (e: Throwable) {
                "$version: ${e.message?.lineSequence()?.firstOrNull()}"
            } finally {
                db.close()
                context.deleteDatabase(name)
            }
        }
        assertEquals("migrations that failed", emptyList<String>(), failures)
    }

    @Test fun version71GivesEveryRowASyncIdAndCapturesLaterChanges() {
        val name = "sync.db"
        create(name, 70)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(70) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }
            ).build()
        )
        helper.writableDatabase.apply {
            (1..3).forEach {
                execSQL(
                    "INSERT INTO categories (name, color, is_system, is_income, display_order, created_at, updated_at) " +
                        "VALUES ('C$it', '#000000', 0, 0, $it, '2026-09-01T09:00', '2026-09-01T09:00')"
                )
            }
            execSQL(
                "INSERT INTO lend_borrow_persons (name, color, created_at, updated_at) " +
                    "VALUES ('Alex', '#000000', '2026-09-01T09:00', '2026-09-01T09:00')"
            )
        }
        helper.close()
        val before = System.currentTimeMillis()
        val db = Room.databaseBuilder(context, HootbookDatabase::class.java, name)
            .addMigrations(*HootbookDatabase.MIGRATIONS).addCallback(SyncTriggers.Callback).allowMainThreadQueries().build()
        try {
            val sql = db.openHelper.writableDatabase
            fun rows(query: String) = sql.query(query).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0) to c.getLong(1)) }
            }
            val categories = rows("SELECT sync_id, sync_updated_at FROM categories ORDER BY id")
            val people = rows("SELECT sync_id, sync_updated_at FROM lend_borrow_persons")
            assertEquals(listOf("C1", "C2", "C3"), rows("SELECT name, id FROM categories ORDER BY id").map { it.first })
            val all = categories + people
            assertEquals(4, all.size)
            assertTrue(all.all { (id, at) -> id.length == 32 && at >= before - 60_000 })
            assertEquals(4, all.map { it.first }.toSet().size)
            // Rows that were there are not queued; the first sync uploads everything anyway
            assertEquals(0, rows("SELECT COUNT(*), 0 FROM sync_outbox").single().first.toInt())
            assertEquals(
                SyncTriggers.TABLES.size * 3,
                rows("SELECT COUNT(*), 0 FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'sync_v%'").single().first.toInt()
            )

            sql.execSQL("UPDATE categories SET color = '#FFFFFF' WHERE name = 'C1'")
            val queued = rows("SELECT sync_id, queued_at FROM sync_outbox WHERE table_name = 'categories' AND op = 'UPSERT'")
            assertEquals(listOf(categories.first().first), queued.map { it.first })
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun version72GivesBuiltInCategoriesTheirFixedSyncIds() {
        val name = "seeds.db"
        create(name, 71)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(71) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }
            ).build()
        )
        helper.writableDatabase.apply {
            SyncTriggers.install(this, SyncTriggers.TABLES)
            // A built-in category renamed by the user, its subcategory, and one of the user's own
            execSQL(
                "INSERT INTO categories (name, color, is_system, is_income, display_order, default_name, created_at, updated_at) " +
                    "VALUES ('吃饭', '#000000', 1, 0, 1, 'Food & Drinks', '2026-09-01T09:00', '2026-09-01T09:00')"
            )
            execSQL(
                "INSERT INTO categories (name, color, is_system, is_income, display_order, created_at, updated_at) " +
                    "VALUES ('Pets', '#000000', 0, 0, 2, '2026-09-01T09:00', '2026-09-01T09:00')"
            )
            execSQL(
                "INSERT INTO subcategories (category_id, name, is_system, default_name, created_at, updated_at) " +
                    "VALUES (1, 'Eating out', 1, 'Eating out', '2026-09-01T09:00', '2026-09-01T09:00')"
            )
        }
        val queuedBefore = helper.writableDatabase.query("SELECT COUNT(*) FROM sync_outbox").use { it.moveToFirst(); it.getInt(0) }
        assertEquals(3, queuedBefore)
        helper.close()
        val db = Room.databaseBuilder(context, HootbookDatabase::class.java, name)
            .addMigrations(*HootbookDatabase.MIGRATIONS).addCallback(SyncTriggers.Callback).allowMainThreadQueries().build()
        try {
            val sql = db.openHelper.writableDatabase
            fun id(query: String) = sql.query(query).use { it.moveToFirst(); it.getString(0) }
            assertEquals(SyncIds.seededCategory("Food & Drinks"), id("SELECT sync_id FROM categories WHERE name = '吃饭'"))
            assertEquals(SyncIds.seededSubcategory("Food & Drinks", "Eating out"), id("SELECT sync_id FROM subcategories"))
            val own = id("SELECT sync_id FROM categories WHERE name = 'Pets'")
            assertEquals(32, own.length)
            // The old ids' queued upserts are gone; the user's own category stays queued
            val queued = sql.query("SELECT sync_id FROM sync_outbox").use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            assertEquals(listOf(own), queued)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun aSubscriptionsNotesSurviveTheRenameOfTheirColumn() {
        val name = "notes.db"
        create(name, 69)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(69) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }
            ).build()
        )
        helper.writableDatabase.execSQL(
            "INSERT INTO subscriptions (merchant_name, amount, state, sms_body, created_at, updated_at, currency) " +
                "VALUES ('Music', '15', 'ACTIVE', 'family plan', '2026-09-01T09:00', '2026-09-01T09:00', 'CNY')"
        )
        helper.close()
        val db = Room.databaseBuilder(context, HootbookDatabase::class.java, name)
            .addMigrations(*HootbookDatabase.MIGRATIONS).addCallback(SyncTriggers.Callback).allowMainThreadQueries().build()
        try {
            val notes = db.openHelper.writableDatabase.query("SELECT notes FROM subscriptions").use { c ->
                c.moveToFirst(); c.getString(0)
            }
            assertEquals("family plan", notes)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
