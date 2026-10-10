package io.github.hootkit_dev.hootbook.data.preferences

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.github.hootkit_dev.hootbook.data.model.CustomCurrency

private val Context.dataStore: DataStore<Preferences> by
        preferencesDataStore(
            name = "user_preferences",
            produceMigrations = { listOf(BaseCurrencyMigration()) }
        )

@Singleton
class UserPreferencesRepository
@Inject
constructor(@ApplicationContext private val context: Context) {
    private object PreferencesKeys {
        val DARK_THEME_ENABLED = booleanPreferencesKey("dark_theme_enabled")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val DEVELOPER_MODE_ENABLED = booleanPreferencesKey("developer_mode_enabled")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val HAS_SHOWN_SCAN_TUTORIAL = booleanPreferencesKey("has_shown_scan_tutorial")
        val BASE_CURRENCY = stringPreferencesKey("base_currency")

        // Currency Settings preferences
        val UNIFIED_CURRENCY_ENABLED = booleanPreferencesKey("unified_currency_enabled")
        val UNIFIED_CURRENCY_CODE = stringPreferencesKey("unified_currency_code")
        val DEFAULT_CURRENCY_ENABLED = booleanPreferencesKey("default_currency_enabled")
        val DEFAULT_CURRENCY_CODE = stringPreferencesKey("default_currency_code")
        val CUSTOM_CURRENCIES_JSON = stringPreferencesKey("custom_currencies_json")

        // Theme preferences
        val IS_AMOLED_MODE = booleanPreferencesKey("is_amoled_mode")
        val THEME_STYLE = stringPreferencesKey("theme_style")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")

        // App Lock preferences
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_TIMEOUT_MINUTES = intPreferencesKey("app_lock_timeout_minutes")
        val LAST_AUTH_TIMESTAMP = longPreferencesKey("last_auth_timestamp")

        // In-App Review preferences
        val FIRST_LAUNCH_TIME = longPreferencesKey("first_launch_time")
        val HAS_SHOWN_REVIEW_PROMPT = booleanPreferencesKey("has_shown_review_prompt")
        val LAST_REVIEW_PROMPT_TIME = longPreferencesKey("last_review_prompt_time")

        // Profile preferences
        val USER_NAME = stringPreferencesKey("user_name")
        val PROFILE_IMAGE_URI = stringPreferencesKey("profile_image_uri")
        val PROFILE_BACKGROUND_COLOR = intPreferencesKey("profile_background_color")
        val BANNER_IMAGE_URI = stringPreferencesKey("banner_image_uri")
        
        // Notification preferences
        val SCAN_NEW_TRANSACTIONS_ENABLED = booleanPreferencesKey("scan_new_transactions_enabled")
        val SCAN_NEW_TRANSACTIONS_ALERT_TIME = longPreferencesKey("scan_new_transactions_alert_time")
        val UPCOMING_NOTIFICATIONS_ENABLED = booleanPreferencesKey("upcoming_notifications_enabled")
        val DISABLED_SUBSCRIPTION_NOTIFICATION_IDS = androidx.datastore.preferences.core.stringSetPreferencesKey("disabled_subscription_notification_ids")
        val SHOW_BANNER_IMAGE = booleanPreferencesKey("show_banner_image")
        val APP_FONT = stringPreferencesKey("app_font")
        
        // Home Widget Preferences
        val HOME_WIDGETS_ORDER = stringPreferencesKey("home_widgets_order")
        val HIDDEN_HOME_WIDGETS = androidx.datastore.preferences.core.stringSetPreferencesKey("hidden_home_widgets")
        val BLUR_EFFECTS = booleanPreferencesKey("blur_effects")
        val APP_ICON = stringPreferencesKey("app_icon")
        val TOKEN_INFO_ENABLED = booleanPreferencesKey("token_info_enabled")
    }

    val userPreferences: Flow<UserPreferences> =
        context.dataStore.data.map { preferences ->
            UserPreferences(
                isDarkThemeEnabled = preferences[PreferencesKeys.DARK_THEME_ENABLED],
                isDynamicColorEnabled = preferences[PreferencesKeys.DYNAMIC_COLOR_ENABLED]
                    ?: true,
                isDeveloperModeEnabled = preferences[PreferencesKeys.DEVELOPER_MODE_ENABLED]
                    ?: false,
                isTokenInfoEnabled = preferences[PreferencesKeys.TOKEN_INFO_ENABLED]
                    ?: false,
                hasShownScanTutorial = preferences[PreferencesKeys.HAS_SHOWN_SCAN_TUTORIAL]
                    ?: false,
                baseCurrency = preferences[PreferencesKeys.BASE_CURRENCY]
                    ?: "CNY",
                isAmoledMode = preferences[PreferencesKeys.IS_AMOLED_MODE] ?: false,
                userName = preferences[PreferencesKeys.USER_NAME] ?: "User",
                profileImageUri = preferences[PreferencesKeys.PROFILE_IMAGE_URI],
                profileBackgroundColor =
                    preferences[PreferencesKeys.PROFILE_BACKGROUND_COLOR] ?: 0,
                bannerImageUri = preferences[PreferencesKeys.BANNER_IMAGE_URI],
                showBannerImage = preferences[PreferencesKeys.SHOW_BANNER_IMAGE] ?: false,
                appFont = try {
                    AppFont.valueOf(
                        preferences[PreferencesKeys.APP_FONT] ?: AppFont.SYSTEM.name
                    )
                } catch (e: Exception) {
                    AppFont.SYSTEM

                },
                themeStyle = try {
                    ThemeStyle.valueOf(
                        preferences[PreferencesKeys.THEME_STYLE] ?: ThemeStyle.DYNAMIC.name
                    )
                } catch (e: Exception) {
                    ThemeStyle.DYNAMIC
                },
                accentColor = try {
                    AccentColor.valueOf(
                        preferences[PreferencesKeys.ACCENT_COLOR] ?: AccentColor.BLUE.name
                    )
                } catch (e: Exception) {
                    AccentColor.BLUE
                },
                blurEffects = preferences[PreferencesKeys.BLUR_EFFECTS] ?: false,
                appIcon = try {
                    AppIcon.valueOf(
                        preferences[PreferencesKeys.APP_ICON] ?: AppIcon.ORIGINAL.name
                    )
                } catch (e: Exception) {
                    AppIcon.ORIGINAL
                },
                unifiedCurrencyEnabled = preferences[PreferencesKeys.UNIFIED_CURRENCY_ENABLED] ?: false,
                unifiedCurrencyCode = preferences[PreferencesKeys.UNIFIED_CURRENCY_CODE],
                defaultCurrencyEnabled = preferences[PreferencesKeys.DEFAULT_CURRENCY_ENABLED] ?: false,
                defaultCurrencyCode = preferences[PreferencesKeys.DEFAULT_CURRENCY_CODE]
            )
        }

    val baseCurrency: Flow<String> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.BASE_CURRENCY] ?: "CNY"
        }

    // Currency Settings flows
    val unifiedCurrencyEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.UNIFIED_CURRENCY_ENABLED] ?: false
        }

    val unifiedCurrencyCode: Flow<String?> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.UNIFIED_CURRENCY_CODE]
        }

    val defaultCurrencyEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.DEFAULT_CURRENCY_ENABLED] ?: false
        }

    val defaultCurrencyCode: Flow<String?> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.DEFAULT_CURRENCY_CODE]
        }

    val customCurrencies: Flow<List<CustomCurrency>> =
        context.dataStore.data.map { preferences ->
            val json = preferences[PreferencesKeys.CUSTOM_CURRENCIES_JSON]
            if (json != null) {
                runCatching {
                    Json.decodeFromString<List<CustomCurrency>>(json)
                }.getOrElse { emptyList() }
            } else {
                emptyList()
            }
        }

    suspend fun addCustomCurrency(currency: CustomCurrency) {
        context.dataStore.edit { preferences ->
            val json = preferences[PreferencesKeys.CUSTOM_CURRENCIES_JSON]
            val currentList = if (json != null) {
                runCatching { Json.decodeFromString<List<CustomCurrency>>(json) }.getOrElse { emptyList() }
            } else {
                emptyList()
            }
            val newList = currentList.filterNot { it.code.equals(currency.code, ignoreCase = true) } + currency
            preferences[PreferencesKeys.CUSTOM_CURRENCIES_JSON] = Json.encodeToString(newList)
        }
    }

    suspend fun updateBaseCurrency(currency: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BASE_CURRENCY] = currency
        }
    }

    suspend fun setUnifiedCurrencyEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.UNIFIED_CURRENCY_ENABLED] = enabled
        }
    }

    suspend fun setUnifiedCurrencyCode(code: String?) {
        context.dataStore.edit { preferences ->
            if (code == null) {
                preferences.remove(PreferencesKeys.UNIFIED_CURRENCY_CODE)
            } else {
                preferences[PreferencesKeys.UNIFIED_CURRENCY_CODE] = code
            }
        }
    }

    suspend fun setDefaultCurrencyEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_CURRENCY_ENABLED] = enabled
        }
    }

    suspend fun setDefaultCurrencyCode(code: String?) {
        context.dataStore.edit { preferences ->
            if (code == null) {
                preferences.remove(PreferencesKeys.DEFAULT_CURRENCY_CODE)
            } else {
                preferences[PreferencesKeys.DEFAULT_CURRENCY_CODE] = code
            }
        }
    }

    val isDeveloperModeEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.DEVELOPER_MODE_ENABLED] ?: false
        }

    suspend fun updateDarkThemeEnabled(enabled: Boolean?) {
        context.dataStore.edit { preferences ->
            if (enabled == null) {
                preferences.remove(PreferencesKeys.DARK_THEME_ENABLED)
            } else {
                preferences[PreferencesKeys.DARK_THEME_ENABLED] = enabled
            }
        }
    }

    suspend fun updateDynamicColorEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DYNAMIC_COLOR_ENABLED] = enabled
        }
    }

    suspend fun updateAmoledMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_AMOLED_MODE] = enabled
        }
    }

    suspend fun updateThemeStyle(style: ThemeStyle) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_STYLE] = style.name
        }
    }

    suspend fun updateAccentColor(color: AccentColor) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCENT_COLOR] = color.name
        }
    }

    suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEVELOPER_MODE_ENABLED] = enabled
        }
    }

    val isTokenInfoEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.TOKEN_INFO_ENABLED] ?: false
        }

    suspend fun setTokenInfoEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TOKEN_INFO_ENABLED] = enabled
        }
    }

    suspend fun updateSystemPrompt(prompt: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SYSTEM_PROMPT] = prompt
        }
    }

    fun getSystemPrompt(): Flow<String?> =
            context.dataStore.data.map { preferences -> preferences[PreferencesKeys.SYSTEM_PROMPT] }

    suspend fun markScanTutorialShown() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SHOWN_SCAN_TUTORIAL] = true
        }
    }

    suspend fun setFirstLaunchTime(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FIRST_LAUNCH_TIME] = timestamp
        }
    }

    suspend fun hasShownReviewPrompt(): Boolean {
        return context.dataStore
                .data
                .map { preferences ->
                    preferences[PreferencesKeys.HAS_SHOWN_REVIEW_PROMPT] ?: false
                }
                .first()
    }

    // Flow methods for backup/restore
    fun getFirstLaunchTime(): Flow<Long?> =
            context.dataStore.data.map { preferences ->
                preferences[PreferencesKeys.FIRST_LAUNCH_TIME]
            }

    fun getHasShownReviewPrompt(): Flow<Boolean> =
            context.dataStore.data.map { preferences ->
                preferences[PreferencesKeys.HAS_SHOWN_REVIEW_PROMPT] ?: false
            }

    fun getLastReviewPromptTime(): Flow<Long?> =
            context.dataStore.data.map { preferences ->
                preferences[PreferencesKeys.LAST_REVIEW_PROMPT_TIME]
            }

    // Update methods for import
    suspend fun updateDarkTheme(enabled: Boolean?) {
        updateDarkThemeEnabled(enabled)
    }

    suspend fun updateDynamicColor(enabled: Boolean) {
        updateDynamicColorEnabled(enabled)
    }

    suspend fun updateDeveloperMode(enabled: Boolean) {
        setDeveloperModeEnabled(enabled)
    }

    suspend fun updateFirstLaunchTime(timestamp: Long) {
        setFirstLaunchTime(timestamp)
    }

    suspend fun updateHasShownScanTutorial(shown: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SHOWN_SCAN_TUTORIAL] = shown
        }
    }

    suspend fun updateLastReviewPromptTime(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_REVIEW_PROMPT_TIME] = timestamp
        }
    }

    // App Lock methods
    val isAppLockEnabled: Flow<Boolean> =
            context.dataStore.data.map { preferences ->
                preferences[PreferencesKeys.APP_LOCK_ENABLED] ?: false
            }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LOCK_ENABLED] = enabled
        }
    }

    /**
     * Atomically updates both app lock enabled state and authentication timestamp. This prevents
     * race conditions where the flow sees enabled=true but timestamp=0.
     */
    suspend fun setAppLockEnabledWithTimestamp(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LOCK_ENABLED] = enabled
            if (enabled) {
                preferences[PreferencesKeys.LAST_AUTH_TIMESTAMP] = System.currentTimeMillis()
            }
        }
    }

    val appLockTimeoutMinutes: Flow<Int> =
            context.dataStore.data.map { preferences ->
                preferences[PreferencesKeys.APP_LOCK_TIMEOUT_MINUTES] ?: 1 // Default to 1 minute
            }

    /**
     * Atomically updates timeout and authentication timestamp. This prevents immediate lock when
     * changing timeout by resetting the auth time.
     */
    suspend fun setAppLockTimeoutWithTimestamp(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LOCK_TIMEOUT_MINUTES] = minutes
            preferences[PreferencesKeys.LAST_AUTH_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun getAppLockTimeoutMinutes(): Int {
        return context.dataStore
                .data
                .map { preferences -> preferences[PreferencesKeys.APP_LOCK_TIMEOUT_MINUTES] ?: 1 }
                .first()
    }

    suspend fun setLastAuthTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_AUTH_TIMESTAMP] = timestamp
        }
    }

    suspend fun getLastAuthTimestamp(): Long {
        return context.dataStore
                .data
                .map { preferences -> preferences[PreferencesKeys.LAST_AUTH_TIMESTAMP] ?: 0L }
                .first()
    }

    fun getLastAuthTimestampFlow(): Flow<Long> =
            context.dataStore.data.map { preferences ->
                preferences[PreferencesKeys.LAST_AUTH_TIMESTAMP] ?: 0L
            }

    suspend fun updateUserName(name: String) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.USER_NAME] = name }
    }

    suspend fun updateProfileImageUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri == null) {
                preferences.remove(PreferencesKeys.PROFILE_IMAGE_URI)
            } else {
                preferences[PreferencesKeys.PROFILE_IMAGE_URI] = uri
            }
        }
    }

    suspend fun updateProfileBackgroundColor(color: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PROFILE_BACKGROUND_COLOR] = color
        }
    }

    suspend fun updateBannerImageUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri == null) {
                preferences.remove(PreferencesKeys.BANNER_IMAGE_URI)
            } else {
                preferences[PreferencesKeys.BANNER_IMAGE_URI] = uri
            }
        }
    }

    suspend fun updateShowBannerImage(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_BANNER_IMAGE] = show
        }
    }

    // Notification Preferences
    val scanNewTransactionsEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.SCAN_NEW_TRANSACTIONS_ENABLED] ?: true
        }

    val scanNewTransactionsAlertTime: Flow<Long> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.SCAN_NEW_TRANSACTIONS_ALERT_TIME] ?: 1200L // Default 20:00 (1200 minutes)
        }

    val upcomingNotificationsEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.UPCOMING_NOTIFICATIONS_ENABLED] ?: true
        }

    suspend fun setUpcomingNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.UPCOMING_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    val disabledSubscriptionNotificationIds: Flow<Set<String>> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.DISABLED_SUBSCRIPTION_NOTIFICATION_IDS] ?: emptySet()
        }

    suspend fun toggleSubscriptionNotification(id: Long, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.DISABLED_SUBSCRIPTION_NOTIFICATION_IDS] ?: emptySet()
            if (enabled) {
                preferences[PreferencesKeys.DISABLED_SUBSCRIPTION_NOTIFICATION_IDS] = current - id.toString()
            } else {
                preferences[PreferencesKeys.DISABLED_SUBSCRIPTION_NOTIFICATION_IDS] = current + id.toString()
            }
        }
    }

    suspend fun updateAppFont(font: AppFont) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_FONT] = font.name
        }
    }

    // Home Widget Preferences
    val homeWidgetsOrder: Flow<List<HomeWidget>> =
        context.dataStore.data.map { preferences ->
            val orderString = preferences[PreferencesKeys.HOME_WIDGETS_ORDER]
            if (orderString != null) {
                orderString.split(",")
                    .mapNotNull { HomeWidget.fromName(it) }
            } else {
                emptyList() // Empty list means use default order logic in ViewModel
            }
        }

    val hiddenHomeWidgets: Flow<Set<HomeWidget>> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.HIDDEN_HOME_WIDGETS]
                ?.mapNotNull { HomeWidget.fromName(it) }
                ?.toSet()
                ?: emptySet()
        }

    suspend fun updateHomeWidgetsOrder(order: List<HomeWidget>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HOME_WIDGETS_ORDER] = order.joinToString(",") { it.name }
        }
    }

    suspend fun updateHiddenHomeWidgets(hidden: Set<HomeWidget>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HIDDEN_HOME_WIDGETS] = hidden.map { it.name }.toSet()
        }
    }

    /** Clears the saved order and hidden set so the home screen falls back to defaults. */
    suspend fun resetHomeWidgetsLayout() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.HOME_WIDGETS_ORDER)
            preferences.remove(PreferencesKeys.HIDDEN_HOME_WIDGETS)
        }
    }

    suspend fun updateBlurEffects(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BLUR_EFFECTS] = enabled
        }
    }

    suspend fun updateAppIcon(icon: AppIcon) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_ICON] = icon.name
        }
    }
}

data class UserPreferences(
        val isDarkThemeEnabled: Boolean? = null, // null means follow system
        val isDynamicColorEnabled: Boolean = true, // Default to dynamic colors
        val isDeveloperModeEnabled: Boolean = false,
        val isTokenInfoEnabled: Boolean = false,
        val hasShownScanTutorial: Boolean = false,
        val baseCurrency: String = "CNY", // Default to CNY
        val isAmoledMode: Boolean = false,
        val userName: String = "User",
        val profileImageUri: String? = null,
        val profileBackgroundColor: Int = 0,
        val bannerImageUri: String? = null,
        val showBannerImage: Boolean = false,
        val appFont: AppFont = AppFont.SYSTEM,
        val themeStyle: ThemeStyle = ThemeStyle.DYNAMIC,
        val accentColor: AccentColor = AccentColor.BLUE,
        val blurEffects: Boolean = false,
        val appIcon: AppIcon = AppIcon.ORIGINAL,
        // Currency Settings preferences
        val unifiedCurrencyEnabled: Boolean = false,
        val unifiedCurrencyCode: String? = null,
        val defaultCurrencyEnabled: Boolean = false,
        val defaultCurrencyCode: String? = null
)
