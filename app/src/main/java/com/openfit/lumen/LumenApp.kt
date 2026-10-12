package com.openfit.lumen

import android.app.Application
import com.openfit.lumen.data.Settings
import com.openfit.lumen.data.db.LumenDatabase
import com.openfit.lumen.data.hc.HealthConnectSource
import com.openfit.lumen.data.sync.SyncEngine
import com.openfit.lumen.data.sync.SyncWorker
import com.openfit.lumen.widget.LumenWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Holds the app's single instances: local database, Health Connect source, settings and sync. */
class LumenApp : Application() {
  val database by lazy { LumenDatabase.create(this) }
  val settings by lazy { Settings(this) }
  val healthConnect by lazy { HealthConnectSource(this) }
  val sync by lazy { SyncEngine(database.healthDao(), healthConnect, settings) { LumenWidgets.updateAll(this) } }

  /** App-lifetime scope for small background jobs. */
  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  override fun onCreate() {
    super.onCreate()
    if (settings.info.value.connected) SyncWorker.schedule(this)
    // Widgets may still show a placeholder (e.g. after an update): redraw them from stored data.
    appScope.launch { runCatching { LumenWidgets.updateAll(this@LumenApp) } }
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
    runCatching { LumenWidgets.updateAll(this) }
    withTimeoutOrNull(10_000) { runCatching { healthConnect.revokeAll() } }
  }
}
