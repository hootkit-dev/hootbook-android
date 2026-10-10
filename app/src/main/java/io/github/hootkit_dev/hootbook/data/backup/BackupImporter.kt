package io.github.hootkit_dev.hootbook.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.GsonBuilder
import androidx.room.withTransaction
import io.github.hootkit_dev.hootbook.data.brokerage.BrokerageRepository
import io.github.hootkit_dev.hootbook.data.icons.MerchantIconStore
import org.json.JSONObject
import io.github.hootkit_dev.hootbook.data.database.HootbookDatabase
import io.github.hootkit_dev.hootbook.data.database.entity.*
import io.github.hootkit_dev.hootbook.data.preferences.UserPreferencesRepository
import io.github.hootkit_dev.hootbook.data.preferences.AppFont
import io.github.hootkit_dev.hootbook.data.preferences.ThemeStyle
import io.github.hootkit_dev.hootbook.data.preferences.AccentColor
import io.github.hootkit_dev.hootbook.data.preferences.AppIcon
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.zip.ZipInputStream
import java.io.FileOutputStream
import java.io.File
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: HootbookDatabase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val brokerageRepository: BrokerageRepository,
    private val merchantIconStore: MerchantIconStore,
    private val aiSettings: io.github.hootkit_dev.hootbook.data.ai.AiSettings
) {
    
    private val gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
        .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
        .registerTypeAdapter(BigDecimal::class.java, BigDecimalTypeAdapter())
        .registerTypeAdapterFactory(SyncIdDefaultsFactory())
        .create()
    
    /**
     * Import backup from a file URI
     */
    suspend fun importBackup(
        uri: Uri,
        strategy: ImportStrategy = ImportStrategy.MERGE,
        restoreOnboardingCompletion: Boolean = true
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            // Read and parse the backup file
            val (backup, extras) = readBackupFile(uri)

            // Validate backup version
            if (!isCompatibleVersion(backup)) {
                return@withContext ImportResult.Error("Incompatible backup version")
            }
            
            
            // Import based on strategy
            val result = when (strategy) {
                ImportStrategy.REPLACE_ALL -> replaceAllData(backup, restoreOnboardingCompletion)
                ImportStrategy.MERGE -> mergeData(backup, restoreOnboardingCompletion)
            }
            // Brokerage connections are only added, never replaced: a failure here leaves the ledger import intact
            if (result is ImportResult.Success && extras.brokerage != null) {
                try {
                    brokerageRepository.restore(extras.brokerage)
                } catch (e: Exception) {
                    Log.w("BackupImporter", "Brokerage connections not restored: ${e.javaClass.simpleName}")
                }
            }
            // The AI key only fills an empty setting: a device's own key is never replaced
            if (result is ImportResult.Success && extras.ai != null) {
                runCatching { aiSettings.restoreFromBackup(extras.ai) }
                    .onFailure { Log.w("BackupImporter", "AI settings not restored: ${it.javaClass.simpleName}") }
            }
            if (result is ImportResult.Success && extras.merchantIcons != null) {
                try {
                    val index = JSONObject(extras.merchantIcons)
                    merchantIconStore.restore(index.keys().asSequence().associateWith { index.getString(it) })
                } catch (e: Exception) {
                    Log.w("BackupImporter", "Merchant icons not restored: ${e.javaClass.simpleName}")
                }
            }
            result
        } catch (e: Exception) {
            Log.e("BackupImporter", "Import failed", e)
            ImportResult.Error("Import failed: ${e.message}")
        }
    }
    
    /**
     * Read and parse backup file
     */
    /**
     * Read and parse backup file (ZIP or JSON)
     */
    /** What a backup zip carries besides backup.json, read before the import decides to use it. */
    private data class BackupExtras(val brokerage: String? = null, val merchantIcons: String? = null, val ai: String? = null)

    private suspend fun readBackupFile(uri: Uri): Pair<HootbookBackup, BackupExtras> {
        return withContext(Dispatchers.IO) {
            // Try to read as ZIP first
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val zipInput = ZipInputStream(inputStream)
                    var backup: HootbookBackup? = null
                    var brokerage: String? = null
                    var ai: String? = null
                    var merchantIcons: String? = null
                    var entry = zipInput.nextEntry

                    if (entry != null) {
                        val attachmentsDir = File(context.filesDir, "attachments")
                        if (!attachmentsDir.exists()) attachmentsDir.mkdirs()
                        val avatarsDir = File(context.filesDir, "avatars")
                        if (!avatarsDir.exists()) avatarsDir.mkdirs()

                        while (entry != null) {
                            val name = entry.name
                            if (name == "backup.json") {
                                // Read JSON
                                val bytes = zipInput.readBytes()
                                val content = String(bytes, Charsets.UTF_8)
                                backup = gson.fromJson(content, HootbookBackup::class.java)
                            } else if (name == BackupExporter.AI_ENTRY) {
                                ai = String(zipInput.readBytes(), Charsets.UTF_8)
                            } else if (name == BackupExporter.BROKERAGE_ENTRY) {
                                brokerage = String(zipInput.readBytes(), Charsets.UTF_8)
                            } else if (name == BackupExporter.MERCHANT_ICON_INDEX) {
                                merchantIcons = String(zipInput.readBytes(), Charsets.UTF_8)
                            } else if (name.startsWith("${MerchantIconStore.DIRECTORY}/") && !entry.isDirectory) {
                                merchantIconStore.directory.mkdirs()
                                FileOutputStream(File(merchantIconStore.directory, File(name).name)).use { output ->
                                    zipInput.copyTo(output)
                                }
                            } else if (name.startsWith("attachments/") && !entry.isDirectory) {
                                // Extract Attachment
                                val fileName = File(name).name
                                val outFile = File(attachmentsDir, fileName)
                                FileOutputStream(outFile).use { output ->
                                    zipInput.copyTo(output)
                                }
                            } else if (name.startsWith("avatars/") && !entry.isDirectory) {
                                // Extract Person Avatar
                                val fileName = File(name).name
                                val outFile = File(avatarsDir, fileName)
                                FileOutputStream(outFile).use { output ->
                                    zipInput.copyTo(output)
                                }
                            } else if (name.startsWith("profile/") && !entry.isDirectory) {
                                // Extract Profile Images
                                val profileDir = File(context.filesDir, "profile")
                                if (!profileDir.exists()) profileDir.mkdirs()

                                val fileName = File(name).name
                                val outFile = File(profileDir, fileName)
                                FileOutputStream(outFile).use { output ->
                                    zipInput.copyTo(output)
                                }
                            }
                            zipInput.closeEntry()
                            entry = zipInput.nextEntry
                        }
                        // If we found a backup.json, return it
                        if (backup != null) return@withContext backup to BackupExtras(brokerage, merchantIcons, ai)
                    }
                }
            } catch (e: Exception) {
                // Not a ZIP or failed, fall back to legacy JSON
                Log.d("BackupImporter", "Failed to read as ZIP, trying legacy JSON: ${e.message}")
            }

            // Legacy JSON handling
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val content = reader.readText()
                gson.fromJson(content, HootbookBackup::class.java) to BackupExtras()
            } ?: throw Exception("Failed to read backup file")
        }
    }
    
    /**
     * Check if backup version is compatible
     */
    private fun isCompatibleVersion(backup: HootbookBackup): Boolean {
        // For now, accept all v1.x backups
        return backup.format.startsWith("Cashiro Backup v1")

    }
    
    /**
     * Replace all existing data with backup data
     */
    private suspend fun replaceAllData(backup: HootbookBackup, restoreOnboardingCompletion: Boolean): ImportResult {
        var importedTransactions = 0
        var importedCategories = 0
        
        return database.withTransaction {
            try {
                // Clear existing data
                database.transactionDao().deleteAllTransactions()
                database.categoryDao().deleteAllCategories()
                database.cardDao().deleteAllCards()
                database.accountBalanceDao().deleteAllBalances()
                database.subscriptionDao().deleteAllSubscriptions()
                database.budgetDao().deleteAllBudgets()
                database.subcategoryDao().getAllSubcategories().first().forEach { 
                    database.subcategoryDao().deleteSubcategory(it)
                }
                database.exchangeRateDao().deleteAllRates()
                database.lendBorrowDao().deleteAllTransactions()
                database.lendBorrowDao().deleteAllPersons()

                // Accounts first: transactions and balances refer to them
                val accountIds = importAccounts(backup.database, keepIds = true)

                // Import all data
                backup.database.categories.forEach { category ->
                    database.categoryDao().insertCategory(category.sanitize())
                    importedCategories++
                }
                
                backup.database.transactions.forEach { transaction ->
                    database.transactionDao().insertTransaction(transaction.sanitize().withAccounts(accountIds))
                    importedTransactions++
                }
                
                backup.database.cards.forEach { card ->
                    database.cardDao().insertCard(card.sanitize())
                }
                
                balancesToImport(backup.database).forEach { balance ->
                    database.accountBalanceDao().insertBalance(balance)
                }
                
                backup.database.subscriptions.forEach { subscription ->
                    database.subscriptionDao().insertSubscription(subscription.sanitize())
                }
                
                backup.database.exchangeRates.forEach { rate ->
                    database.exchangeRateDao().insertExchangeRate(rate)
                }
                
                backup.database.budgets.forEach { budget ->
                    database.budgetDao().insertBudget(budget.sanitize())
                }

                backup.database.budgetCategoryLimits.forEach { limit ->
                    database.budgetDao().insertCategoryLimit(limit)
                }
                
                backup.database.subcategories.forEach { subcategory ->
                    database.subcategoryDao().insertSubcategory(subcategory.sanitize())
                }

                backup.database.lendBorrowPersons.forEach { person ->
                    database.lendBorrowDao().insertPerson(person)
                }

                backup.database.lendBorrowTransactions.forEach { tx ->
                    database.lendBorrowDao().insertTransaction(tx)
                }
                
                database.accountBalanceDao().linkTransactionsToAccounts()

                // Import preferences
                importPreferences(backup.preferences, restoreOnboardingCompletion)
                
                ImportResult.Success(
                    importedTransactions = importedTransactions,
                    importedCategories = importedCategories,
                    skippedDuplicates = 0
                )
            } catch (e: Exception) {
                throw e
            }
        }
    }
    
    /**
     * Merge backup data with existing data
     */
    private suspend fun mergeData(backup: HootbookBackup, restoreOnboardingCompletion: Boolean): ImportResult {
        var importedTransactions = 0
        var importedCategories = 0
        var skippedDuplicates = 0
        
        return database.withTransaction {
            try {
                val accountsBefore = existingAccountKeys()
                val accountIds = importAccounts(backup.database, keepIds = false)

                // Get existing data for duplicate checking
                val existingTransactions = database.transactionDao().getAllTransactions().first()
                val existingTransactionsMap = existingTransactions.associateBy { it.transactionHash }
                // The same record edited elsewhere has another hash but keeps its sync id
                val existingBySyncId = existingTransactions.filter { it.syncId.isNotEmpty() }.associateBy { it.syncId }
                
                val existingCategories = database.categoryDao()
                    .getAllCategories().first()
                    .map { it.name }
                    .toSet()
                
                // Mapping to track OldCategoryID -> NewCategoryID for subcategories
                val categoryIdMap = mutableMapOf<Long, Long>()
                
                // Import categories (merge by name)
                backup.database.categories.forEach { category ->
                    val sanitizedCategory = category.sanitize()
                    val existingCategory = database.categoryDao().getCategoryByName(sanitizedCategory.name)
                    if (existingCategory == null) {
                        // Generate new ID for imported category
                        val newCategory = sanitizedCategory.copy(id = 0)
                        val newId = database.categoryDao().insertCategory(newCategory)
                        categoryIdMap[sanitizedCategory.id] = newId
                        importedCategories++
                    } else {
                        categoryIdMap[sanitizedCategory.id] = existingCategory.id
                    }
                }

                // Import subcategories (merge by name within category)
                backup.database.subcategories.forEach { subcategory ->
                    val sanitizedSubcategory = subcategory.sanitize()
                    val newCategoryId = categoryIdMap[sanitizedSubcategory.categoryId] ?: return@forEach
                    val existingSubcategories = database.subcategoryDao().getSubcategoriesByCategoryId(newCategoryId).first()
                    val alreadyExists = existingSubcategories.any { it.name == sanitizedSubcategory.name }
                    
                    if (!alreadyExists) {
                        val newSubcategory = sanitizedSubcategory.copy(id = 0, categoryId = newCategoryId)
                        database.subcategoryDao().insertSubcategory(newSubcategory)
                    }
                }
                
                // Backup transaction id -> its id here, for balance rows and loans
                val transactionIdMap = mutableMapOf<Long, Long>()
                // Backup transactions that are new here: only their balance rows come along
                val insertedTransactions = mutableSetOf<Long>()

                // Import transactions (merge by hash)
                backup.database.transactions.forEach { backupTxn ->
                    val sanitizedTxn = backupTxn.sanitize().withAccounts(accountIds)
                    val existingTxn = existingTransactionsMap[sanitizedTxn.transactionHash]
                        ?: sanitizedTxn.syncId.takeIf { it.isNotEmpty() }?.let { existingBySyncId[it] }
                    if (existingTxn == null) {
                        // New transaction, insert it
                        val newTransaction = sanitizedTxn.copy(id = 0)
                        val newId = database.transactionDao().insertTransaction(newTransaction)
                        transactionIdMap[sanitizedTxn.id] = newId
                        if (newId > 0) insertedTransactions += sanitizedTxn.id
                        importedTransactions++
                    } else {
                        transactionIdMap[sanitizedTxn.id] = existingTxn.id
                        // Transaction exists locally. Should we update it with backup data?
                        // If backupTxn has a newer updatedAt or different fields, update local
                        
                        // Heuristic: Update if backup version is "better"
                        // 1. User edits have updatedAt > dateTime
                        // 2. Local unedited SMS scans have updatedAt == dateTime (due to our change in Mapper)
                        
                        val shouldUpdate = when {
                            // Case 1: Backup has a newer edit than local
                            sanitizedTxn.updatedAt.isAfter(existingTxn.updatedAt) -> true
                            
                            // Case 2: Local is just a fresh scan (identical updatedAt/dateTime) 
                            // but backup has an actual edit (updatedAt > dateTime)
                            sanitizedTxn.updatedAt.isAfter(sanitizedTxn.dateTime) && 
                                    !existingTxn.updatedAt.isAfter(existingTxn.dateTime) -> true
                            
                            // Otherwise, keep local
                            else -> false
                        }
 
                        if (shouldUpdate) {
                            // A record keeps its own sync id; the triggers would restore it anyway
                            val updatedTxn = sanitizedTxn.copy(id = existingTxn.id, syncId = existingTxn.syncId)
                            database.transactionDao().updateTransaction(updatedTxn)
                            importedTransactions++
                        } else {
                            skippedDuplicates++
                        }
                    }
                }
                
                // Import other entities with duplicate checking
                importCardsWithMerge(backup.database.cards)
                importAccountBalancesWithMerge(backup.database, transactionIdMap, insertedTransactions, accountsBefore)
                importSubscriptionsWithMerge(backup.database.subscriptions)
                importBudgetsWithMerge(backup.database.budgets, backup.database.budgetCategoryLimits)
                importExchangeRatesWithMerge(backup.database.exchangeRates)

                importLendBorrowWithMerge(backup.database, transactionIdMap)
                
                database.accountBalanceDao().linkTransactionsToAccounts()

                // Import preferences (merge with existing)
                importPreferences(backup.preferences, restoreOnboardingCompletion)
                
                ImportResult.Success(
                    importedTransactions = importedTransactions,
                    importedCategories = importedCategories,
                    skippedDuplicates = skippedDuplicates
                )
            } catch (e: Exception) {
                throw e
            }
        }
    }
    
    /**
     * Import cards with duplicate checking
     */
    private suspend fun importCardsWithMerge(cards: List<CardEntity>) {
        val existingCards = database.cardDao().getAllCards().first()
        val existingCardKeys = existingCards.map { "${it.bankName}_${it.cardLast4}" }.toSet()
        
        cards.forEach { card ->
            val sanitizedCard = card.sanitize()
            val key = "${sanitizedCard.bankName}_${sanitizedCard.cardLast4}"
            if (!existingCardKeys.contains(key)) {
                val newCard = sanitizedCard.copy(id = 0)
                database.cardDao().insertCard(newCard)
            }
        }
    }
    
    /**
     * Import account balances with duplicate checking
     */
    private suspend fun importAccountBalancesWithMerge(
        snapshot: DatabaseSnapshot,
        transactionIds: Map<Long, Long>,
        insertedTransactions: Set<Long>,
        accountsBefore: Set<Pair<String, String>>
    ) {
        // Only what is new here: the rows of transactions just added (under their ids here), and
        // the other rows (setup, calibrations) only of accounts that did not exist. Re-adding every
        // row on each sync piled up copies, and a copy with a higher id could bring back an old balance.
        balancesToImport(snapshot).forEach { balance ->
            val keep = balance.transactionId?.let { it in insertedTransactions }
                ?: ((balance.bankName to balance.accountLast4) !in accountsBefore)
            if (!keep) return@forEach
            val newBalance = balance.copy(id = 0, transactionId = balance.transactionId?.let { transactionIds[it] })
            database.accountBalanceDao().insertBalance(newBalance)
        }
    }

    private suspend fun existingAccountKeys(): Set<Pair<String, String>> =
        database.accountDao().getAccounts().map { it.name to it.last4 }.toSet()

    /**
     * People and loan entries from a backup, under new ids: an id from another device can belong
     * to a different record here. A person with the same name is the same person; an entry whose
     * transaction is already linked to one here is not added again.
     */
    private suspend fun importLendBorrowWithMerge(snapshot: DatabaseSnapshot, transactionIds: Map<Long, Long>) {
        val dao = database.lendBorrowDao()
        val byName = dao.getAllPersonsSync().associateBy { it.name }
        val personIds = snapshot.lendBorrowPersons.associate { person ->
            person.id to (byName[person.name]?.id ?: dao.insertPerson(person.copy(id = 0)))
        }
        snapshot.lendBorrowTransactions.forEach { entry ->
            val personId = personIds[entry.personId] ?: return@forEach
            val linked = entry.transactionId?.let { transactionIds[it] }
            if (linked != null && dao.getTransactionByWalletId(linked) != null) return@forEach
            dao.insertTransaction(entry.copy(id = 0, personId = personId, transactionId = linked))
        }
    }

    /**
     * Brings a backup's accounts and their currencies in, before its transactions and balances,
     * and returns each backup account id's id here. An account already here (same name and
     * last 4) is kept as it is and gains the backup's currencies. (Matched by name, not sync id:
     * balance rows still name their account. One whose sync id is taken here gets a new one.)
     */
    private suspend fun importAccounts(snapshot: DatabaseSnapshot, keepIds: Boolean): Map<Long, Long> {
        val dao = database.accountDao()
        val ids = mutableMapOf<Long, Long>()
        snapshot.accounts.orEmpty().forEach { account ->
            val existing = dao.findAccount(account.name, account.last4)
            ids[account.id] = existing?.id ?: dao.insertAccount(if (keepIds) account else account.copy(id = 0))
        }
        snapshot.accountCurrencies.orEmpty().forEach { currency ->
            ids[currency.accountId]?.let { dao.addCurrency(currency.copy(accountId = it)) }
        }
        return ids
    }

    /** A backup's balance rows, ready to insert; see [inAccountCurrencies]. */
    private fun balancesToImport(snapshot: DatabaseSnapshot): List<AccountBalanceEntity> {
        val balances = snapshot.accountBalances.map { it.sanitize() }
        return if (snapshot.accounts != null) balances else inAccountCurrencies(balances)
    }

    /** Points a backup's transaction at the ids its accounts have here; unknown ones are linked later by name. */
    private fun TransactionEntity.withAccounts(ids: Map<Long, Long>) =
        copy(accountId = accountId?.let { ids[it] }, toAccountId = toAccountId?.let { ids[it] })

    /**
     * Import subscriptions with duplicate checking
     */
    private suspend fun importSubscriptionsWithMerge(subscriptions: List<SubscriptionEntity>) {
        val existingSubscriptions = database.subscriptionDao().getAllSubscriptions().first()
        val existingKeys = existingSubscriptions.map { "${it.merchantName}_${it.amount}" }.toSet()
        
        subscriptions.forEach { subscription ->
            val sanitizedSubscription = subscription.sanitize()
            val key = "${sanitizedSubscription.merchantName}_${sanitizedSubscription.amount}"
            if (!existingKeys.contains(key)) {
                val newSubscription = sanitizedSubscription.copy(id = 0)
                database.subscriptionDao().insertSubscription(newSubscription)
            }
        }
    }
    
    /**
     * Import budgets with merge and ID remapping
     */
    private suspend fun importBudgetsWithMerge(
        budgets: List<BudgetEntity>,
        limits: List<BudgetCategoryLimitEntity>
    ) {
        val existingBudgets = database.budgetDao().getAllBudgets().first()
        // Key by Name + Year + Month
        val existingKeys = existingBudgets.map { "${it.name}_${it.year}_${it.month}" }.toSet()
        
        // Map to track OldID -> NewID for limits
        val idMap = mutableMapOf<Long, Long>()

        budgets.forEach { budget ->
            val sanitizedBudget = budget.sanitize()
            val key = "${sanitizedBudget.name}_${sanitizedBudget.year}_${sanitizedBudget.month}"
            if (!existingKeys.contains(key)) {
                val oldId = sanitizedBudget.id
                val newBudget = sanitizedBudget.copy(id = 0)
                val newId = database.budgetDao().insertBudget(newBudget)
                
                if (oldId != 0L) {
                    idMap[oldId] = newId
                }
            }
        }
        
        // Import limits using new budget IDs
        limits.forEach { limit ->
            val newBudgetId = idMap[limit.budgetId]
            if (newBudgetId != null) {
                // Check if limit already exists for this budget? 
                // Since this is a NEW budget (checked above), limits naturally won't exist.
                val newLimit = limit.copy(id = 0, budgetId = newBudgetId)
                database.budgetDao().insertCategoryLimit(newLimit)
            }
        }
    }

    private suspend fun importExchangeRatesWithMerge(rates: List<ExchangeRateEntity>) {
        rates.forEach { rate ->
            database.exchangeRateDao().insertExchangeRate(rate)
        }
    }

    /**
     * Import user preferences
     */
    private suspend fun importPreferences(preferences: PreferencesSnapshot, restoreOnboardingCompletion: Boolean) {
        // Theme preferences
        preferences.theme.isDarkThemeEnabled?.let {
            userPreferencesRepository.updateDarkTheme(it)
        }
        userPreferencesRepository.updateDynamicColor(preferences.theme.isDynamicColorEnabled)

        // Developer preferences
        userPreferencesRepository.updateDeveloperMode(preferences.developer.isDeveloperModeEnabled)
        userPreferencesRepository.setTokenInfoEnabled(preferences.developer.isTokenInfoEnabled)
        preferences.developer.systemPrompt?.let {
            userPreferencesRepository.updateSystemPrompt(it)
        }
        
        // App preferences
        // During first-run restore, only the caller may mark setup complete after success.
        if (restoreOnboardingCompletion) {
            userPreferencesRepository.updateHasShownScanTutorial(preferences.app.hasShownScanTutorial)
        }
        preferences.app.firstLaunchTime?.let {
            userPreferencesRepository.updateFirstLaunchTime(it)
        }
        preferences.app.lastReviewPromptTime?.let {
            userPreferencesRepository.updateLastReviewPromptTime(it)
        }

        // Extended Theme Preferences
        preferences.theme.isAmoledMode?.let {
            userPreferencesRepository.updateAmoledMode(it)
        }
        
        preferences.theme.appFont?.let { fontName ->
            try {
                val font = AppFont.valueOf(fontName)
                userPreferencesRepository.updateAppFont(font)
            } catch (e: Exception) {
                // Ignore invalid font
            }
        }

        preferences.theme.themeStyle?.let { styleName ->
            try {
                val style = ThemeStyle.valueOf(styleName)
                userPreferencesRepository.updateThemeStyle(style)
            } catch (e: Exception) {
                // Ignore invalid style
            }
        }

        preferences.theme.accentColor?.let { colorName ->
            try {
                val color = AccentColor.valueOf(colorName)
                userPreferencesRepository.updateAccentColor(color)
            } catch (e: Exception) {
                // Ignore invalid color
            }
        }

        preferences.theme.blurEffects?.let {
            userPreferencesRepository.updateBlurEffects(it)
        }

        preferences.theme.appIcon?.let { iconName ->
            try {
                val icon = AppIcon.valueOf(iconName)
                userPreferencesRepository.updateAppIcon(icon)
            } catch (e: Exception) {
                // Ignore invalid icon
            }
        }

        // Profile Preferences
        preferences.profile?.let { profile ->
            userPreferencesRepository.updateUserName(profile.userName)
            
            // Profile Image
            if (profile.profileImageUri == "profile/profile_image") {
                val file = File(context.filesDir, "profile/profile_image")
                if (file.exists()) {
                    userPreferencesRepository.updateProfileImageUri(Uri.fromFile(file).toString())
                }
            } else {
                userPreferencesRepository.updateProfileImageUri(profile.profileImageUri)
            }

            userPreferencesRepository.updateProfileBackgroundColor(profile.profileBackgroundColor)
            
            // Banner Image
            if (profile.bannerImageUri == "profile/banner_image") {
                val file = File(context.filesDir, "profile/banner_image")
                if (file.exists()) {
                    userPreferencesRepository.updateBannerImageUri(Uri.fromFile(file).toString())
                }
            } else {
                userPreferencesRepository.updateBannerImageUri(profile.bannerImageUri)
            }

            userPreferencesRepository.updateShowBannerImage(profile.showBannerImage)
        }

        // Currency Preferences
        preferences.currency?.let { currencyPrefs ->
            userPreferencesRepository.setUnifiedCurrencyEnabled(currencyPrefs.unifiedCurrencyEnabled)
            currencyPrefs.unifiedCurrencyCode?.let {
                userPreferencesRepository.setUnifiedCurrencyCode(it)
            }
            userPreferencesRepository.setDefaultCurrencyEnabled(currencyPrefs.defaultCurrencyEnabled)
            currencyPrefs.defaultCurrencyCode?.let {
                userPreferencesRepository.setDefaultCurrencyCode(it)
            }
            currencyPrefs.customCurrencies.forEach { customCurrency ->
                userPreferencesRepository.addCustomCurrency(customCurrency)
            }
        }
    }
}

/**
 * Balance rows from a backup made before accounts had rows of their own, each in its account's
 * currency: its latest manual row's, else its latest row's. A bug used to stamp stray currencies
 * on an account's rows; as in the 66→67 migration, they must not become currencies that can't be
 * removed.
 */
internal fun inAccountCurrencies(balances: List<AccountBalanceEntity>): List<AccountBalanceEntity> {
    val latest = compareBy<AccountBalanceEntity>({ it.timestamp }, { it.id })
    val mainCurrency = balances.groupBy { it.bankName to it.accountLast4 }.mapValues { (_, rows) ->
        (rows.filter { it.sourceType == "MANUAL" }.maxWithOrNull(latest) ?: rows.maxWith(latest)).currency
    }
    return balances.map { it.copy(currency = mainCurrency.getValue(it.bankName to it.accountLast4)) }
}
