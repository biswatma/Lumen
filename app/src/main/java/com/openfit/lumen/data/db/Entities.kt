package com.openfit.lumen.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One Google Health data point, stored exactly as received ([json]) plus a few
 * columns pulled out of it so the app can query without re-parsing.
 *
 * - [startMs]/[endMs]: the observation interval in epoch millis. Samples have
 *   start == end; daily summaries span local midnight to midnight.
 * - [day]: the civil date (yyyy-MM-dd) the point belongs to, in the wearer's
 *   local time when it was recorded. Sleep sessions use the wake-up date.
 * - [value]/[value2]/[label]: per-type extracted fields, see DataPointParser.
 */
@Entity(
  tableName = "data_points",
  indices = [Index("type", "startMs"), Index("type", "day")],
)
data class DataPointEntity(
  @PrimaryKey val name: String,
  val type: String,
  val startMs: Long,
  val endMs: Long,
  val day: String,
  val value: Double?,
  val value2: Double?,
  val label: String?,
  val json: String,
)

/** Per data type bookkeeping for incremental sync. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
  @PrimaryKey val type: String,
  val lastSuccessMs: Long?,
  val lastAttemptMs: Long,
  val lastError: String?,
  val pointsLastRun: Int,
)

data class DayValue(val day: String, val total: Double)

data class TypeCount(val type: String, val count: Int)
