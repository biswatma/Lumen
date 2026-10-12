package com.openfit.lumen.data

/**
 * The kinds of reading Lumen stores. [id] is the `type` column in the local
 * database. Every type except [HealthTypes.ACTIVE_ZONE_MINUTES] is read from a
 * Health Connect record type; zone minutes are worked out from heart rate.
 */
data class HealthType(
  val id: String,
  val backfillDays: Int,
  /** Rows older than this are pruned after each sync; null keeps the whole backfill. */
  val keepDays: Int? = null,
)

object HealthTypes {
  val STEPS = HealthType("steps", 180)
  val DISTANCE = HealthType("distance", 180)
  val ACTIVE_CALORIES = HealthType("active-calories", 180)
  val TOTAL_CALORIES = HealthType("total-calories", 180)
  // Stored as one row per minute (average and max bpm). 180 days are read so
  // zone minutes can be derived for Trends, but only 30 days of minutes are kept.
  val HEART_RATE = HealthType("heart-rate", 180, keepDays = 30)
  val ACTIVE_ZONE_MINUTES = HealthType("active-zone-minutes", 180)
  val RESTING_HR = HealthType("resting-heart-rate", 180)
  val HRV = HealthType("hrv", 180)
  val SPO2 = HealthType("oxygen-saturation", 180)
  val BREATHING = HealthType("respiratory-rate", 180)
  val SKIN_TEMP = HealthType("skin-temperature", 180)
  val SLEEP = HealthType("sleep", 180)
  val EXERCISE = HealthType("exercise", 180)
  // For biological age: cardio fitness and BMI. Weight and height change rarely, so look back a year.
  val VO2_MAX = HealthType("vo2-max", 180)
  val WEIGHT = HealthType("weight", 365)
  val HEIGHT = HealthType("height", 365 * 3)

  /** Types read from Health Connect, in sync order (resting HR before heart rate, which uses it for zones). */
  val READ = listOf(
    STEPS, DISTANCE, ACTIVE_CALORIES, TOTAL_CALORIES, RESTING_HR, HRV, SPO2, BREATHING, SKIN_TEMP,
    SLEEP, EXERCISE, VO2_MAX, WEIGHT, HEIGHT, HEART_RATE,
  )
}
