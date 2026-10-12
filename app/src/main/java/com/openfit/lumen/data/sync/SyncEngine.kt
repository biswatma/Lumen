package com.openfit.lumen.data.sync

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import com.openfit.lumen.data.HealthType
import com.openfit.lumen.data.HealthTypes
import com.openfit.lumen.data.Settings
import com.openfit.lumen.data.db.HealthDao
import com.openfit.lumen.data.db.SyncStateEntity
import com.openfit.lumen.data.hc.HealthConnectSource
import com.openfit.lumen.data.hc.HeartZones
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SyncStatus {
  data object Idle : SyncStatus
  data class Running(val step: String, val done: Int, val total: Int) : SyncStatus
  data class Finished(val atMs: Long, val failedTypes: List<String>) : SyncStatus
  /** Health Connect is missing, or Lumen has no permissions left. */
  data object NoAccess : SyncStatus
}

/**
 * Copies Google Health readings from Health Connect into the local database.
 * Each type re-reads from (last success − 2 days) so late and edited records
 * are picked up; the first sync backfills [HealthType.backfillDays] (Health
 * Connect only returns more than 30 days of history if the history permission
 * is granted). Rows are upserted by Health Connect record id, so re-reading a
 * window never duplicates anything, and one failing type doesn't stop the rest.
 */
class SyncEngine(
  private val dao: HealthDao,
  private val source: HealthConnectSource,
  private val settings: Settings,
  /** Called after each completed sync, e.g. to refresh home-screen widgets. */
  private val onSynced: suspend () -> Unit = {},
) {
  private val mutex = Mutex()
  private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
  val status: StateFlow<SyncStatus> = _status.asStateFlow()

  @Volatile private var stopRequested = false

  /** Make a running sync stop at the next step, then run [block] with syncing locked out. */
  suspend fun stopAndRun(block: suspend () -> Unit) {
    stopRequested = true
    try {
      mutex.withLock { block() }
    } finally {
      stopRequested = false
      _status.value = SyncStatus.Idle
    }
  }

  /**
   * Sync every type. [inBackground] syncs only run if the background-read
   * permission was granted. Returns false when there is no access at all.
   */
  suspend fun syncAll(inBackground: Boolean = false, waitIfRunning: Boolean = false): Boolean {
    // A sync already running is normally enough; a pull-to-refresh waits for it and then reads again.
    if (mutex.isLocked && !waitIfRunning) return true
    return mutex.withLock { runSync(inBackground, heartRateFrom = null) }
  }

  /** Re-derive zone minutes for the heart-rate minutes still stored, e.g. after the age changes. */
  suspend fun recomputeZones() = mutex.withLock {
    val keep = HealthTypes.HEART_RATE.keepDays?.toLong() ?: 30
    runSync(inBackground = false, heartRateFrom = Instant.now().minus(keep, ChronoUnit.DAYS), only = HealthTypes.HEART_RATE)
  }

  private suspend fun runSync(inBackground: Boolean, heartRateFrom: Instant?, only: HealthType? = null): Boolean {
    if (source.sdkStatus() != HealthConnectClient.SDK_AVAILABLE) {
      _status.value = SyncStatus.NoAccess
      return false
    }
    val granted = source.grantedPermissions()
    val types = only?.let(::listOf) ?: HealthTypes.READ
    if (HealthTypes.READ.none { source.permissionFor(it) in granted }) {
      settings.update { it.copy(accessOff = true) }
      _status.value = SyncStatus.NoAccess
      return false
    }
    if (inBackground && HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND !in granted) return true

    val now = Instant.now()
    val failed = mutableListOf<String>()
    for ((i, type) in types.withIndex()) {
      if (stopRequested) return true
      _status.value = SyncStatus.Running(type.id, i, types.size)
      val previous = dao.state(type.id)
      if (source.permissionFor(type) !in granted) {
        failed += type.id
        dao.upsertState(SyncStateEntity(type.id, previous?.lastSuccessMs, now.toEpochMilli(), NOT_ALLOWED, 0))
        continue
      }
      val earliest = now.minus(type.backfillDays.toLong(), ChronoUnit.DAYS)
      val from = heartRateFrom.takeIf { type == HealthTypes.HEART_RATE }
        ?: previous?.lastSuccessMs?.let { Instant.ofEpochMilli(it).minus(2, ChronoUnit.DAYS) }?.takeIf { it.isAfter(earliest) }
        ?: earliest
      try {
        val count = syncType(type, from, now)
        if (stopRequested) return true
        dao.upsertState(SyncStateEntity(type.id, now.toEpochMilli(), now.toEpochMilli(), null, count))
      } catch (e: CancellationException) {
        throw e
      } catch (e: SecurityException) {
        failed += type.id
        dao.upsertState(SyncStateEntity(type.id, previous?.lastSuccessMs, now.toEpochMilli(), NOT_ALLOWED, 0))
      } catch (e: Exception) {
        failed += type.id
        dao.upsertState(SyncStateEntity(type.id, previous?.lastSuccessMs, now.toEpochMilli(), e.message ?: e.javaClass.simpleName, 0))
      }
    }
    if (stopRequested) return true

    val device = runCatching { source.latestDeviceName() }.getOrNull()
    val others = runCatching { source.otherStepSources() }.getOrNull()
    settings.update {
      it.copy(
        lastSyncMs = now.toEpochMilli(),
        accessOff = false,
        deviceName = device ?: it.deviceName,
        ignoredSources = others ?: it.ignoredSources,
      )
    }
    _status.value = SyncStatus.Finished(now.toEpochMilli(), failed)
    runCatching { onSynced() }
    return true
  }

  /** Reads [type] in chunks (a day at a time for dense heart rate) and stores it. Returns rows written. */
  private suspend fun syncType(type: HealthType, from: Instant, to: Instant): Int {
    val chunk = if (type == HealthTypes.HEART_RATE) 1L else 30L
    val age = settings.info.value.age ?: HeartZones.DEFAULT_AGE
    var count = 0
    var start = from
    while (start < to) {
      if (stopRequested) return count
      val end = minOf(start.plus(chunk, ChronoUnit.DAYS), to)
      val restingHr = if (type == HealthTypes.HEART_RATE) restingHrByDay(start, end) else emptyMap()
      // Read a little past both edges, then keep only rows that start inside this chunk. Records
      // that cross a chunk edge are then stored exactly once, whichever way Health Connect matches
      // interval records against a time range.
      val margin = if (type == HealthTypes.HEART_RATE) Duration.ofHours(2) else Duration.ofDays(1)
      val rows = source.read(type, start.minus(margin), end.plus(margin), restingHr, age)
        .filter { it.startMs >= start.toEpochMilli() && it.startMs < end.toEpochMilli() }
      // Replace, don't merge: records deleted or rewritten in Health Connect must not linger here.
      // Heart rate also yields derived zone-minute rows, which are replaced the same way.
      rows.groupBy { it.type }.let { byType ->
        val types = if (type == HealthTypes.HEART_RATE) listOf(HealthTypes.HEART_RATE.id, HealthTypes.ACTIVE_ZONE_MINUTES.id) else listOf(type.id)
        types.forEach { t -> dao.replaceRange(t, start.toEpochMilli(), end.toEpochMilli(), byType[t].orEmpty()) }
      }
      count += rows.size
      start = end
    }
    type.keepDays?.let { dao.pruneBefore(type.id, to.minus(it.toLong(), ChronoUnit.DAYS).toEpochMilli()) }
    if (type == HealthTypes.HEART_RATE) {
      dao.upsertState(SyncStateEntity(HealthTypes.ACTIVE_ZONE_MINUTES.id, to.toEpochMilli(), to.toEpochMilli(), null, 0))
    }
    return count
  }

  private suspend fun restingHrByDay(from: Instant, to: Instant): Map<LocalDate, Double> {
    val zone = java.time.ZoneId.systemDefault()
    val fromDay = from.atZone(zone).toLocalDate().minusDays(1)
    val toDay = to.atZone(zone).toLocalDate()
    return dao.onDays(HealthTypes.RESTING_HR.id, fromDay.toString(), toDay.toString())
      .filter { it.value != null }
      .groupBy { LocalDate.parse(it.day) }
      .mapValues { (_, l) -> l.mapNotNull { it.value }.average() }
  }

  companion object {
    const val NOT_ALLOWED = "Not allowed in Health Connect"
  }
}
