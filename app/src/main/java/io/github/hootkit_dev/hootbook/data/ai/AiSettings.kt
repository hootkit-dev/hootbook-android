package io.github.hootkit_dev.hootbook.data.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The wire protocol a provider speaks. */
enum class AiProtocol(val defaultBaseUrl: String, val defaultModel: String) {
    // Claude Messages API
    ANTHROPIC("https://api.anthropic.com", "claude-opus-5-5"),
    // OpenAI Chat Completions: OpenAI, DeepSeek, Qwen (DashScope), OpenRouter, Gemini's compatible endpoint…
    OPENAI_COMPATIBLE("https://api.openai.com/v1", "")
}

data class AiConfig(
    val protocol: AiProtocol = AiProtocol.ANTHROPIC,
    val baseUrl: String = protocol.defaultBaseUrl,
    val model: String = protocol.defaultModel,
    val apiKey: String = ""
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()
}

/** The cloud model to use, kept with the key in encrypted preferences. Nothing is sent until one is set. */
@Singleton
class AiSettings @Inject constructor(@ApplicationContext private val context: Context) {
    // Whether the key can be kept encrypted; without the keystore it lives only until the app closes
    var keyStoredSecurely = true
        private set

    private val prefs: SharedPreferences by lazy {
        try {
            EncryptedSharedPreferences.create(
                "cashiro_ai_secure_prefs",
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // The settings still persist, but the key is never written in plain text (see save)
            keyStoredSecurely = false
            context.getSharedPreferences("cashiro_ai_prefs", Context.MODE_PRIVATE)
        }
    }

    private val _config by lazy { MutableStateFlow(load()) }
    val config: StateFlow<AiConfig> get() = _config.asStateFlow()

    fun save(config: AiConfig) {
        val clean = config.copy(baseUrl = config.baseUrl.trim().trimEnd('/'), model = config.model.trim(),
            apiKey = config.apiKey.trim())
        prefs.edit {
            putString(KEY_PROTOCOL, clean.protocol.name)
            putString(KEY_BASE_URL, clean.baseUrl)
            putString(KEY_MODEL, clean.model)
            if (keyStoredSecurely) putString(KEY_API_KEY, clean.apiKey) else remove(KEY_API_KEY)
        }
        _config.value = clean
    }

    /** The provider, model and key for a backup the user chose to include them in; null when none is set. */
    fun exportForBackup(): String? {
        val c = config.value.takeIf { it.isConfigured } ?: return null
        return org.json.JSONObject()
            .put(KEY_PROTOCOL, c.protocol.name).put(KEY_BASE_URL, c.baseUrl)
            .put(KEY_MODEL, c.model).put(KEY_API_KEY, c.apiKey)
            .toString()
    }

    /** Takes a backup's provider and key, unless this device already has a key set. */
    fun restoreFromBackup(json: String): Boolean {
        if (config.value.apiKey.isNotBlank()) return false
        val o = org.json.JSONObject(json)
        val protocol = runCatching { AiProtocol.valueOf(o.getString(KEY_PROTOCOL)) }.getOrNull() ?: return false
        save(AiConfig(protocol, o.optString(KEY_BASE_URL, protocol.defaultBaseUrl), o.optString(KEY_MODEL), o.optString(KEY_API_KEY)))
        return true
    }

    private fun load(): AiConfig {
        val protocol = prefs.getString(KEY_PROTOCOL, null)
            ?.let { name -> AiProtocol.entries.firstOrNull { it.name == name } } ?: AiProtocol.ANTHROPIC
        return AiConfig(
            protocol = protocol,
            baseUrl = prefs.getString(KEY_BASE_URL, null) ?: protocol.defaultBaseUrl,
            model = prefs.getString(KEY_MODEL, null) ?: protocol.defaultModel,
            apiKey = prefs.getString(KEY_API_KEY, null).orEmpty()
        )
    }

    private companion object {
        const val KEY_PROTOCOL = "protocol"
        const val KEY_BASE_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_API_KEY = "api_key"
    }
}
