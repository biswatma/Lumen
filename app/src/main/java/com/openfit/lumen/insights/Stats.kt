package com.openfit.lumen.insights

import kotlin.math.floor

/** A personal "normal" band computed from the person's own recent readings. */
data class Range(val low: Double, val high: Double) {
  val mid: Double get() = (low + high) / 2
  operator fun contains(v: Double) = v in low..high
}

object Stats {
  /** Linear-interpolated percentile, p in 0..1. */
  fun percentile(values: List<Double>, p: Double): Double {
    require(values.isNotEmpty())
    val s = values.sorted()
    val pos = p * (s.size - 1)
    val lo = floor(pos).toInt()
    val hi = minOf(lo + 1, s.size - 1)
    return s[lo] + (s[hi] - s[lo]) * (pos - lo)
  }

  fun mean(values: List<Double>): Double? = if (values.isEmpty()) null else values.average()

  fun median(values: List<Double>): Double? = if (values.isEmpty()) null else percentile(values, 0.5)

  /**
   * The middle ~70% of the person's readings (15th–85th percentile). Needs at
   * least [minCount] readings, otherwise there is no meaningful "normal" yet.
   */
  fun normalRange(values: List<Double>, minCount: Int = 7): Range? =
    if (values.size < minCount) null else Range(percentile(values, 0.15), percentile(values, 0.85))
}
