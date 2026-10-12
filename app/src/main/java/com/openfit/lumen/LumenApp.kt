package com.openfit.lumen

import android.app.Application
import com.openfit.lumen.data.Settings
import com.openfit.lumen.data.db.LumenDatabase
import com.openfit.lumen.data.hc.HealthConnectSource
import com.openfit.lumen.data.sync.SyncEngine
import com.openfit.lumen.data.sync.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Holds the app's single instances: local database, Health Connect source, settings and sync. */
class LumenApp : Application() {
  val database by lazy { LumenDatabase.create(this) }
  val settings by lazy { Settings(this) }
  val healthConnect by lazy { HealthConnectSource(this) }
  val sync by lazy { SyncEngine(database.healthDao(), healthConnect, settings) }

  override fun onCreate() {
    super.onCreate()
    if (settings.info.value.connected) SyncWorker.schedule(this)
  }

  /**
   * Stop syncing, delete every stored reading, then give back Lumen's Health
   * Connect permissions. Local deletion comes first so it happens even if the
   * revoke fails. Nothing in Health Connect or Google Health is deleted.
   */
  suspend fun disconnect() {
    SyncWorker.cancel(this)
    sync.stopAndRun {
      database.healthDao().deleteAllPoints()
      database.healthDao().deleteAllStates()
      // Rewrite the file so deleted readings don't linger in free pages.
      withContext(Dispatchers.IO) { database.openHelper.writableDatabase.execSQL("VACUUM") }
      settings.clear()
    }
    withTimeoutOrNull(10_000) { runCatching { healthConnect.revokeAll() } }
  }
}
