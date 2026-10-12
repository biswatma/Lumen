package com.openfit.lumen.insights

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object Fmt {
  fun duration(minutes: Double?): String {
    if (minutes == null) return "—"
    val m = minutes.roundToLong()
    val h = m / 60
    return if (h == 0L) "${m % 60}m" else "${h}h ${(m % 60).toString().padStart(2, '0')}m"
  }

  /** Signed minutes: +38m, −1h 05m. */
  fun signedDuration(minutes: Double): String {
    if (abs(minutes) < 0.5) return "0m"
    val sign = if (minutes < 0) "−" else "+"
    return sign + duration(abs(minutes))
  }

  fun int(v: Double?): String = if (v == null) "—" else NumberFormat.getIntegerInstance(Locale.getDefault()).format(v.roundToLong())

  fun dec(v: Double?, digits: Int = 1): String = if (v == null) "—" else String.format(Locale.getDefault(), "%.${digits}f", v)

  /** Signed number with a real minus sign: +5, −2.4. */
  fun signed(v: Double, digits: Int = 0): String {
    val body = if (digits == 0) abs(v).roundToInt().toString() else String.format(Locale.getDefault(), "%.${digits}f", abs(v))
    if (body.all { it == '0' || !it.isDigit() }) return body // never "−0.0"
    return (if (v < 0) "−" else "+") + body
  }

  fun pct(v: Double): String = Fmt.signed(v) + "%"
}

/** Time-of-day formatting that follows the phone's 12/24-hour setting. */
class Clock24(private val is24Hour: Boolean, val zone: ZoneId = ZoneId.systemDefault()) {
  private val f: DateTimeFormatter = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", Locale.getDefault())
  private val hourOnly: DateTimeFormatter = DateTimeFormatter.ofPattern(if (is24Hour) "HH" else "ha", Locale.getDefault())

  fun time(ms: Long): String = f.format(Instant.ofEpochMilli(ms).atZone(zone))

  fun time(t: LocalTime): String = f.format(t)

  fun hour(h: Int): String = if (is24Hour) h.toString().padStart(2, '0') else hourOnly.format(LocalTime.of(h % 24, 0)).lowercase()
}
