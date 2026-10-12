package com.openfit.lumen.data.hc

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.openfit.lumen.data.HealthType
import com.openfit.lumen.data.HealthTypes
import com.openfit.lumen.data.SleepStages.Stage
import com.openfit.lumen.data.db.DataPointEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.reflect.KClass
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Reads Google Health data from Health Connect on this phone. Only records
 * written by the Google Health app ([GOOGLE_HEALTH_PACKAGE]) are read, so data
 * other apps put into Health Connect never reaches Lumen.
 */
class HealthConnectSource(private val context: Context) {

  fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context, PROVIDER)

  val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

  private val origin = setOf(DataOrigin(GOOGLE_HEALTH_PACKAGE))

  // ---- permissions ----

  private fun available(feature: Int) =
    client.features.getFeatureStatus(feature) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

  val skinTemperatureAvailable: Boolean get() = available(HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE)
  val backgroundReadAvailable: Boolean get() = available(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)
  val historyReadAvailable: Boolean get() = available(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY)

  /** Read permission for a stored type; zone minutes come from heart rate. */
  fun permissionFor(type: HealthType): String? = recordClass(type)?.let { HealthPermission.getReadPermission(it) }

  /** Everything Lumen asks for: read-only data types plus background and history reads where supported. */
  fun requestedPermissions(): Set<String> = buildSet {
    HealthTypes.READ.forEach { type ->
      if (type == HealthTypes.SKIN_TEMP && !skinTemperatureAvailable) return@forEach
      permissionFor(type)?.let(::add)
    }
    if (backgroundReadAvailable) add(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
    if (historyReadAvailable) add(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
  }

  suspend fun grantedPermissions(): Set<String> = client.permissionController.getGrantedPermissions()

  suspend fun revokeAll() = client.permissionController.revokeAllPermissions()

  // ---- reads ----

  /** Every reading of [type] in [from, to), mapped to database rows. Heart rate also yields zone-minute rows. */
  suspend fun read(
    type: HealthType,
    from: Instant,
    to: Instant,
    restingHrByDay: Map<LocalDate, Double> = emptyMap(),
    age: Int = HeartZones.DEFAULT_AGE,
  ): List<DataPointEntity> = when (type) {
    HealthTypes.STEPS -> readAll(StepsRecord::class, from, to).map {
      interval(type, it.metadata, it.startTime, it.endTime, it.startZoneOffset, it.count.toDouble()) { put("count", it.count) }
    }
    HealthTypes.DISTANCE -> readAll(DistanceRecord::class, from, to).map {
      interval(type, it.metadata, it.startTime, it.endTime, it.startZoneOffset, it.distance.inMeters * 1000) { put("meters", it.distance.inMeters) }
    }
    HealthTypes.ACTIVE_CALORIES -> readAll(ActiveCaloriesBurnedRecord::class, from, to).map {
      interval(type, it.metadata, it.startTime, it.endTime, it.startZoneOffset, it.energy.inKilocalories) { put("kcal", it.energy.inKilocalories) }
    }
    HealthTypes.TOTAL_CALORIES -> readAll(TotalCaloriesBurnedRecord::class, from, to).map {
      interval(type, it.metadata, it.startTime, it.endTime, it.startZoneOffset, it.energy.inKilocalories) { put("kcal", it.energy.inKilocalories) }
    }
    HealthTypes.RESTING_HR -> readAll(RestingHeartRateRecord::class, from, to).map {
      sample(type, it.metadata, it.time, it.zoneOffset, it.beatsPerMinute.toDouble()) { put("bpm", it.beatsPerMinute) }
    }
    HealthTypes.HRV -> readAll(HeartRateVariabilityRmssdRecord::class, from, to).map {
      sample(type, it.metadata, it.time, it.zoneOffset, it.heartRateVariabilityMillis) { put("rmssdMs", it.heartRateVariabilityMillis) }
    }
    HealthTypes.SPO2 -> readAll(OxygenSaturationRecord::class, from, to).map {
      sample(type, it.metadata, it.time, it.zoneOffset, it.percentage.value) { put("percent", it.percentage.value) }
    }
    HealthTypes.BREATHING -> readAll(RespiratoryRateRecord::class, from, to).map {
      sample(type, it.metadata, it.time, it.zoneOffset, it.rate) { put("breathsPerMinute", it.rate) }
    }
    HealthTypes.SKIN_TEMP -> readAll(SkinTemperatureRecord::class, from, to).mapNotNull { r ->
      if (r.deltas.isEmpty()) return@mapNotNull null
      val avgDelta = r.deltas.map { it.delta.inCelsius }.average()
      row(type, r.metadata.id, r.startTime, r.endTime, r.endZoneOffset, anchor = r.endTime, value = avgDelta, value2 = r.baseline?.inCelsius) {
        meta(r.metadata)
        put("averageDeltaCelsius", avgDelta)
        r.baseline?.let { put("baselineCelsius", it.inCelsius) }
        put("deltaCount", r.deltas.size)
      }
    }
    HealthTypes.SLEEP -> readAll(SleepSessionRecord::class, from, to).map(::sleepRow)
    HealthTypes.EXERCISE -> readAll(ExerciseSessionRecord::class, from, to).map { r ->
      val minutes = (r.endTime.toEpochMilli() - r.startTime.toEpochMilli()) / 60_000.0
      val name = r.title?.takeIf { it.isNotBlank() } ?: exerciseName(r.exerciseType)
      row(type, r.metadata.id, r.startTime, r.endTime, r.startZoneOffset, value = minutes, label = name) {
        meta(r.metadata)
        put("exerciseType", r.exerciseType)
        r.title?.let { put("title", it) }
      }
    }
    HealthTypes.VO2_MAX -> readAll(Vo2MaxRecord::class, from, to).map {
      sample(type, it.metadata, it.time, it.zoneOffset, it.vo2MillilitersPerMinuteKilogram) {
        put("vo2MlPerMinKg", it.vo2MillilitersPerMinuteKilogram)
        put("measurementMethod", it.measurementMethod)
      }
    }
    HealthTypes.WEIGHT -> readAll(WeightRecord::class, from, to).map {
      sample(type, it.metadata, it.time, it.zoneOffset, it.weight.inKilograms) { put("kg", it.weight.inKilograms) }
    }
    HealthTypes.HEIGHT -> readAll(HeightRecord::class, from, to).map {
      sample(type, it.metadata, it.time, it.zoneOffset, it.height.inMeters) { put("meters", it.height.inMeters) }
    }
    HealthTypes.HEART_RATE -> heartRateRows(from, to, restingHrByDay, age)
    else -> emptyList()
  }

  /** Apps other than Google Health that have steps in Health Connect (shown, never read). */
  suspend fun otherStepSources(days: Long = 7): Set<String> {
    val now = Instant.now()
    val response = client.readRecords(
      ReadRecordsRequest(StepsRecord::class, TimeRangeFilter.between(now.minusSeconds(days * 86_400), now), pageSize = 1000),
    )
    return response.records.map { it.metadata.dataOrigin.packageName }.toSet() - GOOGLE_HEALTH_PACKAGE
  }

  /** Google Health's steps for today, summed live from its records in Health Connect. */
  suspend fun googleHealthStepsToday(): Long {
    val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
    return readAll(StepsRecord::class, start, Instant.now()).filter { it.startTime >= start }.sumOf { it.count }
  }

  /**
   * Health Connect's own step total for today across all apps (it removes overlaps between
   * sources). Null when Health Connect has no total, e.g. no app is on its priority list.
   */
  suspend fun allAppsStepsToday(): Long? {
    val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
    val response = client.aggregate(
      AggregateRequest(metrics = setOf(StepsRecord.COUNT_TOTAL), timeRangeFilter = TimeRangeFilter.between(start, Instant.now())),
    )
    return response[StepsRecord.COUNT_TOTAL]
  }

  /** End time of the newest Google Health steps record in Health Connect. */
  suspend fun newestStepsRecordEnd(): Instant? {
    val now = Instant.now()
    val response = client.readRecords(
      ReadRecordsRequest(StepsRecord::class, TimeRangeFilter.between(now.minusSeconds(7 * 86_400), now), dataOriginFilter = origin, ascendingOrder = false, pageSize = 1),
    )
    return response.records.firstOrNull()?.endTime
  }

  /** The wearable named on the most recent Google Health record, if any. */
  suspend fun latestDeviceName(): String? {
    val now = Instant.now()
    val response = client.readRecords(
      ReadRecordsRequest(StepsRecord::class, TimeRangeFilter.between(now.minusSeconds(3 * 86_400), now), dataOriginFilter = origin, ascendingOrder = false, pageSize = 20),
    )
    return response.records.firstNotNullOfOrNull { r -> r.metadata.device?.let { listOfNotNull(it.manufacturer, it.model).joinToString(" ").ifBlank { null } } }
  }

  private suspend fun <T : Record> readAll(cls: KClass<T>, from: Instant, to: Instant): List<T> {
    val out = mutableListOf<T>()
    var pageToken: String? = null
    do {
      val response = client.readRecords(
        ReadRecordsRequest(cls, TimeRangeFilter.between(from, to), dataOriginFilter = origin, pageSize = 5000, pageToken = pageToken),
      )
      out += response.records
      pageToken = response.pageToken?.takeIf { it.isNotEmpty() }
    } while (pageToken != null)
    return out
  }

  // ---- heart rate → per-minute rows + zone minutes ----

  private suspend fun heartRateRows(from: Instant, to: Instant, restingHrByDay: Map<LocalDate, Double>, age: Int): List<DataPointEntity> {
    val records = readAll(HeartRateRecord::class, from, to)
    val offsets = mutableMapOf<Long, ZoneOffset?>()
    val samples = records.flatMap { r ->
      r.samples.map { s ->
        offsets.putIfAbsent(s.time.toEpochMilli() / 60_000, r.startZoneOffset)
        s.time.toEpochMilli() to s.beatsPerMinute.toDouble()
      }
    }
    val out = mutableListOf<DataPointEntity>()
    for (m in HeartZones.minutes(samples)) {
      val start = Instant.ofEpochMilli(m.startMs)
      val end = start.plusSeconds(60)
      val offset = offsets[m.startMs / 60_000]
      out += row(HealthTypes.HEART_RATE, "heart-rate/${m.startMs}", start, end, offset, value = m.avg, value2 = m.max) {
        put("avgBpm", m.avg)
        put("maxBpm", m.max)
      }
      val day = localDay(start, offset)
      val rhr = restingHrByDay[day] ?: restingHrByDay[day.minusDays(1)] ?: HeartZones.DEFAULT_RESTING_HR
      HeartZones.zone(m.avg, rhr, age)?.let { z ->
        out += row(HealthTypes.ACTIVE_ZONE_MINUTES, "azm/${m.startMs}", start, end, offset, value = z.credit.toDouble(), label = z.name) {
          put("zone", z.name)
          put("avgBpm", m.avg)
          put("restingBpm", rhr)
          put("age", age)
        }
      }
    }
    return out
  }

  // ---- sleep ----

  private fun sleepRow(r: SleepSessionRecord): DataPointEntity {
    val stages = r.stages.mapNotNull { s ->
      val stage = when (s.stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> Stage.AWAKE
        SleepSessionRecord.STAGE_TYPE_REM -> Stage.REM
        SleepSessionRecord.STAGE_TYPE_DEEP -> Stage.DEEP
        SleepSessionRecord.STAGE_TYPE_LIGHT, SleepSessionRecord.STAGE_TYPE_SLEEPING -> Stage.LIGHT
        else -> return@mapNotNull null
      }
      Triple(stage, s.startTime.toEpochMilli(), s.endTime.toEpochMilli())
    }
    val totalMin = (r.endTime.toEpochMilli() - r.startTime.toEpochMilli()) / 60_000.0
    val awakeMin = stages.filter { it.first == Stage.AWAKE }.sumOf { (it.third - it.second) / 60_000.0 }
    val asleepMin = if (stages.isEmpty()) totalMin else stages.filter { it.first != Stage.AWAKE }.sumOf { (it.third - it.second) / 60_000.0 }
    val startHour = r.startTime.atZone(r.startZoneOffset ?: ZoneId.systemDefault()).hour
    val nap = totalMin < 180 && startHour in 9..19
    return row(
      HealthTypes.SLEEP, r.metadata.id, r.startTime, r.endTime, r.endZoneOffset, anchor = r.endTime,
      value = asleepMin, value2 = awakeMin, label = if (nap) "nap" else "sleep",
    ) {
      meta(r.metadata)
      r.title?.let { put("title", it) }
      putJsonArray("stages") {
        stages.forEach { (stage, s, e) -> add(buildJsonObject { put("stage", stage.name); put("startMs", s); put("endMs", e) }) }
      }
    }
  }

  // ---- row builders ----

  private fun interval(type: HealthType, meta: Metadata, start: Instant, end: Instant, offset: ZoneOffset?, value: Double, fields: JsonObjectBuilder.() -> Unit) =
    row(type, meta.id, start, end, offset, value = value) { meta(meta); fields() }

  private fun sample(type: HealthType, meta: Metadata, time: Instant, offset: ZoneOffset?, value: Double, fields: JsonObjectBuilder.() -> Unit) =
    row(type, meta.id, time, time, offset, value = value) { meta(meta); fields() }

  private fun row(
    type: HealthType,
    name: String,
    start: Instant,
    end: Instant,
    offset: ZoneOffset?,
    anchor: Instant = start,
    value: Double? = null,
    value2: Double? = null,
    label: String? = null,
    fields: JsonObjectBuilder.() -> Unit,
  ) = DataPointEntity(
    name = "${type.id}/$name",
    type = type.id,
    startMs = start.toEpochMilli(),
    endMs = end.toEpochMilli(),
    day = localDay(anchor, offset).toString(),
    value = value,
    value2 = value2,
    label = label,
    json = buildJsonObject {
      put("source", "health-connect")
      put("startTime", start.toString())
      put("endTime", end.toString())
      offset?.let { put("zoneOffset", it.id) }
      fields()
    }.toString(),
  )

  private fun JsonObjectBuilder.meta(m: Metadata) {
    putJsonObject("metadata") {
      put("id", m.id)
      put("dataOrigin", m.dataOrigin.packageName)
      put("lastModifiedTime", m.lastModifiedTime.toString())
      m.clientRecordId?.let { put("clientRecordId", it) }
      m.device?.let { d -> putJsonObject("device") { d.manufacturer?.let { put("manufacturer", it) }; d.model?.let { put("model", it) }; put("type", d.type) } }
    }
  }

  private fun localDay(t: Instant, offset: ZoneOffset?): LocalDate = t.atZone(offset ?: ZoneId.systemDefault()).toLocalDate()

  private fun recordClass(type: HealthType): KClass<out Record>? = when (type) {
    HealthTypes.STEPS -> StepsRecord::class
    HealthTypes.DISTANCE -> DistanceRecord::class
    HealthTypes.ACTIVE_CALORIES -> ActiveCaloriesBurnedRecord::class
    HealthTypes.TOTAL_CALORIES -> TotalCaloriesBurnedRecord::class
    HealthTypes.HEART_RATE -> HeartRateRecord::class
    HealthTypes.RESTING_HR -> RestingHeartRateRecord::class
    HealthTypes.HRV -> HeartRateVariabilityRmssdRecord::class
    HealthTypes.SPO2 -> OxygenSaturationRecord::class
    HealthTypes.BREATHING -> RespiratoryRateRecord::class
    HealthTypes.SKIN_TEMP -> SkinTemperatureRecord::class
    HealthTypes.SLEEP -> SleepSessionRecord::class
    HealthTypes.EXERCISE -> ExerciseSessionRecord::class
    HealthTypes.VO2_MAX -> Vo2MaxRecord::class
    HealthTypes.WEIGHT -> WeightRecord::class
    HealthTypes.HEIGHT -> HeightRecord::class
    else -> null
  }

  companion object {
    /** Health Connect itself (the Play Store app on Android 13 and lower). */
    const val PROVIDER = "com.google.android.apps.healthdata"

    /** The Google Health app (formerly Fitbit; the rebrand kept the package name). */
    const val GOOGLE_HEALTH_PACKAGE = "com.fitbit.FitbitMobile"

    fun exerciseName(type: Int): String = when (type) {
      ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walk"
      ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Run"
      ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Treadmill run"
      ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Bike"
      ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Indoor bike"
      ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hike"
      ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Swim"
      ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
      ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Strength training"
      ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
      ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elliptical"
      ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "Rowing machine"
      ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "Pilates"
      ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "Dance"
      else -> "Workout"
    }
  }
}
