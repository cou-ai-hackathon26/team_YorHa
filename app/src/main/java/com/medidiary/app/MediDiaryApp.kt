package com.medidiary.app

import android.app.Application
import com.medidiary.app.ai.InferencePipeline
import com.medidiary.app.ai.ModelManager
import com.medidiary.app.data.DiaryRepository
import com.medidiary.app.data.SettingsStore
import com.medidiary.app.data.db.AppDatabase
import com.medidiary.app.data.dgda.DgdaLookupRepository
import com.medidiary.app.data.dgda.DgdaSeeder
import com.medidiary.app.export.ExportManager
import com.medidiary.app.reminder.ReminderScheduler
import com.medidiary.app.sync.FamilyPrefs
import com.medidiary.app.sync.FamilySync
import com.medidiary.app.sync.FamilySyncFactory
import com.medidiary.app.work.RetentionWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual DI — small enough that Hilt isn't worth the build time during the sprint. */
class AppContainer(app: Application) {
    val database = AppDatabase.build(app)
    val settings = SettingsStore(app)
    val familyPrefs = FamilyPrefs(app)
    val diaryRepository = DiaryRepository(database, familyPrefs::identity) { familySync.requestSync() }
    /** Task 1: no-op in the offline flavor, encrypted Firestore sync in the family flavor. */
    val familySync: FamilySync by lazy { FamilySyncFactory.create(app, diaryRepository, familyPrefs, settings) }
    val dgdaLookup = DgdaLookupRepository(database)
    val modelManager = ModelManager(app)
    val pipeline = InferencePipeline(modelManager, settings)
    val exportManager = ExportManager(app)
}

class MediDiaryApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            DgdaSeeder.seedIfNeeded(this@MediDiaryApp, container.database)
            container.dgdaLookup.invalidate()
            container.modelManager.extractBundledAssets()
            container.diaryRepository.applyRetention(container.settings.settings.value.retentionDays)
            // Raw audio is deleted after each transcription; this catches crashes mid-flow.
            cacheDir.listFiles { f -> f.extension == "wav" }?.forEach { it.delete() }
        }
        RetentionWorker.schedule(this)
        ReminderScheduler.ensureChannel(this)
        ReminderScheduler.schedule(this, container.settings.settings.value)
        container.familySync.start()
    }
}
