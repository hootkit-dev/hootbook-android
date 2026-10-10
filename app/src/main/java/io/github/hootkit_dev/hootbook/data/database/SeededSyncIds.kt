package io.github.hootkit_dev.hootbook.data.database

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Built-in categories and subcategories carry fixed sync ids ([SyncIds.seededCategory],
 * [SyncIds.seededSubcategory]): every device seeds the same defaults, and with ids of their own
 * they would arrive on the other device as copies. New installs seed them with these ids
 * (DatabaseCallback); [rekey] gives them to the built-in rows of a database seeded before.
 */
object SeededSyncIds {
    /**
     * Sets the fixed id on every built-in row that lacks it, skipping a row whose id another row
     * already holds. Capture is paused meanwhile (the update trigger would put the old id back),
     * and queued changes of ids that no longer exist are dropped.
     */
    fun rekey(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE sync_control SET applying_remote = 1 WHERE id = 1")
        try {
            val categories = db.query(
                "SELECT id, COALESCE(default_name, name), sync_id FROM categories WHERE is_system = 1"
            ).use { c -> buildList { while (c.moveToNext()) add(Triple(c.getLong(0), c.getString(1), c.getString(2))) } }
            categories.forEach { (id, name, current) -> assign(db, "categories", id, current, SyncIds.seededCategory(name)) }

            val subcategories = db.query(
                "SELECT s.id, COALESCE(c.default_name, c.name), COALESCE(s.default_name, s.name), s.sync_id " +
                    "FROM subcategories s JOIN categories c ON c.id = s.category_id WHERE s.is_system = 1"
            ).use { c ->
                buildList {
                    while (c.moveToNext()) add(Seeded(c.getLong(0), c.getString(1), c.getString(2), c.getString(3)))
                }
            }
            subcategories.forEach {
                assign(db, "subcategories", it.id, it.syncId, SyncIds.seededSubcategory(it.category, it.name))
            }
            listOf("categories", "subcategories").forEach { table ->
                db.execSQL(
                    "DELETE FROM sync_outbox WHERE table_name = '$table' AND op = '${SyncTriggers.OP_UPSERT}' " +
                        "AND sync_id NOT IN (SELECT sync_id FROM `$table`)"
                )
            }
        } finally {
            db.execSQL("UPDATE sync_control SET applying_remote = 0 WHERE id = 1")
        }
    }

    private class Seeded(val id: Long, val category: String, val name: String, val syncId: String)

    private fun assign(db: SupportSQLiteDatabase, table: String, id: Long, current: String, wanted: String) {
        if (current == wanted) return
        val taken = db.query("SELECT 1 FROM `$table` WHERE sync_id = ? AND id <> ?", arrayOf<Any>(wanted, id))
            .use { it.moveToFirst() }
        if (!taken) db.execSQL("UPDATE `$table` SET sync_id = ? WHERE id = ?", arrayOf<Any>(wanted, id))
    }
}
