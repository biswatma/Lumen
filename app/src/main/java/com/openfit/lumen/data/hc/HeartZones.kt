package com.openfit.lumen.data.hc

/**
 * Heart-rate zones and Active Zone Minutes, estimated the way Fitbit / Google
 * Health describe them: zones are fractions of heart rate reserve
 * (max HR − resting HR), with max HR = 220 − age. Fat burn = 40–59 %,
 * cardio = 60–84 %, peak = 85 %+. A fat-burn minute earns 1 zone minute; a
 * cardio or peak minute earns 2.
 *
 * Health Connect has no zone-minute record, so these are estimates and can
 * differ slightly from the numbers in the Google Health app.
 */
object HeartZones {
  enum class Zone(val credit: Int) { FAT_BURN(1), CARDIO(2), PEAK(2) }

  /** One minute of heart rate: average and highest bpm of the samples in it. */
  data class Minute(val startMs: Long, val avg: Double, val max: Double)

  /** Bucket raw (time, bpm) samples into whole minutes. */
  fun minutes(samples: List<Pair<Long, Double>>): List<Minute> =
    samples.groupBy { it.first / 60_000 }.map { (minute, s) ->
      Minute(minute * 60_000, s.sumOf { it.second } / s.size, s.maxOf { it.second })
    }.sortedBy { it.startMs }

  fun zone(bpm: Double, restingHr: Double, age: Int): Zone? {
    val maxHr = 220.0 - age
    val reserve = (maxHr - restingHr).coerceAtLeast(20.0)
    val pct = (bpm - restingHr) / reserve
    return when {
      pct >= 0.85 -> Zone.PEAK
      pct >= 0.60 -> Zone.CARDIO
      pct >= 0.40 -> Zone.FAT_BURN
      else -> null
    }
  }

  const val DEFAULT_AGE = 35
  const val DEFAULT_RESTING_HR = 62.0
}
