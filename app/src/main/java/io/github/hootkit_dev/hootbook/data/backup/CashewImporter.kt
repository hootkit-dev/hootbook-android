package io.github.hootkit_dev.hootbook.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import io.github.hootkit_dev.hootbook.data.database.HootbookDatabase
import io.github.hootkit_dev.hootbook.data.database.entity.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CashewImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: HootbookDatabase,
    private val attachmentImporter: CashewAttachmentImporter,
    private val userPreferencesRepository: io.github.hootkit_dev.hootbook.data.preferences.UserPreferencesRepository
) {
    // Entries Cashew stored without a currency take the app's main one
    private var baseCurrency = "CNY"

    private val tag = "CashewImporter"

    /**
     * Imports Cashew backup (either SQLite db or CSV) from the given Uri.
     */
    suspend fun importCashew(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        baseCurrency = userPreferencesRepository.baseCurrency.first()
        val tempFile = File(context.cacheDir, "cashew_import_temp.db")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext ImportResult.Error("Could not open file URI")

            if (isSqliteDatabase(tempFile)) {
                importFromSqlite(tempFile)
            } else {
                importFromCsv(tempFile)
            }
        } catch (e: Exception) {
            Log.e(tag, "Import failed", e)
            ImportResult.Error("Import failed: ${e.message}")
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun isSqliteDatabase(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        return try {
            val header = ByteArray(16)
            file.inputStream().use { it.read(header) }
            header.decodeToString() == "SQLite format 3\u0000"
        } catch (e: Exception) {
            false
        }
    }

    private fun getTableColumns(db: SQLiteDatabase, tableName: String): Set<String> {
        val columns = mutableSetOf<String>()
        try {
            db.rawQuery("PRAGMA table_info($tableName)", null).use { c ->
                val nameCol = c.getColumnIndex("name")
                if (nameCol != -1) {
                    while (c.moveToNext()) {
                        columns.add(c.getString(nameCol))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to get table info for $tableName: ${e.message}")
        }
        return columns
    }

    /**
     * Parse and import from a SQLite database export.
     */
    private suspend fun importFromSqlite(file: File): ImportResult {
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            // Retrieve table columns for robust handling of schema variations
            val walletCols = getTableColumns(db, "wallets")
            val categoryCols = getTableColumns(db, "categories")
            val transactionCols = getTableColumns(db, "transactions")
            val budgetCols = getTableColumns(db, "budgets")
            val budgetLimitCols = getTableColumns(db, "category_budget_limits")

            // 1. Wallets mapping
            val cashewWallets = mutableMapOf<String, String>() // wallet_pk -> name (bankName)
            val cashewWalletColors = mutableMapOf<String, String>() // wallet_pk -> color (#RRGGBB)
            val walletsList = mutableListOf<AccountBalanceEntity>()

            if (walletCols.contains("wallet_pk")) {
                db.rawQuery("SELECT * FROM wallets", null).use { c ->
                    val pkIdx = c.getColumnIndex("wallet_pk")
                    val nameIdx = c.getColumnIndex("name")
                    val currIdx = c.getColumnIndex("currency")
                    val colIdx = c.getColumnIndex("colour")
                    while (c.moveToNext()) {
                        val pk = if (pkIdx != -1) c.getString(pkIdx) ?: "" else ""
                        val name = if (nameIdx != -1) c.getString(nameIdx) ?: "Cashew Wallet" else "Cashew Wallet"
                        val currency = if (currIdx != -1) (c.getString(currIdx) ?: baseCurrency).uppercase() else baseCurrency
                        val color = if (colIdx != -1) CashewImportMapper.normalizeColor(c.getString(colIdx)) else "#4CAF50"

                        if (pk.isNotEmpty()) {
                            cashewWallets[pk] = name
                            cashewWalletColors[pk] = color
                            val last4 = CashewImportMapper.deriveLast4(name)
                            walletsList.add(
                                AccountBalanceEntity(
                                    bankName = name,
                                    accountLast4 = last4,
                                    balance = BigDecimal.ZERO, // Will calculate net sum later
                                    timestamp = LocalDateTime.now(),
                                    sourceType = "CASHEW_IMPORT",
                                    currency = currency,
                                    isWallet = true,
                                    color = color
                                )
                            )
                        }
                    }
                }
            }

            // 2. Categories mapping
            val cashewCategories = mutableMapOf<String, String>() // category_pk -> name
            val cashewCategoryColors = mutableMapOf<String, String>() // category_pk -> color
            val subcategoriesToImport = mutableListOf<Pair<String, String>>() // subcategory name -> parent_category_uuid
            val categoriesToImport = mutableListOf<CategoryEntity>()
            val categoryPkMap = mutableMapOf<String, CategoryEntity>() // category_pk -> CategoryEntity

            if (categoryCols.contains("category_pk")) {
                db.rawQuery("SELECT * FROM categories", null).use { c ->
                    val pkIdx = c.getColumnIndex("category_pk")
                    val nameIdx = c.getColumnIndex("name")
                    val colIdx = c.getColumnIndex("colour")
                    val iconIdx = c.getColumnIndex("icon_name")
                    val incIdx = c.getColumnIndex("income")
                    val parentIdx = c.getColumnIndex("main_category_fk")

                    while (c.moveToNext()) {
                        val pk = if (pkIdx != -1) c.getString(pkIdx) ?: "" else ""
                        val name = if (nameIdx != -1) c.getString(nameIdx) ?: "Other" else "Other"
                        val color = if (colIdx != -1) CashewImportMapper.normalizeColor(c.getString(colIdx)) else "#9E9E9E"
                        val icon = if (iconIdx != -1) c.getString(iconIdx) ?: "" else ""
                        val isIncome = if (incIdx != -1) c.getInt(incIdx) == 1 else false
                        val parentFk = if (parentIdx != -1) c.getString(parentIdx) else null

                        if (pk.isNotEmpty()) {
                            if (parentFk != null && parentFk != "null" && parentFk.isNotEmpty() && parentFk != "0") {
                                subcategoriesToImport.add(name to parentFk)
                            } else {
                                cashewCategories[pk] = name
                                cashewCategoryColors[pk] = color
                                val category = CategoryEntity(
                                    id = 0,
                                    name = name,
                                    color = color,
                                    iconName = icon,
                                    isSystem = false,
                                    isIncome = isIncome,
                                    displayOrder = 999
                                )
                                categoriesToImport.add(category)
                                categoryPkMap[pk] = category
                            }
                        }
                    }
                }
            }

            // 3. Raw Transactions query
            val rawTransactions = mutableListOf<RawCashewTransaction>()
            if (transactionCols.contains("transaction_pk")) {
                db.rawQuery("SELECT * FROM transactions", null).use { c ->
                    val pkIdx = c.getColumnIndex("transaction_pk")
                    val amtIdx = c.getColumnIndex("amount")
                    val noteIdx = c.getColumnIndex("note")
                    val dateIdx = when {
                        c.getColumnIndex("date_created") != -1 -> c.getColumnIndex("date_created")
                        c.getColumnIndex("date") != -1 -> c.getColumnIndex("date")
                        else -> c.getColumnIndex("date_time")
                    }
                    val catFkIdx = c.getColumnIndex("category_fk")
                    val subCatFkIdx = c.getColumnIndex("sub_category_fk")
                    val currIdx = c.getColumnIndex("currency")
                    val modIdx = c.getColumnIndex("date_time_modified")
                    val walletFkIdx = c.getColumnIndex("wallet_fk")
                    val pairedIdx = c.getColumnIndex("paired_transaction_fk")
                    val typeIdx = c.getColumnIndex("type")
                    val paidIdx = c.getColumnIndex("paid")

                    while (c.moveToNext()) {
                        val pk = if (pkIdx != -1) c.getString(pkIdx) ?: "" else ""
                        val amount = if (amtIdx != -1) c.getDouble(amtIdx) else 0.0
                        
                        var title = ""
                        for (col in listOf("title", "name", "merchant", "payee", "transaction_name", "desc", "description")) {
                            val idx = c.getColumnIndex(col)
                            if (idx != -1) {
                                val value = c.getString(idx)
                                if (!value.isNullOrBlank()) {
                                    title = value
                                    break
                                }
                            }
                        }
                        
                        val note = if (noteIdx != -1) c.getString(noteIdx) else null
                        val dateVal: Any? = if (dateIdx != -1) {
                            if (c.getType(dateIdx) == android.database.Cursor.FIELD_TYPE_INTEGER) c.getLong(dateIdx) else c.getString(dateIdx)
                        } else null
                        val categoryFk = if (catFkIdx != -1) c.getString(catFkIdx) else null
                        val subCategoryFk = if (subCatFkIdx != -1) c.getString(subCatFkIdx) else null
                        val currency = if (currIdx != -1) (c.getString(currIdx) ?: baseCurrency).uppercase() else baseCurrency
                        val modifiedVal: Any? = if (modIdx != -1) {
                            if (c.getType(modIdx) == android.database.Cursor.FIELD_TYPE_INTEGER) c.getLong(modIdx) else c.getString(modIdx)
                        } else null
                        val walletFk = if (walletFkIdx != -1) c.getString(walletFkIdx) else null
                        val pairedFk = if (pairedIdx != -1) c.getString(pairedIdx) else null
                        val type = if (typeIdx != -1) c.getInt(typeIdx) else 0
                        val paid = if (paidIdx != -1) c.getInt(paidIdx) == 1 else true

                        if (pk.isNotEmpty()) {
                            rawTransactions.add(
                                RawCashewTransaction(
                                    pk = pk,
                                    amount = amount,
                                    title = title,
                                    note = note,
                                    dateVal = dateVal,
                                    categoryFk = categoryFk,
                                    subCategoryFk = subCategoryFk,
                                    currency = currency,
                                    modifiedVal = modifiedVal,
                                    walletFk = walletFk,
                                    pairedTxFk = pairedFk,
                                    type = type,
                                    paid = paid
                                )
                            )
                        }
                    }
                }
            }

            // 4. Budgets query
            val rawBudgets = mutableListOf<RawCashewBudget>()
            if (budgetCols.contains("budget_pk")) {
                db.rawQuery("SELECT * FROM budgets", null).use { c ->
                    val pkIdx = c.getColumnIndex("budget_pk")
                    val nameIdx = c.getColumnIndex("name")
                    val amtIdx = c.getColumnIndex("amount")
                    val startIdx = c.getColumnIndex("start_date")
                    val endIdx = c.getColumnIndex("end_date")
                    val recIdx = c.getColumnIndex("reoccurrence")
                    val incIdx = c.getColumnIndex("income")
                    val archIdx = c.getColumnIndex("archived")
                    val walletFksIdx = c.getColumnIndex("wallet_fks")

                    while (c.moveToNext()) {
                        val pk = if (pkIdx != -1) c.getString(pkIdx) ?: "" else ""
                        val name = if (nameIdx != -1) c.getString(nameIdx) ?: "Budget" else "Budget"
                        val amount = if (amtIdx != -1) c.getDouble(amtIdx) else 0.0
                        val startVal = if (startIdx != -1) c.getLong(startIdx) else 0L
                        val endVal = if (endIdx != -1) c.getLong(endIdx) else 0L
                        val reoccurrence = if (recIdx != -1) c.getInt(recIdx) else 3 // Monthly default
                        val isIncome = if (incIdx != -1) c.getInt(incIdx) == 1 else false
                        val isActive = if (archIdx != -1) c.getInt(archIdx) == 0 else true
                        val walletFks = if (walletFksIdx != -1) c.getString(walletFksIdx) else null

                        if (pk.isNotEmpty()) {
                            rawBudgets.add(
                                RawCashewBudget(
                                    pk = pk,
                                    name = name,
                                    amount = amount,
                                    startDate = startVal,
                                    endDate = endVal,
                                    reoccurrence = reoccurrence,
                                    isIncome = isIncome,
                                    isActive = isActive,
                                    walletFks = walletFks
                                )
                            )
                        }
                    }
                }
            }

            // 5. Budget limits query
            val rawLimits = mutableListOf<RawCashewLimit>()
            if (budgetLimitCols.isNotEmpty()) {
                db.rawQuery("SELECT * FROM category_budget_limits", null).use { c ->
                    val catFkIdx = c.getColumnIndex("category_fk")
                    val budFkIdx = c.getColumnIndex("budget_fk")
                    val amtIdx = c.getColumnIndex("amount")

                    while (c.moveToNext()) {
                        val catFk = if (catFkIdx != -1) c.getString(catFkIdx) ?: "" else ""
                        val budFk = if (budFkIdx != -1) c.getString(budFkIdx) ?: "" else ""
                        val amount = if (amtIdx != -1) c.getDouble(amtIdx) else 0.0
                        if (catFk.isNotEmpty() && budFk.isNotEmpty()) {
                            rawLimits.add(RawCashewLimit(catFk, budFk, amount))
                        }
                    }
                }
            }

            // Database Insertion logic within Room transaction
            var importedTransactionsCount = 0
            var importedCategoriesCount = 0
            var skippedCount = 0
            var importedAttachmentsCount = 0
            var failedAttachmentsCount = 0

            // Prefetch existing transactions so attachment downloads can skip duplicates.
            val existingTxnsMap = database.transactionDao().getAllTransactions().first()
                .associateBy { it.transactionHash }

            // Pre-download Cashew attachments (Google Drive links inside the note)
            // outside the DB transaction so network I/O does not block the transaction.
            fun shouldProcess(tx: RawCashewTransaction): Boolean =
                !existingTxnsMap.containsKey(tx.pk) &&
                    !((tx.type == 3 || tx.type == 4) && !tx.paid)

            val hasDriveLinks = rawTransactions.any { tx ->
                shouldProcess(tx) &&
                    !tx.note.isNullOrBlank() &&
                    (tx.note!!.contains("drive.google.com") || tx.note!!.contains("docs.google.com"))
            }
            val driveToken = if (hasDriveLinks) attachmentImporter.resolveDriveToken() else null

            val attachmentsByPk = mutableMapOf<String, String>()
            rawTransactions.forEach { tx ->
                if (!shouldProcess(tx)) return@forEach
                val result = attachmentImporter.importAttachmentsFromNote(tx.note, driveToken)
                if (result.linkCount > 0) {
                    if (result.savedPaths.isNotEmpty()) {
                        attachmentsByPk[tx.pk] = result.savedPaths.joinToString(",")
                        importedAttachmentsCount += result.savedPaths.size
                    }
                    failedAttachmentsCount += (result.linkCount - result.savedPaths.size)
                }
            }

            database.withTransaction {
                // Get existing categories to prevent duplicates
                val existingCategories = database.categoryDao().getAllCategories().first()
                val existingCategoriesMap = existingCategories.associateBy { it.name.lowercase() }

                // Map Cashew category UUID -> Hootbook Category ID
                val categoryIdMap = mutableMapOf<String, Long>()

                // Insert/merge categories
                categoriesToImport.forEach { category ->
                    val normName = category.name.lowercase()
                    val existing = existingCategoriesMap[normName]
                    val catId = if (existing == null) {
                        val id = database.categoryDao().insertCategory(category)
                        importedCategoriesCount++
                        if (id == -1L) {
                            database.categoryDao().getCategoryByName(category.name)?.id ?: 0L
                        } else id
                    } else {
                        existing.id
                    }

                    // Find corresponding category_pk key
                    val origPk = categoryPkMap.filterValues { it.name == category.name }.keys.firstOrNull()
                    if (origPk != null) {
                        categoryIdMap[origPk] = catId
                    }
                }

                // Insert subcategories
                subcategoriesToImport.forEach { (subName, parentFk) ->
                    val parentId = categoryIdMap[parentFk]
                    if (parentId != null) {
                        val existingSubcategories = database.subcategoryDao().getSubcategoriesByCategoryId(parentId).first()
                        val alreadyExists = existingSubcategories.any { it.name.equals(subName, ignoreCase = true) }
                        if (!alreadyExists) {
                            val newSub = SubcategoryEntity(
                                id = 0,
                                categoryId = parentId,
                                name = subName,
                                createdAt = LocalDateTime.now(),
                                updatedAt = LocalDateTime.now()
                            )
                            database.subcategoryDao().insertSubcategory(newSub)
                        }
                    }
                }

                // Insert accounts
                walletsList.forEach { wallet ->
                    // Insert account balance metadata
                    database.accountBalanceDao().insertBalance(wallet)
                }

                // Process Transactions
                val processedTransfers = mutableSetOf<String>()
                val importedTxnsList = mutableListOf<TransactionEntity>()

                rawTransactions.forEach { tx ->
                    // Skip unpaid credit/debts
                    if ((tx.type == 3 || tx.type == 4) && !tx.paid) {
                        skippedCount++
                        return@forEach
                    }

                    // Dedup via hash
                    if (existingTxnsMap.containsKey(tx.pk)) {
                        skippedCount++
                        return@forEach
                    }

                    val txDate = CashewImportMapper.toLocalDateTime(tx.dateVal)
                    val txModified = CashewImportMapper.toLocalDateTime(tx.modifiedVal)

                    // Determine Category Name
                    val catName = if (tx.categoryFk == "0") "Transfer" else {
                        tx.categoryFk?.let { cashewCategories[it] } ?: "Miscellaneous"
                    }

                    // Determine Subcategory Name
                    val subCatName = tx.subCategoryFk?.let { fk ->
                        // Query the categories table again in sqlite if it represents a subcategory name
                        // In Cashew, subcategories are rows in categories table with main_category_fk set
                        // Our parsing logic put them in categories, but we skipped putting them in cashewCategories map.
                        // Let's retrieve subcategory name directly from database
                        var name: String? = null
                        try {
                            db.rawQuery("SELECT name FROM categories WHERE category_pk = ?", arrayOf(fk)).use { cursor ->
                                if (cursor.moveToFirst()) {
                                    name = cursor.getString(0)
                                }
                            }
                        } catch (e: Exception) {
                            // Fallback
                        }
                        name
                    }

                    // Check if Transfer pair
                    if (tx.categoryFk == "0" && tx.pairedTxFk != null) {
                        if (processedTransfers.contains(tx.pk) || processedTransfers.contains(tx.pairedTxFk)) {
                            // Already processed this pair
                            return@forEach
                        }

                        // Find paired leg
                        val pairLeg = rawTransactions.find { it.pk == tx.pairedTxFk }
                        if (pairLeg != null) {
                            processedTransfers.add(tx.pk)
                            processedTransfers.add(pairLeg.pk)

                            val expenseLeg = if (tx.amount < 0) tx else pairLeg
                            val incomeLeg = if (tx.amount >= 0) tx else pairLeg

                            val fromWalletName = expenseLeg.walletFk?.let { cashewWallets[it] } ?: "Unknown Account"
                            val toWalletName = incomeLeg.walletFk?.let { cashewWallets[it] } ?: "Unknown Account"

                            val transferTx = TransactionEntity(
                                id = 0,
                                amount = CashewImportMapper.toBigDecimal(expenseLeg.amount),
                                merchantName = if (expenseLeg.title.isNotEmpty()) expenseLeg.title else "Transfer",
                                category = "Transfer",
                                transactionType = TransactionType.TRANSFER,
                                dateTime = txDate,
                                description = expenseLeg.note,
                                bankName = fromWalletName,
                                accountNumber = CashewImportMapper.deriveLast4(fromWalletName),
                                fromAccount = fromWalletName,
                                toAccount = toWalletName,
                                transactionHash = expenseLeg.pk,
                                isRecurring = expenseLeg.type == 1 || expenseLeg.type == 2,
                                createdAt = txDate,
                                updatedAt = txModified,
                                currency = expenseLeg.currency,
                                attachments = attachmentsByPk[expenseLeg.pk].orEmpty()
                            )

                            database.transactionDao().insertTransaction(transferTx)
                            importedTxnsList.add(transferTx)
                            importedTransactionsCount++
                        } else {
                            // Standalone leg with category "0" and paired_transaction_fk but counterpart is missing
                            // Import as BALANCE_UPDATE
                            val walletName = tx.walletFk?.let { cashewWallets[it] } ?: "Unknown Wallet"
                            val balanceUpdateTx = TransactionEntity(
                                id = 0,
                                amount = CashewImportMapper.toBigDecimal(tx.amount),
                                merchantName = if (tx.title.isNotEmpty()) tx.title else "Balance Update",
                                category = "Balance Update",
                                transactionType = TransactionType.BALANCE_UPDATE,
                                dateTime = txDate,
                                description = tx.note,
                                bankName = walletName,
                                accountNumber = CashewImportMapper.deriveLast4(walletName),
                                transactionHash = tx.pk,
                                isRecurring = tx.type == 1 || tx.type == 2,
                                createdAt = txDate,
                                updatedAt = txModified,
                                currency = tx.currency,
                                attachments = attachmentsByPk[tx.pk].orEmpty()
                            )
                            database.transactionDao().insertTransaction(balanceUpdateTx)
                            importedTxnsList.add(balanceUpdateTx)
                            importedTransactionsCount++
                        }
                    } else if (tx.categoryFk == "0") {
                        // Standalone category "0" without paired_transaction_fk -> BALANCE_UPDATE
                        val walletName = tx.walletFk?.let { cashewWallets[it] } ?: "Unknown Wallet"
                        val balanceUpdateTx = TransactionEntity(
                            id = 0,
                            amount = CashewImportMapper.toBigDecimal(tx.amount),
                            merchantName = if (tx.title.isNotEmpty()) tx.title else "Balance Update",
                            category = "Balance Update",
                            transactionType = TransactionType.BALANCE_UPDATE,
                            dateTime = txDate,
                            description = tx.note,
                            bankName = walletName,
                            accountNumber = CashewImportMapper.deriveLast4(walletName),
                            transactionHash = tx.pk,
                            isRecurring = tx.type == 1 || tx.type == 2,
                            createdAt = txDate,
                            updatedAt = txModified,
                            currency = tx.currency
                        )
                        database.transactionDao().insertTransaction(balanceUpdateTx)
                        importedTxnsList.add(balanceUpdateTx)
                        importedTransactionsCount++
                    } else {
                        // Normal transaction
                        val walletName = tx.walletFk?.let { cashewWallets[it] } ?: "Unknown Wallet"
                        val last4 = CashewImportMapper.deriveLast4(walletName)

                        val finalTxType = when (tx.type) {
                            3 -> TransactionType.CREDIT
                            4 -> TransactionType.BORROWED
                            else -> if (tx.amount > 0) TransactionType.INCOME else TransactionType.EXPENSE
                        }

                        val normalTx = TransactionEntity(
                            id = 0,
                            amount = CashewImportMapper.toBigDecimal(tx.amount),
                            merchantName = when {
                                tx.title.isNotBlank() && tx.title != "Unknown" -> tx.title
                                !tx.note.isNullOrBlank() -> tx.note!!
                                subCatName != null -> subCatName
                                else -> catName
                            },
                            category = catName,
                            subcategory = subCatName,
                            transactionType = finalTxType,
                            dateTime = txDate,
                            description = tx.note,
                            bankName = walletName,
                            accountNumber = last4,
                            transactionHash = tx.pk,
                            isRecurring = tx.type == 1 || tx.type == 2,
                            createdAt = txDate,
                            updatedAt = txModified,
                            currency = tx.currency,
                            attachments = attachmentsByPk[tx.pk].orEmpty()
                        )

                        database.transactionDao().insertTransaction(normalTx)
                        importedTxnsList.add(normalTx)
                        importedTransactionsCount++
                    }
                }

                // 6. Import budgets and limits
                val budgetIdMap = mutableMapOf<String, Long>() // budget_pk (Cashew) -> budgetId (Hootbook)
                rawBudgets.forEach { budget ->
                    val startLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(budget.startDate), ZoneId.systemDefault())
                    val endLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(budget.endDate), ZoneId.systemDefault())

                    // Parse linked account IDs
                    val linkedWallets = parseJsonStringList(budget.walletFks)
                    val accountIds = linkedWallets.mapNotNull { fk ->
                        val walletName = cashewWallets[fk] ?: return@mapNotNull null
                        val last4 = CashewImportMapper.deriveLast4(walletName)
                        "$walletName:$last4"
                    }

                    val newBudget = BudgetEntity(
                        id = 0,
                        name = budget.name,
                        amount = BigDecimal.valueOf(budget.amount).setScale(2, RoundingMode.HALF_UP),
                        year = startLdt.year,
                        month = startLdt.monthValue,
                        currency = baseCurrency,
                        isActive = budget.isActive,
                        startDate = startLdt,
                        endDate = endLdt,
                        periodType = when (budget.reoccurrence) {
                            0 -> BudgetPeriod.CUSTOM
                            1 -> BudgetPeriod.DAILY
                            2 -> BudgetPeriod.WEEKLY
                            3 -> BudgetPeriod.MONTHLY
                            4 -> BudgetPeriod.YEARLY
                            else -> BudgetPeriod.MONTHLY
                        },
                        trackType = BudgetTrackType.ALL_TRANSACTIONS,
                        budgetType = if (budget.isIncome) BudgetType.SAVINGS else BudgetType.EXPENSE,
                        accountIds = accountIds,
                        color = "#4CAF50"
                    )

                    val budgetId = database.budgetDao().insertBudget(newBudget)
                    if (budgetId != -1L) {
                        budgetIdMap[budget.pk] = budgetId
                    }
                }

                // Import budget limits
                rawLimits.forEach { limit ->
                    val mappedBudgetId = budgetIdMap[limit.budgetFk]
                    val mappedCatName = cashewCategories[limit.categoryFk]
                    if (mappedBudgetId != null && mappedCatName != null) {
                        val limitEntity = BudgetCategoryLimitEntity(
                            id = 0,
                            budgetId = mappedBudgetId,
                            categoryName = mappedCatName,
                            limitAmount = BigDecimal.valueOf(limit.amount).setScale(2, RoundingMode.HALF_UP)
                        )
                        database.budgetDao().insertCategoryLimit(limitEntity)
                    }
                }

                // 7. Compute net balances per wallet and insert snapshot row
                val walletBalances = mutableMapOf<String, BigDecimal>()
                walletsList.forEach { wallet ->
                    walletBalances[wallet.bankName] = BigDecimal.ZERO
                }

                importedTxnsList.forEach { txn ->
                    val amount = txn.amount
                    when (txn.transactionType) {
                        TransactionType.INCOME, TransactionType.CREDIT -> {
                            txn.bankName?.let { bank ->
                                walletBalances[bank] = (walletBalances[bank] ?: BigDecimal.ZERO).add(amount)
                            }
                        }
                        TransactionType.EXPENSE, TransactionType.BORROWED -> {
                            txn.bankName?.let { bank ->
                                walletBalances[bank] = (walletBalances[bank] ?: BigDecimal.ZERO).subtract(amount)
                            }
                        }
                        TransactionType.TRANSFER -> {
                            txn.fromAccount?.let { from ->
                                walletBalances[from] = (walletBalances[from] ?: BigDecimal.ZERO).subtract(amount)
                            }
                            txn.toAccount?.let { to ->
                                walletBalances[to] = (walletBalances[to] ?: BigDecimal.ZERO).add(amount)
                            }
                        }
                        else -> {}
                    }
                }

                // Insert snapshot rows for wallets
                walletsList.forEach { wallet ->
                    val computedBal = walletBalances[wallet.bankName] ?: BigDecimal.ZERO
                    val updatedWallet = wallet.copy(
                        balance = computedBal,
                        timestamp = LocalDateTime.now()
                    )
                    database.accountBalanceDao().insertBalance(updatedWallet)
                }
            }

            ImportResult.Success(
                importedTransactions = importedTransactionsCount,
                importedCategories = importedCategoriesCount,
                skippedDuplicates = skippedCount,
                importedAttachments = importedAttachmentsCount,
                failedAttachments = failedAttachmentsCount
            )
        } finally {
            db.close()
        }
    }

    /**
     * Fallback simpler import from CSV files.
     */
    private suspend fun importFromCsv(file: File): ImportResult {
        var importedTransactionsCount = 0
        var importedCategoriesCount = 0
        var skippedCount = 0
        var importedAttachmentsCount = 0
        var failedAttachmentsCount = 0

        var driveToken: String? = null

        database.withTransaction {
            val existingTxnsMap = database.transactionDao().getAllTransactions().first()
                .associateBy { it.transactionHash }

            val existingCategories = database.categoryDao().getAllCategories().first()
            val existingCategoriesMap = existingCategories.associateBy { it.name.lowercase() }

            file.bufferedReader().use { reader ->
                val headerLine = reader.readLine() ?: return@withTransaction
                val headers = splitCsvLine(headerLine)

                val amtIdx = headers.indexOfFirst { it.equals("amount", true) }
                val titleIdx = headers.indexOfFirst { it.equals("title", true) || it.equals("name", true) }
                val noteIdx = headers.indexOfFirst { it.equals("note", true) || it.equals("description", true) }
                val dateIdx = headers.indexOfFirst { it.equals("date", true) || it.equals("date_time", true) }
                val catIdx = headers.indexOfFirst { it.equals("category", true) }
                val subCatIdx = headers.indexOfFirst { it.equals("subcategory", true) }
                val walletIdx = headers.indexOfFirst { it.equals("wallet", true) || it.equals("account", true) }
                val currIdx = headers.indexOfFirst { it.equals("currency", true) }

                if (amtIdx == -1 || dateIdx == -1) {
                    throw Exception("Required CSV columns (amount, date) are missing")
                }

                var lineStr = reader.readLine()
                var rowCounter = 0
                val walletsToRegister = mutableSetOf<String>()
                val txnsToImport = mutableListOf<TransactionEntity>()

                while (lineStr != null) {
                    val cols = splitCsvLine(lineStr)
                    rowCounter++
                    if (cols.size > amtIdx && cols.size > dateIdx) {
                        val amtVal = cols[amtIdx].toDoubleOrNull() ?: 0.0
                        val titleVal = if (titleIdx != -1 && titleIdx < cols.size) cols[titleIdx] else ""
                        val noteVal = if (noteIdx != -1 && noteIdx < cols.size) cols[noteIdx] else ""
                        val dateVal = cols[dateIdx]
                        val catVal = if (catIdx != -1 && catIdx < cols.size) cols[catIdx] else "Miscellaneous"
                        val subCatVal = if (subCatIdx != -1 && subCatIdx < cols.size) cols[subCatIdx].takeIf { it.isNotBlank() } else null
                        val walletVal = if (walletIdx != -1 && walletIdx < cols.size) cols[walletIdx] else "Cashew Wallet"
                        val currVal = if (currIdx != -1 && currIdx < cols.size) cols[currIdx].uppercase() else baseCurrency

                        // Dedup via stable row properties hash
                        val hash = CashewImportMapper.deriveLast4("$titleVal$amtVal$dateVal") + "_$rowCounter"

                        var finalTitle = if (titleVal.isNotBlank() && titleVal != "Unknown") titleVal else (subCatVal ?: catVal)

                        if (existingTxnsMap.containsKey(hash)) {
                            skippedCount++
                            lineStr = reader.readLine()
                            continue
                        }

                        val txDate = CashewImportMapper.toLocalDateTime(dateVal)

                        // Register Category if needed
                        val normCatName = catVal.lowercase()
                        val catId = if (!existingCategoriesMap.containsKey(normCatName)) {
                            val newCat = CategoryEntity(
                                id = 0,
                                name = catVal,
                                color = "#4CAF50",
                                isSystem = false,
                                isIncome = amtVal > 0,
                                displayOrder = 999
                            )
                            val insertedId = database.categoryDao().insertCategory(newCat)
                            importedCategoriesCount++
                            if (insertedId == -1L) {
                                database.categoryDao().getCategoryByName(catVal)?.id ?: 0L
                            } else insertedId
                        } else {
                            existingCategoriesMap[normCatName]?.id ?: 0L
                        }

                        // Register subcategory if needed
                        if (subCatVal != null && subCatVal.isNotEmpty() && catId > 0) {
                            val existingSubs = database.subcategoryDao().getSubcategoriesByCategoryId(catId).first()
                            if (existingSubs.none { it.name.equals(subCatVal, ignoreCase = true) }) {
                                val newSub = SubcategoryEntity(
                                    id = 0,
                                    categoryId = catId,
                                    name = subCatVal
                                )
                                database.subcategoryDao().insertSubcategory(newSub)
                            }
                        }

                        walletsToRegister.add(walletVal)

                        val finalTxType = if (amtVal > 0) TransactionType.INCOME else TransactionType.EXPENSE

                        // Import attachments from Google Drive links embedded in the note
                        val attachmentResult = if (noteVal.contains("drive.google.com") || noteVal.contains("docs.google.com")) {
                            if (driveToken == null) {
                                driveToken = attachmentImporter.resolveDriveToken()
                            }
                            attachmentImporter.importAttachmentsFromNote(noteVal, driveToken)
                        } else {
                            CashewAttachmentImporter.AttachmentImportResult(emptyList(), 0)
                        }
                        if (attachmentResult.linkCount > 0) {
                            if (attachmentResult.savedPaths.isNotEmpty()) {
                                importedAttachmentsCount += attachmentResult.savedPaths.size
                            }
                            failedAttachmentsCount += (attachmentResult.linkCount - attachmentResult.savedPaths.size)
                        }

                        val txn = TransactionEntity(
                            id = 0,
                            amount = CashewImportMapper.toBigDecimal(amtVal),
                            merchantName = finalTitle,
                            category = catVal,
                            subcategory = subCatVal,
                            transactionType = finalTxType,
                            dateTime = txDate,
                            description = noteVal,
                            bankName = walletVal,
                            accountNumber = CashewImportMapper.deriveLast4(walletVal),
                            transactionHash = hash,
                            isRecurring = false,
                            createdAt = txDate,
                            updatedAt = txDate,
                            currency = currVal,
                            attachments = attachmentResult.savedPaths.joinToString(",")
                        )

                        database.transactionDao().insertTransaction(txn)
                        txnsToImport.add(txn)
                        importedTransactionsCount++
                    }
                    lineStr = reader.readLine()
                }

                // Register wallets metadata
                walletsToRegister.forEach { name ->
                    val last4 = CashewImportMapper.deriveLast4(name)
                    // Compute net sum
                    var balance = BigDecimal.ZERO
                    txnsToImport.filter { it.bankName == name }.forEach { t ->
                        if (t.transactionType == TransactionType.INCOME) {
                            balance = balance.add(t.amount)
                        } else {
                            balance = balance.subtract(t.amount)
                        }
                    }

                    val walletEntity = AccountBalanceEntity(
                        bankName = name,
                        accountLast4 = last4,
                        balance = balance,
                        timestamp = LocalDateTime.now(),
                        sourceType = "CASHEW_IMPORT",
                        isWallet = true,
                        color = "#4CAF50"
                    )
                    database.accountBalanceDao().insertBalance(walletEntity)
                }
            }
        }

        return ImportResult.Success(
            importedTransactions = importedTransactionsCount,
            importedCategories = importedCategoriesCount,
            skippedDuplicates = skippedCount,
            importedAttachments = importedAttachmentsCount,
            failedAttachments = failedAttachmentsCount
        )
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim())
                current = StringBuilder()
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    private fun parseJsonStringList(jsonStr: String?): List<String> {
        if (jsonStr == null) return emptyList()
        val clean = jsonStr.trim().removePrefix("[").removeSuffix("]").trim()
        if (clean.isEmpty()) return emptyList()
        return clean.split(",").map { it.trim().removePrefix("\"").removeSuffix("\"").trim() }
    }

    // Helper models
    private data class RawCashewTransaction(
        val pk: String,
        val amount: Double,
        val title: String,
        val note: String?,
        val dateVal: Any?,
        val categoryFk: String?,
        val subCategoryFk: String?,
        val currency: String,
        val modifiedVal: Any?,
        val walletFk: String?,
        val pairedTxFk: String?,
        val type: Int,
        val paid: Boolean
    )

    private data class RawCashewBudget(
        val pk: String,
        val name: String,
        val amount: Double,
        val startDate: Long,
        val endDate: Long,
        val reoccurrence: Int,
        val isIncome: Boolean,
        val isActive: Boolean,
        val walletFks: String?
    )

    private data class RawCashewLimit(
        val categoryFk: String,
        val budgetFk: String,
        val amount: Double
    )
}
