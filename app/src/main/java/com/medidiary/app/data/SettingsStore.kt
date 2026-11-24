package com.medidiary.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun read() = AppSettings(
        patientName = prefs.getString("patientName", "") ?: "",
        retentionDays = prefs.getInt("retentionDays", 60),
        asrLanguage = prefs.getString("asrLanguage", "bn") ?: "bn",
        useChatTemplate = prefs.getBoolean("useChatTemplate", true),
        llmContextSize = prefs.getInt("llmContextSize", 1024),
        llmMaxTokens = prefs.getInt("llmMaxTokens", 384),
        allowMockEngines = prefs.getBoolean("allowMockEngines", true),
        reminderEnabled = prefs.getBoolean("reminderEnabled", true),
        reminderHour = prefs.getInt("reminderHour", 21),
        reminderMinute = prefs.getInt("reminderMinute", 0),
        reminderSkipIfLogged = prefs.getBoolean("reminderSkipIfLogged", true),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value)
        prefs.edit()
            .putString("patientName", s.patientName)
            .putInt("retentionDays", s.retentionDays)
            .putString("asrLanguage", s.asrLanguage)
            .putBoolean("useChatTemplate", s.useChatTemplate)
            .putInt("llmContextSize", s.llmContextSize)
            .putInt("llmMaxTokens", s.llmMaxTokens)
            .putBoolean("allowMockEngines", s.allowMockEngines)
            .putBoolean("reminderEnabled", s.reminderEnabled)
            .putInt("reminderHour", s.reminderHour)
            .putInt("reminderMinute", s.reminderMinute)
            .putBoolean("reminderSkipIfLogged", s.reminderSkipIfLogged)
            .apply()
        _settings.value = s
    }
}
