package com.jinwoo.ai.data

import android.content.Context
import com.jinwoo.ai.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

data class AppSettings(
    val provider: String = "gemini",            // "gemini" | "openai"
    val openAiModel: String = "",
    val geminiModel: String = "",
    val backendUrl: String = BuildConfig.DEFAULT_BACKEND_URL,
    val wakeWordEnabled: Boolean = false,
    val wakeSensitivity: Float = 0.6f,
    val wakeAlertNotification: Boolean = true,
    val voiceName: String = "",
    val pitch: Float = 0.75f,                   // lower = deeper
    val speed: Float = 0.9f,
    val voiceVolume: Float = 1f,
    val externalTts: Boolean = false,
    val activationAnimation: Boolean = true,
    val activationSound: Boolean = true,
    val soundVolume: Float = 0.8f,
    val customSoundUri: String = "",
    val memoryEnabled: Boolean = true,
    val speechLanguage: String = "en-US",
    val accent: String = "blue",
    val particles: Boolean = true,
) {
    fun activeModel(): String = if (provider == "openai") openAiModel else geminiModel
}

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("jinwoo_settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<AppSettings> = _flow.asStateFlow()
    val current: AppSettings get() = _flow.value

    fun update(block: (AppSettings) -> AppSettings) {
        _flow.update(block)
        prefs.edit().putString("json", toJson(_flow.value).toString()).apply()
    }

    private fun load(): AppSettings {
        val raw = prefs.getString("json", null) ?: return AppSettings()
        return runCatching { fromJson(JSONObject(raw)) }.getOrDefault(AppSettings())
    }

    companion object {
        fun toJson(s: AppSettings) = JSONObject().apply {
            put("provider", s.provider); put("openAiModel", s.openAiModel); put("geminiModel", s.geminiModel)
            put("backendUrl", s.backendUrl); put("wakeWordEnabled", s.wakeWordEnabled)
            put("wakeSensitivity", s.wakeSensitivity.toDouble()); put("wakeAlertNotification", s.wakeAlertNotification)
            put("voiceName", s.voiceName); put("pitch", s.pitch.toDouble()); put("speed", s.speed.toDouble())
            put("voiceVolume", s.voiceVolume.toDouble()); put("externalTts", s.externalTts)
            put("activationAnimation", s.activationAnimation); put("activationSound", s.activationSound)
            put("soundVolume", s.soundVolume.toDouble()); put("customSoundUri", s.customSoundUri)
            put("memoryEnabled", s.memoryEnabled); put("speechLanguage", s.speechLanguage)
            put("accent", s.accent); put("particles", s.particles)
        }

        fun fromJson(o: JSONObject): AppSettings {
            val d = AppSettings()
            fun f(k: String, def: Float) = o.optDouble(k, def.toDouble()).toFloat()
            return AppSettings(
                provider = o.optString("provider", d.provider),
                openAiModel = o.optString("openAiModel", d.openAiModel),
                geminiModel = o.optString("geminiModel", d.geminiModel),
                backendUrl = o.optString("backendUrl", d.backendUrl),
                wakeWordEnabled = o.optBoolean("wakeWordEnabled", d.wakeWordEnabled),
                wakeSensitivity = f("wakeSensitivity", d.wakeSensitivity),
                wakeAlertNotification = o.optBoolean("wakeAlertNotification", d.wakeAlertNotification),
                voiceName = o.optString("voiceName", d.voiceName),
                pitch = f("pitch", d.pitch), speed = f("speed", d.speed), voiceVolume = f("voiceVolume", d.voiceVolume),
                externalTts = o.optBoolean("externalTts", d.externalTts),
                activationAnimation = o.optBoolean("activationAnimation", d.activationAnimation),
                activationSound = o.optBoolean("activationSound", d.activationSound),
                soundVolume = f("soundVolume", d.soundVolume),
                customSoundUri = o.optString("customSoundUri", d.customSoundUri),
                memoryEnabled = o.optBoolean("memoryEnabled", d.memoryEnabled),
                speechLanguage = o.optString("speechLanguage", d.speechLanguage),
                accent = o.optString("accent", d.accent),
                particles = o.optBoolean("particles", d.particles),
            )
        }
    }
}
