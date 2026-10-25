package io.github.tintloom.manager.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Settings(
    /** 0 = follow the phone, 1 = light, 2 = dark */
    val theme: Int = 0,
    val dynamicColor: Boolean = true,
    /** "auto", "standard", "shizuku" or "root" */
    val installMethod: String = "auto",
    /** Check daily and install updates in the background. */
    val autoUpdate: Boolean = true,
    val wifiOnly: Boolean = true,
)

/** Settings, kept privately on the phone. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("manager", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _settings = MutableStateFlow(prefs.getString("settings", null)?.let { runCatching { json.decodeFromString<Settings>(it) }.getOrNull() } ?: Settings())
    val settings: StateFlow<Settings> = _settings

    fun updateSettings(change: (Settings) -> Settings) {
        _settings.update(change)
        prefs.edit().putString("settings", json.encodeToString(Settings.serializer(), _settings.value)).apply()
    }

    /** Set once root worked, so automatic mode doesn't ask the root manager on every check. */
    var rootGranted: Boolean
        get() = prefs.getBoolean("rootGranted", false)
        set(value) = prefs.edit().putBoolean("rootGranted", value).apply()

    var lastCheck: Long
        get() = prefs.getLong("lastCheck", 0)
        set(value) = prefs.edit().putLong("lastCheck", value).apply()
}
