package com.medidiary.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.medidiary.app.MediDiaryApp
import java.util.concurrent.TimeUnit

/** Daily 30/60-day auto-retention cleanup (proposal §9). Runs fully on-device. */
class RetentionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MediDiaryApp).container
        container.diaryRepository.applyRetention(container.settings.settings.value.retentionDays)
        container.exportManager.cleanup()
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("retention", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
