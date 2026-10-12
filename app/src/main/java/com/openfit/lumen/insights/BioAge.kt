package com.openfit.lumen.insights

import kotlin.math.roundToInt

/**
 * A transparent "biological age" estimate: calendar age plus a bounded
 * adjustment per factor, each from the person's own Google Health readings.
 * It's for motivation, not a medical test, and the screen says so.
 *
 * Adjustments (years; negative = younger), each capped so no single reading dominates:
 * - Cardio fitness: 0.5 yr per ml/kg/min of VO₂ max away from a unisex age norm (47 at 20, −0.33 a year), ±4.
 * - Resting heart rate: 0.2 yr per bpm away from 62, ±2.
 * - HRV: 0.1 yr per ms away from an age norm (62 − 0.6 × age), ±1.5.
 * - Activity: (150 − zone minutes a week) / 150, from −0.6 to +1.5.
 * - Sleep regularity: 0.6 yr per 30 min of bedtime standard deviation beyond 30 min, from −0.5 to +1.5.
 * - BMI: 0.3 yr per point outside 18.5–25 (−0.2 inside), up to +1.5.
 */
object BioAge {
  enum class Factor(val title: String, val lever: String) {
    CARDIO("Cardio fitness", "regular cardio — three 30-minute sessions a week raise VO₂ max"),
    RESTING_HR("Resting heart rate", "more easy cardio and steady sleep bring resting heart rate down"),
    HRV("Heart rate variability", "consistent sleep and real rest days raise HRV"),
    ACTIVITY("Activity", "reach 150 zone minutes a week"),
    SLEEP_REGULARITY("Sleep regularity", "keep bedtime within a 30-minute window"),
    BMI("Body mass index", "move BMI toward the 18.5–25 range"),
  }

  data class Inputs(
    val calendarAge: Int,
    val vo2Max: Double? = null,
    val restingHr: Double? = null,
    val hrvMs: Double? = null,
    val zoneMinutesPerWeek: Double? = null,
    val bedtimeSdMinutes: Double? = null,
    val bmi: Double? = null,
  )

  data class Contribution(val factor: Factor, val years: Double)

  data class Estimate(val age: Double, val contributions: List<Contribution>) {
    /** The factor adding the most years, if any adds a meaningful amount (a quarter year or more). */
    val biggestLever: Contribution? get() = contributions.filter { it.years >= 0.25 }.maxByOrNull { it.years }
  }

  /** Needs at least two factors with data; otherwise there's too little to say anything. */
  fun estimate(i: Inputs): Estimate? {
    val a = i.calendarAge.toDouble()
    val c = buildList {
      i.vo2Max?.let { add(Contribution(Factor.CARDIO, ((expectedVo2(a) - it) * 0.5).coerceIn(-4.0, 4.0))) }
      i.restingHr?.let { add(Contribution(Factor.RESTING_HR, ((it - 62) * 0.2).coerceIn(-2.0, 2.0))) }
      i.hrvMs?.let { add(Contribution(Factor.HRV, ((expectedHrv(a) - it) * 0.1).coerceIn(-1.5, 1.5))) }
      i.zoneMinutesPerWeek?.let { add(Contribution(Factor.ACTIVITY, ((150 - it) / 150).coerceIn(-0.6, 1.5))) }
      i.bedtimeSdMinutes?.let { add(Contribution(Factor.SLEEP_REGULARITY, ((it - 30) / 30 * 0.6).coerceIn(-0.5, 1.5))) }
      i.bmi?.let { b ->
        val y = when {
          b > 25 -> (b - 25) * 0.3
          b < 18.5 -> (18.5 - b) * 0.3
          else -> -0.2
        }
        add(Contribution(Factor.BMI, y.coerceAtMost(1.5)))
      }
    }
    if (c.size < 2) return null
    return Estimate(a + c.sumOf { it.years }, c)
  }

  fun expectedVo2(age: Double) = 47 - 0.33 * (age - 20)

  fun expectedHrv(age: Double) = 62 - 0.6 * age

  fun round1(v: Double) = (v * 10).roundToInt() / 10.0
}
