package io.github.hootkit_dev.hootbook.data.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Local change capture for multi-device sync (see docs/sync.md).
 *
 * Every synced table has `sync_id` (a record's identity across devices) and `sync_updated_at`
 * (epoch millis of its last local change). SQLite triggers keep both and queue each change in
 * `sync_outbox`, so every write path (DAOs, raw SQL, imports, cascades) is covered without
 * touching the DAOs:
 *
 * - after insert: a row without a sync id, or with one another row already has (code that copies
 *   an entity with `copy(id = 0)` copies its sync id too), gets a fresh one; the row is stamped
 *   and an UPSERT queued.
 * - after update: the sync id never changes (an update that blanks or replaces it gets the old one
 *   back); the row is stamped and an UPSERT queued.
 * - after delete: a DELETE is queued.
 *
 * Nothing is captured while `sync_control.applying_remote` is 1: a later phase sets it, inside one
 * transaction, while it writes changes that came from another device. `sync_control.capturing` is
 * set by the triggers themselves around the row update they make, so that update does not
 * capture itself.
 *
 * The triggers are not part of Room's schema. [install] creates them (IF NOT EXISTS) from the
 * 70→71 migration, and [Callback] on every create and open, which also turns on
 * `recursive_triggers` so rows an `INSERT OR REPLACE` deletes queue their DELETE.
 */
object SyncTriggers {
    /** The synced tables, as Room names them. Not exchange_rates: those are fetched, not entered. */
    val TABLES = listOf(
        "accounts",
        "account_currencies",
        "account_balances",
        "transactions",
        "categories",
        "subcategories",
        "cards",
        "budgets",
        "budget_category_limits",
        "subscriptions",
        "lend_borrow_persons",
        "lend_borrow_transactions",
        "quick_templates",
    )

    const val OP_UPSERT = "UPSERT"
    const val OP_DELETE = "DELETE"

    /** Bump when a trigger body changes: [install] drops triggers of other versions. */
    private const val VERSION = 1
    private const val PREFIX = "sync_v${VERSION}_"

    /** SQL for a new sync id: 32 lowercase hex digits, as [SyncIds.newId] makes. */
    const val NEW_ID_SQL = "lower(hex(randomblob(16)))"

    /** SQL for the current time in epoch millis. */
    const val NOW_SQL = "CAST(ROUND((julianday('now') - 2440587.5) * 86400000) AS INTEGER)"

    // Without the control row nothing is captured (install puts it back): the capturing guard
    // lives in that row, and without it a trigger's own row update would capture itself forever.
    private const val NOT_PAUSED = "EXISTS (SELECT 1 FROM sync_control WHERE id = 1 AND applying_remote = 0)"
    private const val NOT_PAUSED_OR_CAPTURING =
        "EXISTS (SELECT 1 FROM sync_control WHERE id = 1 AND applying_remote = 0 AND capturing = 0)"

    private const val CAPTURE_BEGIN = "UPDATE sync_control SET capturing = 1 WHERE id = 1;"
    private const val CAPTURE_END = "UPDATE sync_control SET capturing = 0 WHERE id = 1;"

    /*
     * Trigger bodies use no OR REPLACE / OR IGNORE: the conflict policy of the statement that
     * fired a trigger overrides the one inside it (a DAO's INSERT OR IGNORE would turn an
     * INSERT OR REPLACE in here into an ignore). An entry is replaced by delete + insert.
     */
    private fun triggers(table: String): Map<String, String> {
        val taken = "EXISTS (SELECT 1 FROM `$table` o WHERE o.sync_id = NEW.sync_id AND o.rowid <> NEW.rowid)"
        val current = "(SELECT sync_id FROM `$table` WHERE rowid = NEW.rowid)"
        val upsert = """
            DELETE FROM sync_outbox WHERE table_name = '$table' AND sync_id = $current;
            INSERT INTO sync_outbox (table_name, sync_id, op, queued_at)
            SELECT '$table', sync_id, '$OP_UPSERT', $NOW_SQL FROM `$table` WHERE rowid = NEW.rowid;
        """.trimIndent()
        return mapOf(
            "${PREFIX}${table}_insert" to """
                CREATE TRIGGER IF NOT EXISTS `${PREFIX}${table}_insert` AFTER INSERT ON `$table`
                WHEN $NOT_PAUSED_OR_CAPTURING
                BEGIN
                    $CAPTURE_BEGIN
                    UPDATE `$table` SET
                        sync_id = CASE WHEN NEW.sync_id = '' OR $taken THEN $NEW_ID_SQL ELSE NEW.sync_id END,
                        sync_updated_at = max($NOW_SQL, NEW.sync_updated_at)
                    WHERE rowid = NEW.rowid;
                    $CAPTURE_END
                    $upsert
                END
            """.trimIndent(),
            "${PREFIX}${table}_update" to """
                CREATE TRIGGER IF NOT EXISTS `${PREFIX}${table}_update` AFTER UPDATE ON `$table`
                WHEN $NOT_PAUSED_OR_CAPTURING
                BEGIN
                    $CAPTURE_BEGIN
                    UPDATE `$table` SET
                        sync_id = CASE
                            WHEN OLD.sync_id <> '' THEN OLD.sync_id
                            WHEN NEW.sync_id = '' OR $taken THEN $NEW_ID_SQL
                            ELSE NEW.sync_id END,
                        sync_updated_at = max($NOW_SQL, OLD.sync_updated_at + 1)
                    WHERE rowid = NEW.rowid;
                    $CAPTURE_END
                    $upsert
                END
            """.trimIndent(),
            "${PREFIX}${table}_delete" to """
                CREATE TRIGGER IF NOT EXISTS `${PREFIX}${table}_delete` AFTER DELETE ON `$table`
                WHEN OLD.sync_id <> '' AND $NOT_PAUSED
                BEGIN
                    DELETE FROM sync_outbox WHERE table_name = '$table' AND sync_id = OLD.sync_id;
                    INSERT INTO sync_outbox (table_name, sync_id, op, queued_at)
                    VALUES ('$table', OLD.sync_id, '$OP_DELETE', $NOW_SQL);
                END
            """.trimIndent(),
        )
    }

    /**
     * Creates the control row and the triggers of [tables] if missing, and drops other sync
     * triggers (older versions). Needs the sync columns and tables of version 71.
     */
    fun install(db: SupportSQLiteDatabase, tables: List<String> = TABLES) {
        db.execSQL("INSERT OR IGNORE INTO sync_control (id, applying_remote, capturing) VALUES (1, 0, 0)")
        val wanted = tables.flatMap { table -> triggers(table).entries }
        val names = wanted.map { it.key }.toSet()
        val stale = db.query("SELECT name FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'sync\\_v%' ESCAPE '\\'")
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            .filter { it !in names }
        stale.forEach { db.execSQL("DROP TRIGGER IF EXISTS `$it`") }
        wanted.forEach { db.execSQL(it.value) }
    }

    /**
     * Gives every row without a sync id one. Only rows written while the triggers were missing
     * can lack one; they are not queued.
     */
    fun backfill(db: SupportSQLiteDatabase) {
        TABLES.forEach { table ->
            db.execSQL("UPDATE `$table` SET sync_id = $NEW_ID_SQL, sync_updated_at = $NOW_SQL WHERE sync_id = ''")
        }
    }

    /**
     * Installs the triggers on a new database and on every open. On open it also clears flags a
     * crash may have left set, and turns on `recursive_triggers` for the connection that writes,
     * so a row an `INSERT OR REPLACE` deletes queues its DELETE. Add it to every builder of
     * [HootbookDatabase], tests included, before any callback that writes.
     */
    object Callback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            install(db)
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            // execSQL runs on the primary connection, the one every write uses
            db.execSQL("PRAGMA recursive_triggers = ON")
            install(db)
            db.execSQL("UPDATE sync_control SET applying_remote = 0, capturing = 0 WHERE id = 1")
            backfill(db)
        }
    }
}

/** Sync ids made in Kotlin, in the same form the triggers make them. */
object SyncIds {
    fun newId(): String = java.util.UUID.randomUUID().toString().replace("-", "")

    /**
     * The fixed sync id of a built-in (seeded) category, the same on every device, so the defaults
     * each device seeds are one record rather than copies (docs/sync.md, "Seeded categories"):
     * the first 16 bytes of SHA-256 over `cashiro-seed:category:<default name>`, as lowercase hex.
     */
    fun seededCategory(defaultName: String): String = digest("cashiro-seed:category:$defaultName")

    /** The same for a built-in subcategory: `cashiro-seed:subcategory:<category default name>/<default name>`. */
    fun seededSubcategory(categoryDefaultName: String, defaultName: String): String =
        digest("cashiro-seed:subcategory:$categoryDefaultName/$defaultName")

    private fun digest(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .take(16).joinToString("") { "%02x".format(it) }
}
