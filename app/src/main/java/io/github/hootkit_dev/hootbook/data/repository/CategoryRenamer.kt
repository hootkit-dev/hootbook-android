package io.github.hootkit_dev.hootbook.data.repository

import androidx.room.withTransaction
import io.github.hootkit_dev.hootbook.data.database.HootbookDatabase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renames a category or subcategory everywhere it is referenced. Transactions, budget limits,
 * subscriptions, templates and loan entries name their category rather than point at it, so
 * renaming only the category row left them under the old name: no icon, and a second category
 * in the statistics.
 */
@Singleton
class CategoryRenamer @Inject constructor(private val database: HootbookDatabase) {

    suspend fun renameCategory(oldName: String, newName: String, update: suspend () -> Unit) {
        if (oldName == newName) return update()
        database.withTransaction {
            update()
            val db = database.openHelper.writableDatabase
            val args = arrayOf<Any?>(newName, oldName)
            db.execSQL("UPDATE transactions SET category = ? WHERE category = ?", args)
            db.execSQL("UPDATE budget_category_limits SET category_name = ? WHERE category_name = ?", args)
            db.execSQL("UPDATE subscriptions SET category = ? WHERE category = ?", args)
            db.execSQL("UPDATE quick_templates SET category = ? WHERE category = ?", args)
            db.execSQL("UPDATE lend_borrow_transactions SET category_name = ? WHERE category_name = ?", args)
        }
    }

    suspend fun renameSubcategory(category: String, oldName: String, newName: String, update: suspend () -> Unit) {
        if (oldName == newName) return update()
        database.withTransaction {
            update()
            val db = database.openHelper.writableDatabase
            val args = arrayOf<Any?>(newName, category, oldName)
            db.execSQL("UPDATE transactions SET subcategory = ? WHERE category = ? AND subcategory = ?", args)
            db.execSQL("UPDATE subscriptions SET subcategory = ? WHERE category = ? AND subcategory = ?", args)
            db.execSQL("UPDATE quick_templates SET subcategory = ? WHERE category = ? AND subcategory = ?", args)
        }
    }
}
