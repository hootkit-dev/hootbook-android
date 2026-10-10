package io.github.hootkit_dev.hootbook.data.backup

import com.google.gson.annotations.SerializedName
import io.github.hootkit_dev.hootbook.data.database.entity.*
import java.time.LocalDateTime

/**
 * Root container for Hootbook backup data
 */
data class HootbookBackup(
    @SerializedName("_format")
    val format: String = "Cashiro Backup v1.0",
    
    @SerializedName("_warning")
    val warning: String = "Contains sensitive financial data. Keep this file secure.",
    
    @SerializedName("_created")
    val created: String = LocalDateTime.now().toString(),
    
    @SerializedName("_checksum")
    val checksum: String = "",
    
    @SerializedName("metadata")
    val metadata: BackupMetadata,
    
    @SerializedName("database")
    val database: DatabaseSnapshot,
    
    @SerializedName("preferences")
    val preferences: PreferencesSnapshot
)

/**
 * Metadata about the backup
 */
data class BackupMetadata(
    @SerializedName("export_id")
    val exportId: String,
    
    @SerializedName("app_version")
    val appVersion: String,
    
    @SerializedName("database_version")
    val databaseVersion: Int,
    
    @SerializedName("device")
    val device: String,
    
    @SerializedName("android_version")
    val androidVersion: Int,
    
    @SerializedName("statistics")
    val statistics: BackupStatistics
)

/**
 * Statistics about the backup content
 */
data class BackupStatistics(
    @SerializedName("total_transactions")
    val totalTransactions: Int,
    
    @SerializedName("total_categories")
    val totalCategories: Int,
    
    @SerializedName("total_cards")
    val totalCards: Int,
    
    @SerializedName("total_subscriptions")
    val totalSubscriptions: Int,
    
    @SerializedName("total_subcategories")
    val totalSubcategories: Int = 0,

    @SerializedName("date_range")
    val dateRange: DateRange?
)

/**
 * Date range of transactions
 */
data class DateRange(
    @SerializedName("earliest")
    val earliest: String?,
    
    @SerializedName("latest")
    val latest: String?
)

/**
 * Complete database snapshot
 */
data class DatabaseSnapshot(
    @SerializedName("transactions")
    val transactions: List<TransactionEntity>,
    
    @SerializedName("categories")
    val categories: List<CategoryEntity>,
    
    @SerializedName("cards")
    val cards: List<CardEntity>,
    
    @SerializedName("account_balances")
    val accountBalances: List<AccountBalanceEntity>,
    
    @SerializedName("subscriptions")
    val subscriptions: List<SubscriptionEntity>,
    
    @SerializedName("budgets")
    val budgets: List<BudgetEntity> = emptyList(),

    @SerializedName("budget_category_limits")
    val budgetCategoryLimits: List<BudgetCategoryLimitEntity> = emptyList(),
    
    @SerializedName("subcategories")
    val subcategories: List<SubcategoryEntity> = emptyList(),

    @SerializedName("exchange_rates")
    val exchangeRates: List<ExchangeRateEntity> = emptyList(),

    // Absent from backups made before accounts had rows of their own (database version 67)
    @SerializedName("accounts")
    val accounts: List<io.github.hootkit_dev.hootbook.data.database.entity.AccountEntity>? = null,

    @SerializedName("account_currencies")
    val accountCurrencies: List<io.github.hootkit_dev.hootbook.data.database.entity.AccountCurrencyEntity>? = null,

    @SerializedName("lend_borrow_persons")
    val lendBorrowPersons: List<LendBorrowPersonEntity> = emptyList(),

    @SerializedName("lend_borrow_transactions")
    val lendBorrowTransactions: List<LendBorrowTransactionEntity> = emptyList()
)

/**
 * User preferences snapshot
 */
data class PreferencesSnapshot(
    @SerializedName("theme")
    val theme: ThemePreferences,
    
    
    @SerializedName("developer")
    val developer: DeveloperPreferences,
    
    @SerializedName("app")
    val app: AppPreferences,

    @SerializedName("profile")
    val profile: ProfilePreferences? = null,

    @SerializedName("home_widgets")
    val homeWidgets: HomeWidgetPreferences? = null,

    @SerializedName("currency")
    val currency: CurrencyPreferences? = null
)

/**
 * Home widget preferences
 */
data class HomeWidgetPreferences(
    @SerializedName("order")
    val order: List<String>,

    @SerializedName("hidden")
    val hidden: List<String>
)

/**
 * Currency preferences
 */
data class CurrencyPreferences(
    @SerializedName("unified_currency_enabled")
    val unifiedCurrencyEnabled: Boolean,
    
    @SerializedName("unified_currency_code")
    val unifiedCurrencyCode: String?,
    
    @SerializedName("default_currency_enabled")
    val defaultCurrencyEnabled: Boolean,
    
    @SerializedName("default_currency_code")
    val defaultCurrencyCode: String?,

    @SerializedName("custom_currencies")
    val customCurrencies: List<io.github.hootkit_dev.hootbook.data.model.CustomCurrency> = emptyList()
)

/**
 * Theme-related preferences
 */
data class ThemePreferences(
    @SerializedName("is_dark_theme_enabled")
    val isDarkThemeEnabled: Boolean?,
    
    @SerializedName("is_dynamic_color_enabled")
    val isDynamicColorEnabled: Boolean,

    @SerializedName("is_amoled_mode")
    val isAmoledMode: Boolean? = null,

    @SerializedName("navigation_bar_style")
    /** Retained so older backups still parse; the floating navigation style no longer exists. */
    val navigationBarStyle: String? = null,

    @SerializedName("app_font")
    val appFont: String? = null,

    @SerializedName("theme_style")
    val themeStyle: String? = null,

    @SerializedName("accent_color")
    val accentColor: String? = null,

    @SerializedName("blur_effects")
    val blurEffects: Boolean? = null,

    @SerializedName("app_icon")
    val appIcon: String? = null
)

/**
 * Developer mode preferences
 */
data class DeveloperPreferences(
    @SerializedName("is_developer_mode_enabled")
    val isDeveloperModeEnabled: Boolean,
    @SerializedName("is_token_info_enabled")
    val isTokenInfoEnabled: Boolean = false,
    @SerializedName("system_prompt")
    val systemPrompt: String?
)

/**
 * App-related preferences
 */
data class AppPreferences(
    @SerializedName("has_shown_scan_tutorial")
    val hasShownScanTutorial: Boolean,
    
    @SerializedName("first_launch_time")
    val firstLaunchTime: Long?,
    
    @SerializedName("has_shown_review_prompt")
    val hasShownReviewPrompt: Boolean,
    
    @SerializedName("last_review_prompt_time")
    val lastReviewPromptTime: Long?
)

/**
 * Profile-related preferences
 */
data class ProfilePreferences(
    @SerializedName("user_name")
    val userName: String = "User",

    @SerializedName("profile_image_uri")
    val profileImageUri: String? = null,

    @SerializedName("profile_background_color")
    val profileBackgroundColor: Int = 0,

    @SerializedName("banner_image_uri")
    val bannerImageUri: String? = null,

    @SerializedName("show_banner_image")
    val showBannerImage: Boolean = false
)

/**
 * Import result
 */
sealed class ImportResult {
    data class Success(
        val importedTransactions: Int,
        val importedCategories: Int,
        val skippedDuplicates: Int,
        val importedAttachments: Int = 0,
        val failedAttachments: Int = 0
    ) : ImportResult()
    
    data class Error(val message: String) : ImportResult()
}

/**
 * Export result
 */
sealed class ExportResult {
    data class Success(val file: java.io.File) : ExportResult()
    data class Error(val message: String) : ExportResult()
    data class Progress(val current: Int, val total: Int) : ExportResult()
}

/**
 * Import strategy options
 */
enum class ImportStrategy {
    REPLACE_ALL,    // Replace all existing data
    MERGE           // Merge with existing data (skip duplicates)
}

/**
 * Configuration for backup export
 */
data class BackupConfiguration(
    val includeTransactionalData: Boolean = true,
    val includeProfileData: Boolean = true,
    val includeBudgets: Boolean = true,
    val includeAppPreferences: Boolean = true,
    // Brokerage connections with their tokens; off unless the user asks or the file is encrypted
    val includeBrokerageCredentials: Boolean = false,
    // The AI provider and its key; the same rule
    val includeAiKey: Boolean = false
)
