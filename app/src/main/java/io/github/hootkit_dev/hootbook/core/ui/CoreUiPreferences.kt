package io.github.hootkit_dev.hootbook.core.ui

import io.github.hootkit_dev.hootbook.data.preferences.*
import org.json.JSONArray
import org.json.JSONObject

/** Android-only preferences. No ledger identifiers, rates, credentials or business settings. */
data class CoreUiPreferences(
    val darkTheme: Boolean? = null,
    val dynamicColor: Boolean = true,
    val amoled: Boolean = false,
    val font: AppFont = AppFont.SYSTEM,
    val themeStyle: ThemeStyle = ThemeStyle.DYNAMIC,
    val accent: AccentColor = AccentColor.BLUE,
    val blur: Boolean = false,
    val appIcon: AppIcon = AppIcon.HOOTBOOK_WHITE,
    val userName: String = "",
    val avatar: String? = null,
    val banner: String? = null,
    val profileBackground: Int = 0,
    val showBanner: Boolean = false,
    val homeOrder: List<HomeWidget> = HomeWidget.entries.sortedBy { it.defaultOrder },
    val hiddenHomeWidgets: Set<HomeWidget> = emptySet(),
    val unifiedDisplayEnabled: Boolean = false,
    val unifiedDisplayCurrency: String? = null,
    val defaultEntryEnabled: Boolean = false,
    val defaultEntryCurrency: String? = null,
    val notificationsEnabled: Boolean = true,
    val scanReminderEnabled: Boolean = true,
    val reminderMinutes: Int = 1200,
    val mutedSubscriptionIds: Set<String> = emptySet(),
    val onboardingFinished: Boolean = false,
    val developerMode: Boolean = false,
    val tokenInfo: Boolean = false,
    val firstLaunchTime: Long = 0,
    val reviewShown: Boolean = false,
    val lastReviewTime: Long = 0,
    val appLockEnabled: Boolean = false,
    val appLockTimeoutMinutes: Int = 1,
)

/** Versioned whitelist: never imports business settings, secrets, timestamps granting access or old IDs. */
object CoreUiPreferencesCodec {
    const val FORMAT = "hootbook.android.ui"
    const val VERSION = 1
    fun encode(p: CoreUiPreferences): JSONObject = JSONObject().put("format", FORMAT).put("version", VERSION)
        .put("theme", JSONObject().put("dark", p.darkTheme ?: JSONObject.NULL).put("dynamic", p.dynamicColor)
            .put("amoled", p.amoled).put("font", p.font.name).put("style", p.themeStyle.name)
            .put("accent", p.accent.name).put("blur", p.blur).put("icon", p.appIcon.name))
        .put("profile", JSONObject().put("name", p.userName).put("avatar", p.avatar ?: JSONObject.NULL)
            .put("banner", p.banner ?: JSONObject.NULL).put("background", p.profileBackground).put("showBanner", p.showBanner))
        .put("home", JSONObject().put("order", JSONArray(p.homeOrder.map { it.name })).put("hidden", JSONArray(p.hiddenHomeWidgets.map { it.name })))
        .put("display", JSONObject().put("unified", p.unifiedDisplayEnabled).put("currency", p.unifiedDisplayCurrency ?: JSONObject.NULL)
            .put("defaultEntry", p.defaultEntryEnabled).put("entryCurrency", p.defaultEntryCurrency ?: JSONObject.NULL))
        .put("notifications", JSONObject().put("enabled", p.notificationsEnabled).put("scan", p.scanReminderEnabled)
            .put("minutes", p.reminderMinutes).put("muted", JSONArray(p.mutedSubscriptionIds.sorted())))
        .put("app", JSONObject().put("onboarded", p.onboardingFinished).put("developer", p.developerMode).put("tokens", p.tokenInfo)
            .put("firstLaunch", p.firstLaunchTime).put("reviewShown", p.reviewShown).put("lastReview", p.lastReviewTime))
        .put("lock", JSONObject().put("enabled", p.appLockEnabled).put("timeout", p.appLockTimeoutMinutes))

    fun decode(json: String, current: CoreUiPreferences, legacy: Boolean = false): CoreUiPreferences {
        require(json.toByteArray().size <= 256 * 1024) { "Preferences exceed size limit" }
        val root = JSONObject(json)
        if (legacy) return decodeLegacy(root, current)
        require(root.optString("format") == FORMAT && root.getInt("version") == VERSION) { "Unsupported UI preferences format" }
        val t = section(root, "theme")
        val p = section(root, "profile")
        val h = section(root, "home")
        val d = section(root, "display")
        val n = section(root, "notifications")
        val a = section(root, "app")
        val l = section(root, "lock")
        return validate(current.copy(
            darkTheme = if (t.has("dark")) nullableBoolean(t, "dark") else current.darkTheme,
            dynamicColor = bool(t, "dynamic", current.dynamicColor), amoled = bool(t, "amoled", current.amoled),
            font = enum(t, "font", current.font), themeStyle = enum(t, "style", current.themeStyle),
            accent = enum(t, "accent", current.accent), blur = bool(t, "blur", current.blur), appIcon = appIcon(t, "icon", current.appIcon),
            userName = str(p, "name", current.userName), avatar = optionalString(p, "avatar", current.avatar),
            banner = optionalString(p, "banner", current.banner), profileBackground = int(p, "background", current.profileBackground),
            showBanner = bool(p, "showBanner", current.showBanner), homeOrder = widgets(h, "order", current.homeOrder),
            hiddenHomeWidgets = widgets(h, "hidden", current.hiddenHomeWidgets.toList()).toSet(),
            unifiedDisplayEnabled = bool(d, "unified", current.unifiedDisplayEnabled), unifiedDisplayCurrency = optionalString(d, "currency", current.unifiedDisplayCurrency),
            defaultEntryEnabled = bool(d, "defaultEntry", current.defaultEntryEnabled), defaultEntryCurrency = optionalString(d, "entryCurrency", current.defaultEntryCurrency),
            notificationsEnabled = bool(n, "enabled", current.notificationsEnabled), scanReminderEnabled = bool(n, "scan", current.scanReminderEnabled),
            reminderMinutes = int(n, "minutes", current.reminderMinutes), mutedSubscriptionIds = strings(n, "muted", current.mutedSubscriptionIds.toList()).toSet(),
            onboardingFinished = bool(a, "onboarded", current.onboardingFinished), developerMode = bool(a, "developer", current.developerMode),
            tokenInfo = bool(a, "tokens", current.tokenInfo), firstLaunchTime = long(a, "firstLaunch", current.firstLaunchTime),
            reviewShown = bool(a, "reviewShown", current.reviewShown), lastReviewTime = long(a, "lastReview", current.lastReviewTime),
            appLockEnabled = bool(l, "enabled", current.appLockEnabled), appLockTimeoutMinutes = int(l, "timeout", current.appLockTimeoutMinutes),
        ))
    }
    private fun decodeLegacy(r: JSONObject, c: CoreUiPreferences): CoreUiPreferences {
        val t = section(r, "theme"); val p = section(r, "profile")
        val h = if(r.has("home_widgets"))section(r,"home_widgets")else section(r,"homeWidgets")
        val a = section(r, "app"); val dev = section(r, "developer")
        val d = section(r, "currency")
        return validate(c.copy(
            darkTheme = if (t.has("is_dark_theme_enabled")) nullableBoolean(t, "is_dark_theme_enabled") else c.darkTheme,
            dynamicColor = bool(t, "is_dynamic_color_enabled", c.dynamicColor), amoled = bool(t, "is_amoled_mode", c.amoled),
            font = enum(t, "app_font", c.font), themeStyle = enum(t, "theme_style", c.themeStyle), accent = enum(t, "accent_color", c.accent),
            blur = bool(t, "blur_effects", c.blur), appIcon = appIcon(t, "app_icon", c.appIcon), userName = str(p, "user_name", c.userName),
            avatar = optionalString(p, "profile_image_uri", c.avatar), banner = optionalString(p, "banner_image_uri", c.banner),
            profileBackground = int(p, "profile_background_color", c.profileBackground), showBanner = bool(p, "show_banner_image", c.showBanner),
            homeOrder = widgets(h, "order", c.homeOrder), hiddenHomeWidgets = widgets(h, "hidden", c.hiddenHomeWidgets.toList()).toSet(),
            unifiedDisplayEnabled = bool(d, "unified_currency_enabled", c.unifiedDisplayEnabled), unifiedDisplayCurrency = legacyCurrency(d, "unified_currency_code", c.unifiedDisplayCurrency),
            defaultEntryEnabled = bool(d, "default_currency_enabled", c.defaultEntryEnabled), defaultEntryCurrency = legacyCurrency(d, "default_currency_code", c.defaultEntryCurrency),
            onboardingFinished = bool(a, "has_shown_scan_tutorial", c.onboardingFinished), developerMode = bool(dev, "is_developer_mode_enabled", c.developerMode),
            tokenInfo = bool(dev, "is_token_info_enabled", c.tokenInfo),
        ))
    }
    fun validate(p: CoreUiPreferences): CoreUiPreferences {
        require(p.userName.length <= 200 && p.reminderMinutes in 0..1439 && p.appLockTimeoutMinutes in 0..1440)
        listOf(p.unifiedDisplayCurrency, p.defaultEntryCurrency).filterNotNull().forEach { require(it.matches(Regex("[A-Z0-9]{1,4}"))) }
        val order = p.homeOrder.distinct() + HomeWidget.entries.sortedBy { it.defaultOrder }.filterNot { it in p.homeOrder }
        return p.copy(homeOrder = order)
    }
    private fun section(o: JSONObject, key: String): JSONObject {
        if(!o.has(key)||o.isNull(key))return JSONObject()
        require(o.get(key) is JSONObject) {"Invalid preferences section"}
        return o.getJSONObject(key)
    }
    private fun legacyCurrency(o: JSONObject,key:String,default:String?):String? {
        val code=optionalString(o,key,default)?.uppercase(java.util.Locale.ROOT)
        return code?.takeIf{it.matches(Regex("[A-Z0-9]{1,4}"))}
    }
    private fun nullableBoolean(o: JSONObject, key: String): Boolean? { if (o.isNull(key)) return null; require(o.get(key) is Boolean); return o.getBoolean(key) }
    private fun bool(o: JSONObject, key: String, default: Boolean): Boolean = if (!o.has(key) || o.isNull(key)) default else { require(o.get(key) is Boolean); o.getBoolean(key) }
    private fun str(o: JSONObject, key: String, default: String): String = if (!o.has(key) || o.isNull(key)) default else { require(o.get(key) is String); o.getString(key) }
    private fun optionalString(o: JSONObject, key: String, default: String?): String? = if (!o.has(key)) default else if (o.isNull(key)) null else { require(o.get(key) is String); o.getString(key) }
    private fun int(o: JSONObject, key: String, default: Int): Int = if (!o.has(key) || o.isNull(key)) default else { require(o.get(key) is Number); val v=o.getLong(key); require(o.get(key).toString().matches(Regex("-?[0-9]+"))); require(v in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()); v.toInt() }
    private fun long(o: JSONObject, key: String, default: Long): Long = if (!o.has(key) || o.isNull(key)) default else { require(o.get(key) is Number); require(o.get(key).toString().matches(Regex("-?[0-9]+"))); o.getLong(key) }
    private fun appIcon(o: JSONObject, key: String, default: AppIcon): AppIcon = if (!o.has(key) || o.isNull(key)) default else AppIcon.fromStoredName(o.getString(key))
    private inline fun <reified E: Enum<E>> enum(o: JSONObject, key: String, default: E): E = if (!o.has(key) || o.isNull(key)) default else enumValueOf(o.getString(key))
    private fun strings(o: JSONObject, key: String, default: List<String>): List<String> { if (!o.has(key) || o.isNull(key)) return default; val v=o.getJSONArray(key); require(v.length()<=1000); return (0 until v.length()).map { require(v.get(it) is String); v.getString(it) } }
    private fun widgets(o: JSONObject, key: String, default: List<HomeWidget>) = strings(o,key,default.map {it.name}).mapNotNull(HomeWidget::fromName).distinct()
}
