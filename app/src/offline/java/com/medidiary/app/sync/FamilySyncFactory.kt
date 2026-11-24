package com.medidiary.app.sync

import android.app.Application
import com.medidiary.app.data.DiaryRepository
import com.medidiary.app.data.SettingsStore

/** `offline` flavor: no network permission, so family sharing is unavailable. */
object FamilySyncFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(app: Application, repo: DiaryRepository, prefs: FamilyPrefs, settings: SettingsStore): FamilySync =
        DisabledFamilySync(prefs)
}
