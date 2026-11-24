package com.medidiary.app.data

data class AppSettings(
    val patientName: String = "",
    /** Auto-delete entries older than this many days: 30, 60 or custom 1–365 (proposal §9). */
    val retentionDays: Int = 60,
    /** Whisper language: "bn", "en" or "auto". */
    val asrLanguage: String = "bn",
    /** Wrap the transcript with the GGUF chat template (Gemma turns). Turn off if fine-tuned on raw text. */
    val useChatTemplate: Boolean = true,
    val llmContextSize: Int = 1024,
    val llmMaxTokens: Int = 384,
    /** Allow the scripted mock engines when model files are missing (development only). */
    val allowMockEngines: Boolean = true,
    /** Daily check-in reminder (proposal §3 step 1: "A reminder invites the user…"). */
    val reminderEnabled: Boolean = true,
    /** Local time of the reminder, 24 h clock. Default 9:00 PM, so the whole day can be reported. */
    val reminderHour: Int = 21,
    val reminderMinute: Int = 0,
    /** Don't remind on days the user already saved at least one entry. */
    val reminderSkipIfLogged: Boolean = true,
)
