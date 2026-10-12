package com.openfit.lumen.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.openfit.lumen.LumenApp
import java.util.concurrent.TimeUnit

/** Periodic background sync; runs only if background reads were allowed in Health Connect. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val app = applicationContext as LumenApp
    if (!app.settings.info.value.connected) return Result.success()
    return try {
      app.sync.syncAll(inBackground = true)
      Result.success()
    } catch (e: Exception) {
      if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
  }

  companion object {
    private const val NAME = "google-health-sync"

    fun schedule(context: Context) {
      // Health Connect is on the phone, so no network is needed; avoid draining a low battery.
      val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
        .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
        .build()
      WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
      WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }
  }
}
