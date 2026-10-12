package com.openfit.lumen.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthDao {
  @Upsert suspend fun upsert(points: List<DataPointEntity>)

  @Query("SELECT * FROM data_points WHERE type = :type AND startMs >= :fromMs AND startMs < :toMs ORDER BY startMs")
  suspend fun inRange(type: String, fromMs: Long, toMs: Long): List<DataPointEntity>

  @Query("SELECT * FROM data_points WHERE type = :type AND day >= :fromDay AND day <= :toDay ORDER BY startMs")
  suspend fun onDays(type: String, fromDay: String, toDay: String): List<DataPointEntity>

  @Query("SELECT day, SUM(value) AS total FROM data_points WHERE type = :type AND day >= :fromDay AND day <= :toDay GROUP BY day ORDER BY day")
  suspend fun dailySums(type: String, fromDay: String, toDay: String): List<DayValue>

  @Query("SELECT type, COUNT(*) AS count FROM data_points GROUP BY type")
  fun observeCounts(): Flow<List<TypeCount>>

  @Query("DELETE FROM data_points WHERE type = :type AND startMs < :beforeMs")
  suspend fun pruneBefore(type: String, beforeMs: Long)

  @Query("DELETE FROM data_points WHERE type = :type AND startMs >= :fromMs AND startMs < :toMs")
  suspend fun deleteRange(type: String, fromMs: Long, toMs: Long)

  /**
   * Make the stored copy of [type] in [fromMs, toMs) match a fresh read from
   * Health Connect: rows deleted or replaced upstream disappear, in one transaction
   * so screens never see a half-synced window.
   */
  @Transaction
  suspend fun replaceRange(type: String, fromMs: Long, toMs: Long, rows: List<DataPointEntity>) {
    deleteRange(type, fromMs, toMs)
    rows.chunked(2_000).forEach { upsert(it) }
  }

  @Query("DELETE FROM data_points") suspend fun deleteAllPoints()

  @Upsert suspend fun upsertState(state: SyncStateEntity)

  @Query("SELECT * FROM sync_state WHERE type = :type") suspend fun state(type: String): SyncStateEntity?

  @Query("SELECT * FROM sync_state ORDER BY type") fun observeStates(): Flow<List<SyncStateEntity>>

  @Query("DELETE FROM sync_state") suspend fun deleteAllStates()
}
