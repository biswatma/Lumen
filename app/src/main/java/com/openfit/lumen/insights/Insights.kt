package com.openfit.lumen.insights

import com.openfit.lumen.data.HealthType
import com.openfit.lumen.data.HealthTypes
import com.openfit.lumen.data.SleepStages
import com.openfit.lumen.data.SleepStages.Stage
import com.openfit.lumen.data.hc.HeartZones
import com.openfit.lumen.data.db.DataPointEntity
import com.openfit.lumen.data.db.HealthDao
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Computes every screen from the local database. Nothing here talks to the
 * network: all "normal ranges" and comparisons come from the person's own
 * stored Google Health readings.
 */
class Insights(
  private val dao: HealthDao,
  private val clock: Clock24,
  /** The age heart-rate zones were estimated with, or null if the person hasn't set it. */
  private val age: () -> Int? = { null },
  private val now: () -> Instant = Instant::now,
) {
  private val zone get() = clock.zone
  private val dayShort = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
  private val dayLong = DateTimeFormatter.ofPattern("'Today,' d MMMM", Locale.getDefault())
  private val weekday = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

  fun today(): LocalDate = now().atZone(zone).toLocalDate()

  // ======================================================================
  // Today
  // ======================================================================

  suspend fun todayModel(): TodayModel {
    val t = today()
    val sleeps = mainSleeps(t.minusDays(40), t)
    val lastSleep = sleeps[t] ?: sleeps[t.minusDays(1)]
    val usualSleep = lastSleep?.let { last -> Stats.median(sleeps.filterKeys { it < LocalDate.parse(last.day) }.values.mapNotNull { it.value }.takeLast(30)) }

    val rhr = overnight(HealthTypes.RESTING_HR, t)
    val hrv = overnight(HealthTypes.HRV, t)
    val breathing = overnight(HealthTypes.BREATHING, t)
    val spo2 = overnight(HealthTypes.SPO2, t)
    val skin = overnight(HealthTypes.SKIN_TEMP, t)

    // ---- recovery ----
    val signals = mutableListOf<Signal>()
    var sleepDelta: Double? = null
    if (lastSleep?.value != null && usualSleep != null) {
      sleepDelta = lastSleep.value - usualSleep
      signals += Signal("Sleep", Fmt.signedDuration(sleepDelta), toneOf(sleepDelta, good = 15.0, bad = -30.0))
    }
    val rhrDelta = rhr.deltaFromMean
    if (rhrDelta != null) signals += Signal("Resting HR", Fmt.signed(rhrDelta) + " bpm", toneOf(-rhrDelta, good = 1.0, bad = -3.0))
    val hrvDelta = hrv.deltaFromMean
    if (hrvDelta != null) signals += Signal("HRV", Fmt.signed(hrvDelta) + " ms", toneOf(hrvDelta, good = 3.0, bad = -5.0))
    val recovery = recovery(signals, sleepDelta, rhrDelta, hrvDelta)

    // ---- cards ----
    val steps = dailySums(HealthTypes.STEPS, t, t)[t] ?: 0.0
    val weekStart = t.with(DayOfWeek.MONDAY)
    val azm = dailySums(HealthTypes.ACTIVE_ZONE_MINUTES, weekStart, t)
    val azmToday = azm[t] ?: 0.0
    val azmWeek = azm.values.sum()
    val lastStages = lastSleep?.let { SleepStages.fromStored(it.json) }.orEmpty()
    val deepRem = lastStages.filter { it.stage == Stage.DEEP || it.stage == Stage.REM }.sumOf { it.minutes }

    val cards = listOf(
      MetricCard(
        "Sleep", Fmt.duration(lastSleep?.value), "", frac(lastSleep?.value, 0.0, 480.0), Accent.RECOVERY,
        when {
          lastSleep == null -> "No sleep recorded last night"
          usualSleep == null -> "Building your baseline"
          lastStages.isEmpty() -> "Usual ${Fmt.duration(usualSleep)}"
          else -> "Usual ${Fmt.duration(usualSleep)} · ${Fmt.duration(deepRem)} deep + REM"
        },
        Tab.SLEEP,
      ),
      vitalCard("Resting HR", rhr, "bpm", 0, 40.0, 90.0, "lower is better", Tab.HEART),
      vitalCard("HRV", hrv, "ms", 0, 0.0, (hrv.range?.high ?: 60.0) * 1.4, "higher is better", Tab.HEART),
      MetricCard(
        "Steps", Fmt.int(steps), "", frac(steps, 0.0, STEP_GOAL), Accent.ACTIVITY,
        "${(steps / STEP_GOAL * 100).roundToInt()}% of ${Fmt.int(STEP_GOAL)}" + (paceProjection(steps)?.let { " · on pace for ${Fmt.int(it)}" } ?: ""),
        Tab.ACTIVITY,
      ),
      MetricCard(
        "Active Zone Min", Fmt.int(azmToday), "min", frac(azmWeek, 0.0, AZM_GOAL), Accent.ACTIVITY,
        "${Fmt.int(azmWeek)} of ${Fmt.int(AZM_GOAL)} this week", Tab.ACTIVITY,
      ),
      vitalCard("Breathing rate", breathing, "/min", 1, 8.0, 24.0, "overnight", Tab.SLEEP),
      vitalCard("Blood oxygen", spo2, "%", 0, 85.0, 100.0, "overnight avg", Tab.SLEEP),
      MetricCard(
        "Skin temp", skin.value?.let { Fmt.signed(it, 1) } ?: "—", "°C", frac(skin.value, -2.0, 2.0), Accent.RECOVERY,
        if (skin.value == null) "No reading yet" else "vs your baseline" + (skin.range?.let { " · normal ${Fmt.signed(it.low, 1)} to ${Fmt.signed(it.high, 1)}" } ?: ""),
        Tab.SLEEP,
      ),
    )

    // ---- in plain words ----
    val notes = mutableListOf<PlainNote>()
    val monitorNote = overnightNote(
      listOf(
        Triple("resting heart rate", rhr, Concern.HIGH),
        Triple("HRV", hrv, Concern.LOW),
        Triple("breathing rate", breathing, Concern.HIGH),
        Triple("blood oxygen", spo2, Concern.LOW),
        Triple("skin temperature", skin, Concern.EITHER),
      ),
    )
    monitorNote?.let(notes::add)
    quietNote(t.minusDays(1))?.let(notes::add)
    lastSleep?.let { bedtimeNote(it, sleeps.values.toList()) }?.let(notes::add)

    // ---- rings, coaching, monitor ----
    val plan = bedtimePlan(sleeps.values.toList())
    val coaching = if (signals.isEmpty()) recovery.body else recovery.body + (plan?.let { " Aim for bed by ${it.second}." } ?: "")
    val monitor = listOf(
      MonitorItem("RR", breathing.value?.let { Fmt.dec(it, 1) } ?: "—", "rpm", monitorTone(breathing, Concern.HIGH), Tab.SLEEP),
      MonitorItem("RHR", rhr.value?.let { Fmt.int(it) } ?: "—", "bpm", monitorTone(rhr, Concern.HIGH), Tab.HEART),
      MonitorItem("HRV", hrv.value?.let { Fmt.int(it) } ?: "—", "ms", monitorTone(hrv, Concern.LOW), Tab.HEART),
      MonitorItem("SpO₂", spo2.value?.let { Fmt.dec(it, 0) } ?: "—", "%", monitorTone(spo2, Concern.LOW), Tab.SLEEP),
      MonitorItem("Temp", skin.value?.let { Fmt.signed(it, 1) } ?: "—", "°C", monitorTone(skin, Concern.EITHER), Tab.SLEEP),
    )

    return TodayModel(
      dateLabel = dayLong.format(t),
      dataUpTo = dataUpTo(),
      recovery = recovery,
      cards = cards,
      notes = notes,
      moveScore = (steps / STEP_GOAL * 100).roundToInt(),
      recoveryScore = recoveryScore(sleepDelta, rhrDelta, hrvDelta),
      sleepScore = lastSleep?.value?.let { (it / SLEEP_NEED * 100).roundToInt().coerceAtMost(100) },
      coaching = coaching,
      steps = Fmt.int(steps),
      stepsFraction = frac(steps, 0.0, STEP_GOAL),
      distance = dailySums(HealthTypes.DISTANCE, t, t)[t]?.let { "${Fmt.dec(it / 1_000_000)} km" } ?: "—",
      activeKcal = Fmt.int(dailySums(HealthTypes.ACTIVE_CALORIES, t, t)[t]),
      onPace = paceProjection(steps)?.let(Fmt::int),
      monitor = monitor,
      monitorNote = monitorNote,
      bio = bioAgeModel(),
    )
  }

  /** Newest stored steps / heart-rate reading: how current Google Health's data in Health Connect is. */
  private suspend fun dataUpTo(): String? {
    val nowMs = now().toEpochMilli()
    val ms = dao.latestEnd(listOf(HealthTypes.STEPS.id, HealthTypes.HEART_RATE.id), nowMs + 60_000) ?: return null
    val day = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    return when (day) {
      today() -> clock.time(ms)
      today().minusDays(1) -> "yesterday ${clock.time(ms)}"
      else -> "${dayShort.format(day)} ${clock.time(ms)}"
    }
  }

  private fun monitorTone(o: Overnight, concern: Concern): Tone = when {
    o.value == null || o.range == null -> Tone.NEUTRAL
    o.concerning(concern) -> Tone.BAD
    else -> Tone.GOOD
  }

  /**
   * (wind-down, target bedtime) for 8 hours asleep before the usual wake time,
   * allowing the usual time awake in bed. Needs 3+ of the last 14 nights.
   */
  private fun bedtimePlan(sleeps: List<DataPointEntity>): Pair<String, String>? {
    val recent = sleeps.sortedBy { it.day }.takeLast(14).filter { it.value != null }
    if (recent.size < 3) return null
    val wake = Stats.median(recent.map { minuteOfDay(it.endMs).toDouble() })!!
    val overhead = Stats.median(recent.map { ((it.endMs - it.startMs) / 60_000.0 - it.value!!).coerceAtLeast(0.0) })!!
    val target = Math.floorMod((wake - SLEEP_NEED - overhead).roundToInt(), 1440)
    val windDown = Math.floorMod(target - 30, 1440)
    fun t(m: Int) = clock.time(LocalTime.of(m / 60, m % 60))
    return t(windDown) to t(target)
  }

  private fun recovery(signals: List<Signal>, sleepDelta: Double?, rhrDelta: Double?, hrvDelta: Double?): Recovery {
    if (signals.isEmpty()) {
      return Recovery(
        "Not enough data yet",
        "Wear your device overnight. Lumen compares last night's sleep and resting heart rate with your own normal, which takes about a week of readings.",
        signals,
      )
    }
    val good = signals.count { it.tone == Tone.GOOD }
    val bad = signals.count { it.tone == Tone.BAD }
    val headline = when {
      bad >= 2 || (bad == 1 && good == 0 && signals.size == 1) -> "Your body is still recovering."
      good >= 2 && bad == 0 -> "You recovered well."
      else -> "Recovery is about normal."
    }
    val parts = mutableListOf<String>()
    sleepDelta?.let {
      parts += when {
        it >= 10 -> "you slept ${Fmt.duration(it)} longer than usual"
        it <= -10 -> "you slept ${Fmt.duration(-it)} less than usual"
        else -> "you slept about your usual amount"
      }
    }
    rhrDelta?.let {
      val r = abs(it).roundToInt()
      parts += when {
        r == 0 -> "your resting heart rate is at your normal"
        it < 0 -> "your resting heart rate is $r bpm below normal"
        else -> "your resting heart rate is $r bpm above normal"
      }
    }
    if (rhrDelta == null && hrvDelta != null) {
      val h = abs(hrvDelta).roundToInt()
      parts += if (hrvDelta >= 0) "your HRV is $h ms above normal" else "your HRV is $h ms below normal"
    }
    val advice = when (headline) {
      "You recovered well." -> "A good day for a harder workout."
      "Your body is still recovering." -> "Keep today light and aim for an earlier night."
      else -> "A normal training day is fine."
    }
    val sentence = parts.joinToString(" and ").replaceFirstChar { it.uppercase() }
    return Recovery(headline, "$sentence. $advice", signals)
  }

  /** Which way out of the normal range is worth mentioning for an overnight reading. */
  enum class Concern { HIGH, LOW, EITHER }

  private fun Overnight.concerning(concern: Concern): Boolean {
    val v = value ?: return false
    val r = range ?: return false
    return when (concern) {
      Concern.HIGH -> v > r.high
      Concern.LOW -> v < r.low
      Concern.EITHER -> v !in r
    }
  }

  private fun overnightNote(readings: List<Triple<String, Overnight, Concern>>): PlainNote? {
    val known = readings.filter { it.second.value != null && it.second.range != null }
    if (known.isEmpty()) return null
    val flagged = known.filter { (_, o, c) -> o.concerning(c) }.map { it.first }
    if (flagged.isEmpty()) return PlainNote(NoteKind.OK, "Every overnight reading is inside, or on the good side of, your normal range.")
    val names = if (flagged.size == 1) flagged[0] else flagged.dropLast(1).joinToString(", ") + " and " + flagged.last()
    val verb = if (flagged.size == 1) "is" else "are"
    return PlainNote(NoteKind.NUDGE, "Your $names $verb outside your normal range. One night isn't a concern; several in a row can mean illness or stress.")
  }

  private suspend fun quietNote(day: LocalDate): PlainNote? {
    val quiet = quietStretch(hourlySteps(day), lastHour = 23) ?: return null
    val steps = dailySums(HealthTypes.STEPS, day, day)[day]
    val gap = steps?.takeIf { it in 1.0..<STEP_GOAL }?.let { " You finished ${Fmt.int(STEP_GOAL - it)} steps short of ${Fmt.int(STEP_GOAL)}." } ?: ""
    return PlainNote(
      NoteKind.NUDGE,
      "Yesterday was quietest from ${clock.time(LocalTime.of(quiet.first, 0))} to ${clock.time(LocalTime.of((quiet.last + 1) % 24, 0))}, with under ${QUIET_STEPS.toInt()} steps an hour. A 10-minute walk in the middle breaks it up.$gap",
    )
  }

  /** Steps per local hour of [day]. Records spanning several hours are split in proportion to time. */
  private suspend fun hourlySteps(day: LocalDate): DoubleArray {
    val dayStart = startMs(day)
    val byHour = DoubleArray(24)
    for (p in dao.inRange(HealthTypes.STEPS.id, dayStart - 3_600_000, startMs(day.plusDays(1)))) {
      val v = p.value ?: continue
      val span = (p.endMs - p.startMs).coerceAtLeast(1)
      for (h in 0 until 24) {
        val hs = dayStart + h * 3_600_000L
        val overlap = minOf(p.endMs.coerceAtLeast(p.startMs + 1), hs + 3_600_000) - maxOf(p.startMs, hs)
        if (overlap > 0) byHour[h] += v * overlap / span
      }
    }
    return byHour
  }

  /** Longest run of 2+ waking hours (08–21) under [QUIET_STEPS] steps each, up to [lastHour]. */
  private fun quietStretch(byHour: DoubleArray, lastHour: Int): IntRange? {
    var best: IntRange? = null
    var runStart = -1
    for (h in 8..minOf(21, lastHour) + 1) {
      val quiet = h <= minOf(21, lastHour) && byHour[h] < QUIET_STEPS
      if (quiet && runStart < 0) runStart = h
      if (!quiet && runStart >= 0) {
        val run = runStart until h
        if (run.count() >= 2 && (best == null || run.count() > best.count())) best = run
        runStart = -1
      }
    }
    return best
  }

  private fun bedtimeNote(last: DataPointEntity, all: List<DataPointEntity>): PlainNote? {
    val others = all.filter { it.day < last.day }.takeLast(30).map(::bedtimeMinutes)
    if (others.size < 5) return null
    val diff = bedtimeMinutes(last) - others.average()
    return when {
      diff >= 30 -> PlainNote(NoteKind.INFO, "Bedtime was ${Fmt.duration(diff)} later than your average — the biggest thing to keep steady.")
      diff <= -30 -> PlainNote(NoteKind.INFO, "Bedtime was ${Fmt.duration(-diff)} earlier than your average.")
      else -> PlainNote(NoteKind.OK, "Bedtime was within half an hour of your average. Keeping it steady helps everything else.")
    }
  }

  // ======================================================================
  // Sleep
  // ======================================================================

  suspend fun sleepModel(): SleepModel? {
    val t = today()
    val sleeps = mainSleeps(t.minusDays(40), t)
    val last = sleeps.entries.maxByOrNull { it.key }?.value ?: return null
    val lastDay = LocalDate.parse(last.day)
    val before = sleeps.filterKeys { it < lastDay }.values.mapNotNull { it.value }.takeLast(30)
    val usual = Stats.median(before)
    val asleep = last.value ?: 0.0
    val stages = SleepStages.fromStored(last.json)
    val total = (last.endMs - last.startMs).coerceAtLeast(1).toFloat()

    val spans = stages.map { HypnoSpan(it.stage, (it.startMs - last.startMs) / total, (it.endMs - it.startMs) / total) }
    val axis = (0..4).map { i -> clock.time(last.startMs + ((last.endMs - last.startMs) * i / 4)) }

    val stageMinutes = Stage.entries.associateWith { s -> stages.filter { it.stage == s }.sumOf { it.minutes } }
    val stageTotal = stageMinutes.values.sum().coerceAtLeast(1.0)
    val awakeSpans = stages.filter { it.stage == Stage.AWAKE }
    val rows = if (stages.isEmpty()) emptyList() else listOf(Stage.DEEP, Stage.REM, Stage.LIGHT, Stage.AWAKE).map { s ->
      val pct = (stageMinutes.getValue(s) / stageTotal * 100).roundToInt()
      val typical = TYPICAL.getValue(s)
      val note = if (s == Stage.AWAKE) {
        if (awakeSpans.isEmpty()) "No wake-ups recorded"
        else "${awakeSpans.size} ${if (awakeSpans.size == 1) "wake" else "wakes"}, longest ${Fmt.duration(awakeSpans.maxOf { it.minutes })}"
      } else {
        val r = "${typical.low.roundToInt()}–${typical.high.roundToInt()}%"
        when {
          pct > typical.high -> "Above typical $r"
          pct < typical.low -> "Below typical $r"
          else -> "Inside typical $r"
        }
      }
      StageRow(s, STAGE_NAMES.getValue(s), Fmt.duration(stageMinutes.getValue(s)), pct, typical, note)
    }

    val summary = buildString {
      if (usual != null) {
        val d = asleep - usual
        append(
          when {
            d >= 10 -> "${Fmt.duration(d)} more than your usual ${Fmt.duration(usual)}."
            d <= -10 -> "${Fmt.duration(-d)} less than your usual ${Fmt.duration(usual)}."
            else -> "About your usual ${Fmt.duration(usual)}."
          },
        )
      } else append("Lumen needs a few more nights to learn your usual.")
      if (rows.isNotEmpty()) {
        fun level(s: Stage) = rows.first { it.stage == s }.let { r -> if (r.pct > r.typical.high) "high" else if (r.pct < r.typical.low) "low" else "normal" }
        append(" Deep sleep was ${level(Stage.DEEP)}; REM was ${level(Stage.REM)}.")
      } else append(" This night was recorded without sleep stages.")
    }

    // last 7 nights ending on the latest night
    val avg30 = Stats.mean(before)
    val nights = (6 downTo 0).map { lastDay.minusDays(it.toLong()) }.map { d ->
      val v = sleeps[d]?.value
      NightBar(
        weekday.format(d),
        v?.let { "${(it / 60).toInt()}:${(it % 60).roundToInt().toString().padStart(2, '0')}" } ?: "–",
        (v ?: 0.0) / 60.0,
        v != null && avg30 != null && v < avg30 - 45,
        d == lastDay,
      )
    }
    val week = (6 downTo 0).mapNotNull { sleeps[lastDay.minusDays(it.toLong())] }
    val nightsNote = if (week.size < 3) "Fewer than three nights recorded this week." else buildString {
      val beds = week.map(::bedtimeMinutes)
      val spread = beds.max() - beds.min()
      append(if (spread < 45) "Your bedtime stayed within 45 minutes all week — nicely consistent." else "Bedtime varied by ${Fmt.duration(spread)} this week.")
      val shortest = week.minByOrNull { it.value ?: Double.MAX_VALUE }
      if (shortest?.value != null && week.size >= 4) {
        append(" Shortest night: ${weekday.format(LocalDate.parse(shortest.day))}, ${Fmt.duration(shortest.value)} after a ${clock.time(shortest.startMs)} bedtime.")
      }
    }

    // overnight vitals for the wake-up day
    val lowest = dao.inRange(HealthTypes.HEART_RATE.id, last.startMs, last.endMs).mapNotNull { it.value }.minOrNull()
    val vitals = listOf(
      Vital("Lowest heart rate", lowest?.let { "${it.roundToInt()} bpm" } ?: "—", if (lowest == null) "No reading" else "During this sleep", Tone.NEUTRAL),
      vital("Blood oxygen avg", overnight(HealthTypes.SPO2, lastDay, exact = true), Concern.LOW) { "${Fmt.dec(it, 0)}%" },
      vital("Breathing rate", overnight(HealthTypes.BREATHING, lastDay, exact = true), Concern.HIGH) { "${Fmt.dec(it, 1)}/min" },
      vital("Skin temperature", overnight(HealthTypes.SKIN_TEMP, lastDay, exact = true), Concern.EITHER) { "${Fmt.signed(it, 1)} °C" },
    )

    val label = if (lastDay == t) "Last night" else dayShort.format(lastDay)
    val deepRow = rows.firstOrNull { it.stage == Stage.DEEP }
    val usualDelta = usual?.let { asleep - it }
    val insightTitle = when {
      rows.isEmpty() -> "Recorded without stages"
      deepRow != null && deepRow.pct > deepRow.typical.high -> "Deep sleep ran high"
      deepRow != null && deepRow.pct < deepRow.typical.low -> "Deep sleep ran low"
      usualDelta != null && usualDelta >= 30 -> "A longer night than usual"
      usualDelta != null && usualDelta <= -30 -> "A shorter night than usual"
      else -> "A typical night"
    }
    val plan = bedtimePlan(sleeps.values.toList())
    return SleepModel(
      dateLabel = "$label · ${dayShort.format(Instant.ofEpochMilli(last.startMs).atZone(zone).toLocalDate())} → ${dayShort.format(lastDay)}",
      asleep = Fmt.duration(asleep),
      window = "${clock.time(last.startMs)} → ${clock.time(last.endMs)}",
      awake = "${Fmt.duration(last.value2 ?: stageMinutes.getValue(Stage.AWAKE))} awake",
      summary = summary,
      spans = spans,
      axis = axis,
      stages = rows,
      nights = nights,
      avgHours = avg30?.div(60.0),
      nightsNote = nightsNote,
      vitals = vitals,
      score = (asleep / SLEEP_NEED * 100).roundToInt().coerceAtMost(100),
      timeInBed = Fmt.duration((last.endMs - last.startMs) / 60_000.0),
      insightTitle = insightTitle,
      insightBody = summary,
      windDown = plan?.first,
      targetBedtime = plan?.second,
    )
  }

  // ======================================================================
  // Heart
  // ======================================================================

  suspend fun heartModel(day: LocalDate): HeartModel {
    val rhr = overnight(HealthTypes.RESTING_HR, day, exact = true)
    val hrv = overnight(HealthTypes.HRV, day, exact = true)
    val dayStart = startMs(day)
    val dayEnd = startMs(day.plusDays(1))
    val samples = dao.inRange(HealthTypes.HEART_RATE.id, dayStart, dayEnd).filter { it.value != null }
    val azm = dao.inRange(HealthTypes.ACTIVE_ZONE_MINUTES.id, dayStart, dayEnd)

    fun slotOf(ms: Long) = (minuteOfDay(ms) / 30).coerceIn(0, 47)
    val slotZone = azm.groupBy { slotOf(it.startMs) }.mapValues { (_, l) -> l.maxOf { zoneOf(it.label).ordinal } }
    // Rows are one per minute: value = average bpm, value2 = highest bpm in that minute.
    fun high(p: DataPointEntity) = p.value2 ?: p.value!!
    val slots = samples.groupBy { slotOf(it.startMs) }.map { (i, l) ->
      HrSlot(i, l.minOf { it.value!! }, l.maxOf(::high), Zone.entries[slotZone[i] ?: 0])
    }.sortedBy { it.index }

    val peak = samples.maxByOrNull(::high)
    val highlight = if (peak == null) "No heart rate readings for this day yet." else {
      val workout = dao.onDays(HealthTypes.EXERCISE.id, day.toString(), day.toString()).firstOrNull { peak.startMs in it.startMs..it.endMs }
      val bpm = high(peak).roundToInt()
      if (workout != null) "Your highest stretch was a ${Fmt.duration(workout.value)} ${(workout.label ?: "workout").lowercase()} at ${clock.time(workout.startMs)}, peaking at $bpm bpm."
      else "Your heart rate peaked at $bpm bpm around ${clock.time(peak.startMs)}."
    }

    val zoneMinutes = listOf(Zone.FAT_BURN, Zone.CARDIO, Zone.PEAK).associateWith { z ->
      azm.filter { zoneOf(it.label) == z }.sumOf { ((it.endMs - it.startMs) / 60_000.0).coerceAtLeast(1.0) }
    }
    val zoneTotal = zoneMinutes.values.sum().coerceAtLeast(1.0)
    val zones = zoneMinutes.map { (z, m) -> ZoneRow(z, ZONE_NAMES.getValue(z), m.roundToInt(), (m / zoneTotal).toFloat()) }

    val rhrMap = daily(HealthTypes.RESTING_HR, day.minusDays(29), day)
    val rhrDays = (29 downTo 0).map { rhrMap[day.minusDays(it.toLong())] }
    val known = rhrDays.filterNotNull()
    val trend = if (known.size < 10) "Shaded band is your normal range. A trend needs a few more weeks of readings." else {
      val first = known.take(7).average()
      val lastWeek = known.takeLast(7).average()
      val d = lastWeek - first
      "Shaded band is your normal range. " + when {
        d <= -1 -> "Down ${Fmt.dec(-d)} bpm over the month — a sign of improving fitness or recovery."
        d >= 1 -> "Up ${Fmt.dec(d)} bpm over the month. Stress, illness, alcohol or short sleep can all raise it."
        else -> "Steady over the month."
      }
    }

    return HeartModel(
      dayLabel = dayLabel(day),
      canGoForward = day < today(),
      rhr = rhr.value?.let { Fmt.int(it) } ?: "—",
      rhrNote = differenceNote(rhr, "bpm"),
      rhrTone = rhr.deltaFromMean?.let { toneOf(-it, 1.0, -3.0) } ?: Tone.NEUTRAL,
      hrv = hrv.value?.let { Fmt.int(it) } ?: "—",
      hrvNote = differenceNote(hrv, "ms"),
      hrvTone = hrv.deltaFromMean?.let { toneOf(it, 3.0, -5.0) } ?: Tone.NEUTRAL,
      slots = slots,
      highlight = highlight,
      zones = zones,
      rhrDays = rhrDays,
      rhrRange = Stats.normalRange(known),
      rhrTrend = trend,
      peakLabel = peak?.let { "peak ${high(it).roundToInt()} · ${clock.time(it.startMs)}" },
      rhrDeltaShort = shortDelta(rhr),
      hrvDeltaShort = shortDelta(hrv),
      cardio = cardioFitness(day),
      zonesNote = age().let { a ->
        if (a == null) "Estimated from your heart rate, assuming age ${HeartZones.DEFAULT_AGE}. Set your age on the Data source screen."
        else "Estimated from your heart rate reserve (age $a, resting heart rate). Health Connect doesn't carry Google Health's own zone minutes."
      },
    )
  }

  private fun shortDelta(o: Overnight): String {
    val d = o.deltaFromMean ?: return if (o.value == null) "No reading" else "Building normal"
    return if (abs(d) < 0.5) "At your normal" else "${Fmt.signed(d)} vs normal"
  }

  private suspend fun cardioFitness(day: LocalDate): CardioFitness? {
    val vo2 = daily(HealthTypes.VO2_MAX, day.minusDays(90), day).entries.maxByOrNull { it.key }?.value ?: return null
    val a = age()
    val note = if (a == null) "VO₂ max estimate · set your age to compare"
    else {
      val years = ((BioAge.expectedVo2(a.toDouble()) - vo2) * 0.5).coerceIn(-4.0, 4.0)
      "VO₂ max estimate · ${Fmt.signed(years, 1)} yrs body age"
    }
    return CardioFitness(Fmt.int(vo2), note)
  }

  private fun differenceNote(o: Overnight, unit: String): String {
    val d = o.deltaFromMean ?: return if (o.value == null) "No reading" else "Building your normal"
    val r = abs(d).roundToInt()
    return when {
      r == 0 -> "At your normal"
      d < 0 -> "$r $unit below your normal"
      else -> "$r $unit above your normal"
    }
  }

  // ======================================================================
  // Activity
  // ======================================================================

  suspend fun activityModel(day: LocalDate): ActivityModel {
    val t = today()
    val dayStart = startMs(day)
    val dayEnd = startMs(day.plusDays(1))
    val byHour = hourlySteps(day)
    val total = byHour.sum()
    val lastHour = if (day == t) now().atZone(zone).toLocalTime().hour else 23
    val waking = (7..22).filter { it <= lastHour }
    val hours = (0..23).map { h -> HourBar(h, byHour[h], h in waking && byHour[h] < 250) }
    val lowCount = hours.count { it.low }
    val busiest = (0..23).maxByOrNull { byHour[it] }
    val hoursNote = if (total <= 0) "No steps recorded for this day yet." else buildString {
      if (busiest != null) append("Your busiest hour was ${clock.time(LocalTime.of(busiest, 0))} with ${Fmt.int(byHour[busiest])} steps.")
      if (waking.isNotEmpty()) append(" Grey hours had fewer than 250 steps — $lowCount of ${waking.size} waking hours.")
    }

    // Active Zone Minutes for the Monday–Sunday week containing [day]
    val monday = day.with(DayOfWeek.MONDAY)
    val weekPoints = dao.inRange(HealthTypes.ACTIVE_ZONE_MINUTES.id, startMs(monday), startMs(monday.plusDays(7)))
    val azmWeek = (0..6).map { i ->
      val d = monday.plusDays(i.toLong())
      val pts = weekPoints.filter { it.day == d.toString() }
      AzmDay(
        weekday.format(d),
        pts.filter { zoneOf(it.label) == Zone.FAT_BURN }.sumOf { it.value ?: 0.0 },
        pts.filter { zoneOf(it.label) == Zone.CARDIO || zoneOf(it.label) == Zone.PEAK }.sumOf { it.value ?: 0.0 },
        d == day,
      )
    }
    val weekTotal = azmWeek.sumOf { it.total }
    val sunday = monday.plusDays(6)
    val azmNote = "Cardio and peak minutes count double. " + when {
      weekTotal >= AZM_GOAL -> "You reached ${Fmt.int(AZM_GOAL)} this week."
      sunday < t -> "This week ended ${Fmt.int(AZM_GOAL - weekTotal)} short of ${Fmt.int(AZM_GOAL)}."
      else -> {
        val left = AZM_GOAL - weekTotal
        val daysLeft = ChronoUnit.DAYS.between(maxOf(t, monday), sunday) + 1
        "${Fmt.int(left)} more by Sunday — about ${Fmt.int(left / daysLeft)} a day."
      }
    }

    // stats
    val history = day.minusDays(30)
    val distance = dailySums(HealthTypes.DISTANCE, history, day)
    val active = dailySums(HealthTypes.ACTIVE_CALORIES, history, day)
    val totalKcal = dailySums(HealthTypes.TOTAL_CALORIES, day, day)[day]
    fun usual(m: Map<LocalDate, Double>) = Stats.mean(m.filterKeys { it < day }.values.filter { it > 0 })
    val km = distance[day]?.div(1_000_000)
    val activeKcal = active[day]
    val quiet = quietStretch(byHour, lastHour)
    val stats = listOf(
      Stat("Distance", km?.let { "${Fmt.dec(it)} km" } ?: "—", usual(distance)?.let { "Usual ${Fmt.dec(it / 1_000_000)} km" } ?: ""),
      Stat(
        "Calories burned",
        Fmt.int(totalKcal),
        if (totalKcal != null && activeKcal != null && totalKcal > activeKcal) "Incl. ${Fmt.int(totalKcal - activeKcal)} at rest" else "",
      ),
      Stat("Active calories", Fmt.int(activeKcal), usual(active)?.let { "Usual ${Fmt.int(it)}" } ?: ""),
      Stat(
        "Longest quiet stretch",
        quiet?.let { "${it.count()}h" } ?: "—",
        quiet?.let { "${clock.time(LocalTime.of(it.first, 0))} – ${clock.time(LocalTime.of((it.last + 1) % 24, 0))}" } ?: "No 2-hour stretch under ${QUIET_STEPS.toInt()} steps",
      ),
    )

    val workouts = dao.onDays(HealthTypes.EXERCISE.id, day.toString(), day.toString()).sortedByDescending { it.startMs }.map {
      // Average heart rate from the stored per-minute heart rate during the session, when still kept.
      val hr = it.value2 ?: Stats.mean(dao.inRange(HealthTypes.HEART_RATE.id, it.startMs, it.endMs).mapNotNull { m -> m.value })
      Workout(
        it.label ?: "Workout",
        "${weekday.format(day)} · ${clock.time(it.startMs)}",
        Fmt.duration(it.value),
        hr?.let { bpm -> "avg ${bpm.roundToInt()} bpm" } ?: "",
      )
    }

    val azmDays = dailySums(HealthTypes.ACTIVE_ZONE_MINUTES, day.minusDays(6), day)
    val azm7 = (6 downTo 0).map { i -> day.minusDays(i.toLong()).let { d -> weekday.format(d) to (azmDays[d] ?: 0.0).roundToInt() } }
    val azm7Total = azm7.sumOf { it.second }
    val azm7Note = "Cardio and peak minutes count double. " +
      if (azm7Total >= AZM_GOAL) "You reached ${Fmt.int(AZM_GOAL)} over these 7 days." else "${Fmt.int(AZM_GOAL - azm7Total)} more reaches ${Fmt.int(AZM_GOAL)}."

    return ActivityModel(
      azm7 = azm7,
      azm7Total = azm7Total,
      azm7Note = azm7Note,
      quietHours = lowCount,
      dayLabel = dayLabel(day),
      canGoForward = day < t,
      steps = Fmt.int(total),
      stepsFraction = frac(total, 0.0, STEP_GOAL),
      stepsPct = "${(total / STEP_GOAL * 100).roundToInt()}%",
      stepsGoalNote = if (total >= STEP_GOAL) "of your ${Fmt.int(STEP_GOAL)} goal\nGoal reached" else "of your ${Fmt.int(STEP_GOAL)} goal\n${Fmt.int(STEP_GOAL - total)} to go",
      hours = hours,
      hourLabels = listOf(0, 6, 12, 18, 23).map(clock::hour),
      hoursNote = hoursNote,
      azmWeek = azmWeek,
      azmTotal = "${Fmt.int(weekTotal)} / ${Fmt.int(AZM_GOAL)}",
      azmNote = azmNote,
      stats = stats,
      workouts = workouts,
    )
  }

  // ======================================================================
  // Trends
  // ======================================================================

  suspend fun trendsModel(range: TrendRange): TrendsModel {
    val t = today()
    val n = range.days.toLong()
    val nightEnd = t // overnight metrics for today are complete by morning
    val dayEnd = t.minusDays(1) // activity for today is still in progress
    val from = t.minusDays(2 * n + 1)

    val sleeps = mainSleeps(from, nightEnd)
    val asleep = sleeps.mapValues { it.value.value ?: 0.0 }.filterValues { it > 0 }
    val bedtimes = sleeps.mapValues { bedtimeMinutes(it.value) }
    val rhr = daily(HealthTypes.RESTING_HR, from, nightEnd)
    val hrv = daily(HealthTypes.HRV, from, nightEnd)
    val breathing = daily(HealthTypes.BREATHING, from, nightEnd)
    val steps = dailySums(HealthTypes.STEPS, from, dayEnd).filterValues { it >= MIN_WORN_STEPS }
    val azm = dailySums(HealthTypes.ACTIVE_ZONE_MINUTES, from, dayEnd)

    val bioRow = age()?.let {
      val now = bioEstimate(nightEnd)
      val before = bioEstimate(nightEnd.minusDays(n))
      if (now == null) null else {
        val spark = (5 downTo 0).map { k -> bioEstimate(nightEnd.minusDays(n * k / 5))?.age }
        val d = before?.let { now.age - it.age }
        TrendRow(
          "Biological age", Fmt.dec(now.age), d?.let { Fmt.signed(it, 1) } ?: "new",
          d?.let { if (abs(it) < 0.1) Tone.NEUTRAL else if (it < 0) Tone.GOOD else Tone.BAD } ?: Tone.NEUTRAL,
          spark,
        )
      }
    }
    val rows = listOfNotNull(bioRow) + listOf(
      trendRow("Time asleep", asleep, nightEnd, n, { "${Fmt.duration(it)} avg" }, { Fmt.signedDuration(it) }, threshold = 10.0, higherIsBetter = true),
      trendRow("Bedtime", bedtimes, nightEnd, n, { "${clock.time(NOON.plusMinutes(it.roundToInt().toLong()))} avg" }, { Fmt.signedDuration(it) }, threshold = 15.0, higherIsBetter = false),
      trendRow("Resting HR", rhr, nightEnd, n, { "${Fmt.dec(it)} bpm" }, { Fmt.signed(it, 1) }, threshold = 1.0, higherIsBetter = false),
      trendRow("HRV", hrv, nightEnd, n, { "${Fmt.int(it)} ms" }, { Fmt.signed(it) }, threshold = 2.0, higherIsBetter = true),
      trendRow("Zone minutes", azm, dayEnd, n, { "${Fmt.int(it)} / day" }, { Fmt.signed(it) }, threshold = 1.5, higherIsBetter = true, fillMissingWithZero = true),
      trendRow("Breathing rate", breathing, nightEnd, n, { "${Fmt.dec(it)} / min" }, { Fmt.signed(it, 1) }, threshold = 1.0, higherIsBetter = false),
    )
    return TrendsModel(range, stepsChart(steps, dayEnd, n), rows, xLabels(nightEnd, n), patterns())
  }

  /** Daily step bars for 7 and 30 days, weekly averages for 90, against the goal. */
  private fun stepsChart(series: Map<LocalDate, Double>, end: LocalDate, n: Long): StepsChart {
    val start = end.minusDays(n - 1)
    val days = (0 until n).map { start.plusDays(it) }
    val cur = days.mapNotNull { series[it] }
    val prev = (1..n).mapNotNull { series[start.minusDays(it)] }
    val weekly = n > 30
    val bars = if (!weekly) days.map { series[it] } else days.chunked(7).map { w -> Stats.mean(w.mapNotNull { series[it] }) }
    val avg = Stats.mean(cur)
    val p = Stats.mean(prev)
    val change = if (avg != null && p != null && p > 0) (avg - p) / p else null
    val date = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    val month = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
    val labels = when {
      n <= 7 -> days.map { weekday.format(it) }
      !weekly -> listOf(0L, 10L, 20L, n - 1).map { date.format(start.plusDays(it)) }
      else -> listOf(0L, 30L, 60L, n - 1).map { month.format(start.plusDays(it)) }
    }
    return StepsChart(
      title = when {
        n <= 7 -> "last 7 days"
        !weekly -> "last 30 days"
        else -> "weekly avg · 90 days"
      },
      average = Fmt.int(avg),
      averageValue = avg ?: 0.0,
      delta = change?.let { Fmt.pct(it * 100) } ?: "new",
      tone = change?.let { toneOf(it, 0.05, -0.05) } ?: Tone.NEUTRAL,
      goalNote = if (weekly) "weeks shown as averages" else "${cur.count { it >= STEP_GOAL }} of ${cur.size} days at goal",
      bars = bars,
      labels = labels,
    )
  }

  /** Four labels under each sparkline: the window's start, two points between, and "now". */
  private fun xLabels(end: LocalDate, n: Long): List<String> {
    val start = end.minusDays(n - 1)
    val f = when {
      n <= 7 -> weekday
      n <= 30 -> DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
      else -> DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
    }
    return listOf(0L, n / 3, 2 * n / 3).map { f.format(start.plusDays(it)) } + "now"
  }

  private fun trendRow(
    label: String,
    series: Map<LocalDate, Double>,
    end: LocalDate,
    n: Long,
    average: (Double) -> String,
    delta: (Double) -> String,
    threshold: Double,
    higherIsBetter: Boolean,
    relative: Boolean = false,
    /** Count missing days as 0 — but only from the first day the series has data, so "not synced yet" isn't "zero". */
    fillMissingWithZero: Boolean = false,
  ): TrendRow {
    fun window(fromExclusive: LocalDate, to: LocalDate): List<Double> {
      val days = generateSequence(fromExclusive.plusDays(1)) { it.plusDays(1) }.takeWhile { it <= to }
      val firstData = series.keys.minOrNull()
      return days.mapNotNull { d -> series[d] ?: if (fillMissingWithZero && firstData != null && d >= firstData) 0.0 else null }.toList()
    }
    val cur = window(end.minusDays(n), end)
    val prev = window(end.minusDays(2 * n), end.minusDays(n))
    // A point per day for 7 and 30 days, per week for 90.
    val buckets = if (n <= 30) n.toInt() else ((n + 6) / 7).toInt()
    val spark = (0 until buckets).map { b ->
      val lo = end.minusDays(n).plusDays(b * n / buckets)
      val hi = end.minusDays(n).plusDays((b + 1) * n / buckets)
      Stats.mean(window(lo, hi))
    }
    val c = Stats.mean(cur)
    val p = Stats.mean(prev)
    if (c == null) return TrendRow(label, "No data", "–", Tone.NEUTRAL, spark)
    if (p == null) return TrendRow(label, average(c), "new", Tone.NEUTRAL, spark)
    val diff = c - p
    val change = if (relative && p != 0.0) diff / p else diff
    val tone = when {
      abs(change) < threshold -> Tone.NEUTRAL
      (change > 0) == higherIsBetter -> Tone.GOOD
      else -> Tone.BAD
    }
    val deltaText = if (relative && p != 0.0) Fmt.pct(change * 100) else delta(diff)
    return TrendRow(label, average(c), deltaText, tone, spark)
  }

  /** Simple two-group comparisons over the last 180 days; each needs 5+ days per group. */
  private suspend fun patterns(): List<Pattern> {
    val t = today()
    val from = t.minusDays(180)
    val sleeps = mainSleeps(from, t)
    val steps = dailySums(HealthTypes.STEPS, from, t.minusDays(1)).filterValues { it >= MIN_WORN_STEPS }
    val rhr = daily(HealthTypes.RESTING_HR, from, t)
    val hrv = daily(HealthTypes.HRV, from, t)
    val azm = dailySums(HealthTypes.ACTIVE_ZONE_MINUTES, from, t.minusDays(1))
    val out = mutableListOf<Pattern>()

    // 1. Steps today → sleep tonight
    run {
      val pairs = steps.mapNotNull { (d, s) -> sleeps[d.plusDays(1)]?.value?.let { s to it } }
      val a = pairs.filter { it.first >= 8000 }.map { it.second }
      val b = pairs.filter { it.first < 8000 }.map { it.second }
      if (a.size >= 5 && b.size >= 5) {
        val d = a.average() - b.average()
        val title = when {
          d >= 10 -> "You sleep longer after days with 8,000+ steps."
          d <= -10 -> "You sleep less after days with 8,000+ steps."
          else -> "Your step count makes little difference to how long you sleep."
        }
        out += Pattern(title, "Days ≥ 8k steps", Fmt.duration(a.average()), "Days < 8k steps", Fmt.duration(b.average()), "Based on ${pairs.size} of your last 180 days")
      }
    }
    // 2. Bedtime → next-morning resting HR
    run {
      val pairs = sleeps.mapNotNull { (d, s) -> rhr[d]?.let { bedtimeMinutes(s) to it } }
      val early = pairs.filter { it.first < 11 * 60 + 30 }.map { it.second } // before 23:30
      val late = pairs.filter { it.first > 12 * 60 + 30 }.map { it.second } // after 00:30
      if (early.size >= 5 && late.size >= 5) {
        val d = late.average() - early.average()
        val title = when {
          d >= 1 -> "Late bedtimes raise your next-day resting heart rate."
          d <= -1 -> "Late bedtimes don't raise your resting heart rate."
          else -> "Bedtime makes little difference to your resting heart rate."
        }
        out += Pattern(title, "Bed before ${clock.time(LocalTime.of(23, 30))}", "${Fmt.dec(early.average())} bpm", "Bed after ${clock.time(LocalTime.of(0, 30))}", "${Fmt.dec(late.average())} bpm", "Based on ${early.size + late.size} nights")
      }
    }
    // 3. Rest vs active day → next-day HRV
    run {
      val pairs = azm.mapNotNull { (d, m) -> hrv[d.plusDays(1)]?.let { m to it } }
      val rest = pairs.filter { it.first < 5 }.map { it.second }
      val active = pairs.filter { it.first >= 30 }.map { it.second }
      if (rest.size >= 5 && active.size >= 5) {
        val d = rest.average() - active.average()
        val title = when {
          d >= 2 -> "Your HRV is highest after rest days."
          d <= -2 -> "Your HRV is higher after active days."
          else -> "Your HRV is about the same after rest and active days."
        }
        out += Pattern(title, "After rest day", "${Fmt.int(rest.average())} ms", "After 30+ zone min", "${Fmt.int(active.average())} ms", "Based on ${rest.size} rest days")
      }
    }
    return out
  }

  // ======================================================================
  // Home-screen widgets
  // ======================================================================

  suspend fun widgetModel(): WidgetModel {
    val t = today()
    val steps = dailySums(HealthTypes.STEPS, t, t)[t] ?: 0.0
    val distances = dailySums(HealthTypes.DISTANCE, t.minusDays(30), t)
    val km = distances[t]?.div(1_000_000)
    val usualKm = Stats.mean(distances.filterKeys { it < t }.values.filter { it > 0 })?.div(1_000_000)

    val sleeps = mainSleeps(t.minusDays(40), t)
    val last = sleeps[t] ?: sleeps[t.minusDays(1)]
    val usualSleep = last?.let { l -> Stats.median(sleeps.filterKeys { it < LocalDate.parse(l.day) }.values.mapNotNull { it.value }.takeLast(30)) }
    val stages = last?.let { SleepStages.fromStored(it.json) }.orEmpty()
    val stageTotal = stages.sumOf { it.minutes }.takeIf { it > 0 }
    val stageShares = if (stageTotal == null) emptyList() else listOf(Stage.DEEP, Stage.REM, Stage.LIGHT, Stage.AWAKE).map { s ->
      (stages.filter { it.stage == s }.sumOf { it.minutes } / stageTotal).toFloat()
    }
    val sleepDelta = if (last?.value != null && usualSleep != null) last.value - usualSleep else null

    val rhr = overnight(HealthTypes.RESTING_HR, t)
    val hrv = overnight(HealthTypes.HRV, t)
    val spo2 = overnight(HealthTypes.SPO2, t)
    val bio = bioAgeModel()

    val stepDays = dailySums(HealthTypes.STEPS, t.minusDays(6), t)
    val week = (6 downTo 0).map { t.minusDays(it.toLong()) }.map { d -> weekday.format(d).take(1) to stepDays[d] }
    val weekAvg = Stats.mean(week.mapNotNull { it.second }.filter { it >= MIN_WORN_STEPS })

    return WidgetModel(
      steps = Fmt.int(steps),
      stepsFraction = frac(steps, 0.0, STEP_GOAL),
      stepsPct = (steps / STEP_GOAL * 100).roundToInt(),
      onPace = paceProjection(steps)?.let(Fmt::int),
      distance = km?.let { "${Fmt.dec(it)} km" } ?: "—",
      distanceUnitless = km?.let { Fmt.dec(it) } ?: "—",
      distanceFraction = frac(km, 0.0, DISTANCE_GOAL_KM),
      distanceNote = buildString {
        append("${((km ?: 0.0) / DISTANCE_GOAL_KM * 100).roundToInt()}% of ${DISTANCE_GOAL_KM.toInt()} km")
        usualKm?.let { append(" · usual ${Fmt.dec(it)}") }
      },
      sleep = Fmt.duration(last?.value),
      sleepFraction = frac(last?.value, 0.0, SLEEP_NEED),
      sleepStages = stageShares,
      sleepNote = when {
        last == null -> "No sleep recorded"
        sleepDelta == null -> "Building your usual"
        kotlin.math.abs(sleepDelta) < 10 -> "About your usual"
        else -> "${Fmt.signedDuration(sleepDelta)} vs usual"
      },
      sleepNoteTone = sleepDelta?.let { toneOf(it, 10.0, -30.0) } ?: Tone.NEUTRAL,
      restingHr = rhr.value?.let { Fmt.int(it) } ?: "—",
      hrv = hrv.value?.let { Fmt.int(it) } ?: "—",
      spo2 = spo2.value?.let { "${Fmt.dec(it, 0)}%" } ?: "—",
      bodyAge = bio?.let { Fmt.dec(it.age) },
      bodyAgeNote = bio?.let { "${Fmt.dec(abs(it.delta))} yrs ${if (it.delta <= 0) "younger" else "older"}" },
      bodyAgeDelta = bio?.delta,
      calendarAge = bio?.calendarAge,
      week = week,
      weekAverage = Fmt.int(weekAvg),
    )
  }

  // ======================================================================
  // Biological age
  // ======================================================================

  /** Null when the person hasn't set their age or there's too little data (see [BioAge.estimate]). */
  suspend fun bioAgeModel(): BioAgeModel? {
    val calendar = age() ?: return null
    val t = today()
    val inputs = bioInputs(t) ?: return null
    val est = BioAge.estimate(inputs) ?: return null

    val factors = est.contributions.map { c ->
      val reading = when (c.factor) {
        BioAge.Factor.CARDIO -> {
          val v = inputs.vo2Max!!
          val expected = BioAge.expectedVo2(calendar.toDouble())
          "VO₂ max ${Fmt.int(v)} · " + when {
            v >= expected + 3 -> "good for your age"
            v <= expected - 3 -> "below average for your age"
            else -> "average for your age"
          }
        }
        BioAge.Factor.RESTING_HR -> "${Fmt.int(inputs.restingHr)} bpm average"
        BioAge.Factor.HRV -> "${Fmt.int(inputs.hrvMs)} ms average"
        BioAge.Factor.ACTIVITY -> "${Fmt.int(inputs.zoneMinutesPerWeek)} zone minutes a week"
        BioAge.Factor.SLEEP_REGULARITY -> "Bedtime varies by ±${Fmt.int(inputs.bedtimeSdMinutes)} min"
        BioAge.Factor.BMI -> "${Fmt.dec(inputs.bmi)} from height and weight"
      }
      val tab = when (c.factor) {
        BioAge.Factor.CARDIO, BioAge.Factor.RESTING_HR, BioAge.Factor.HRV -> Tab.HEART
        BioAge.Factor.ACTIVITY -> Tab.ACTIVITY
        BioAge.Factor.SLEEP_REGULARITY -> Tab.SLEEP
        BioAge.Factor.BMI -> null
      }
      BioFactorRow(c.factor.title, reading, c.years, tab)
    }

    val month = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
    val trend = (5 downTo 0).mapNotNull { k ->
      val end = if (k == 0) t else t.minusMonths(k.toLong()).let { it.withDayOfMonth(it.lengthOfMonth()) }
      bioEstimate(end)?.let { month.format(end) to it.age }
    }
    val first = trend.firstOrNull()?.takeIf { trend.size >= 2 }
    val lever = est.biggestLever?.let { "Biggest lever: ${it.factor.lever}. That could take about ${Fmt.dec(it.years)} years off." }
      ?: "Every factor is helping right now — keep doing what you're doing."
    val daysUsed = dao.onDays(HealthTypes.RESTING_HR.id, t.minusDays(89).toString(), t.toString()).map { it.day }.distinct().size

    return BioAgeModel(
      age = est.age,
      calendarAge = calendar,
      delta = est.age - calendar,
      factors = factors,
      trend = trend,
      sinceLabel = first?.let { "Since ${it.first}" },
      sinceDelta = first?.let { est.age - it.second },
      daysUsed = daysUsed,
      lever = lever,
    )
  }

  private suspend fun bioEstimate(end: LocalDate): BioAge.Estimate? = bioInputs(end)?.let(BioAge::estimate)

  /** Readings for the estimate as of [end]: 30-day averages, VO₂ max over 90 days, latest weight and height. */
  private suspend fun bioInputs(end: LocalDate): BioAge.Inputs? {
    val calendar = age() ?: return null
    val from30 = end.minusDays(29)
    fun avgIfEnough(m: Map<LocalDate, Double>, min: Int = 7) = m.values.takeIf { it.size >= min }?.average()
    val vo2 = daily(HealthTypes.VO2_MAX, end.minusDays(89), end).values.takeIf { it.isNotEmpty() }?.average()
    val rhr = avgIfEnough(daily(HealthTypes.RESTING_HR, from30, end))
    val hrv = avgIfEnough(daily(HealthTypes.HRV, from30, end))
    val azm = dailySums(HealthTypes.ACTIVE_ZONE_MINUTES, from30, end)
    val azmWeek = azm.keys.minOrNull()?.let { first ->
      val days = ChronoUnit.DAYS.between(first, end) + 1
      if (days >= 7) azm.values.sum() / days * 7 else null
    }
    val beds = mainSleeps(from30, end).values.map(::bedtimeMinutes)
    val bedSd = if (beds.size >= 7) beds.average().let { m -> kotlin.math.sqrt(beds.sumOf { (it - m) * (it - m) } / beds.size) } else null
    val weight = dao.onDays(HealthTypes.WEIGHT.id, end.minusDays(364).toString(), end.toString()).lastOrNull()?.value
    val height = dao.onDays(HealthTypes.HEIGHT.id, end.minusDays(365L * 3).toString(), end.toString()).lastOrNull()?.value
    val bmi = if (weight != null && height != null && height > 0.5) weight / (height * height) else null
    return BioAge.Inputs(calendar, vo2, rhr, hrv, azmWeek, bedSd, bmi)
  }

  // ======================================================================
  // shared helpers
  // ======================================================================

  /** A daily overnight reading with its personal normal from the 30 days before it. */
  data class Overnight(val value: Double?, val range: Range?, val mean: Double?) {
    val deltaFromMean: Double? get() = if (value != null && mean != null) value - mean else null
  }

  /** Latest reading for [day] (or, unless [exact], the day before), against the 30 days before it. */
  private suspend fun overnight(type: HealthType, day: LocalDate, exact: Boolean = false): Overnight {
    val map = daily(type, day.minusDays(31), day)
    val readingDay = if (map.containsKey(day) || exact) day else day.minusDays(1)
    val value = map[readingDay]
    val history = map.filterKeys { it < readingDay }.values.toList()
    return Overnight(value, Stats.normalRange(history), Stats.mean(history).takeIf { history.size >= 7 })
  }

  private fun vitalCard(label: String, o: Overnight, unit: String, digits: Int, min: Double, max: Double, hint: String, tab: Tab) = MetricCard(
    label,
    o.value?.let { if (digits == 0) Fmt.int(it) else Fmt.dec(it, digits) } ?: "—",
    unit,
    frac(o.value, min, max),
    Accent.RECOVERY,
    when {
      o.value == null -> "No reading yet"
      o.range == null -> "Building your normal · $hint"
      else -> "Normal ${fmtRange(o.range, digits)} · $hint"
    },
    tab,
  )

  private fun vital(label: String, o: Overnight, concern: Concern, format: (Double) -> String): Vital {
    val v = o.value ?: return Vital(label, "—", "No reading", Tone.NEUTRAL)
    val r = o.range ?: return Vital(label, format(v), "Building your normal", Tone.NEUTRAL)
    val tone = if (o.concerning(concern)) Tone.BAD else if (v in r) Tone.GOOD else Tone.NEUTRAL
    val status = when {
      v > r.high -> "Above your normal"
      v < r.low -> "Below your normal"
      else -> "Normal"
    }
    return Vital(label, format(v), status, tone)
  }

  private fun fmtRange(r: Range, digits: Int): String {
    val lo = if (digits == 0) r.low.roundToInt().toString() else Fmt.dec(r.low, digits)
    val hi = if (digits == 0) r.high.roundToInt().toString() else Fmt.dec(r.high, digits)
    return if (lo == hi) lo else "$lo–$hi"
  }

  /** One value per day: the average of that day's readings (Health Connect may hold several, e.g. SpO2 samples). */
  private suspend fun daily(type: HealthType, from: LocalDate, to: LocalDate): Map<LocalDate, Double> =
    dao.onDays(type.id, from.toString(), to.toString())
      .filter { it.value != null }
      .groupBy { LocalDate.parse(it.day) }
      .mapValues { (_, l) -> l.sumOf { it.value!! } / l.size }

  private suspend fun dailySums(type: HealthType, from: LocalDate, to: LocalDate): Map<LocalDate, Double> =
    dao.dailySums(type.id, from.toString(), to.toString()).associate { LocalDate.parse(it.day) to it.total }

  /** One sleep per wake-up day: the longest session that isn't a daytime nap. */
  private suspend fun mainSleeps(from: LocalDate, to: LocalDate): Map<LocalDate, DataPointEntity> =
    dao.onDays(HealthTypes.SLEEP.id, from.toString(), to.toString())
      .filter { it.value != null && it.label != "nap" }
      .groupBy { LocalDate.parse(it.day) }
      .mapValues { (_, l) -> l.maxBy { it.value!! } }
      .toSortedMap()

  /** Minutes after noon, so 23:30 → 690 and 00:30 → 750 average sensibly. */
  private fun bedtimeMinutes(e: DataPointEntity): Double {
    val m = minuteOfDay(e.startMs)
    return ((m - 720 + 1440) % 1440).toDouble()
  }

  private fun minuteOfDay(ms: Long): Int = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().let { it.hour * 60 + it.minute }


  private fun startMs(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()

  private fun dayLabel(day: LocalDate): String {
    val t = today()
    return when (day) {
      t -> "Today · ${dayShort.format(day)}"
      t.minusDays(1) -> "Yesterday · ${dayShort.format(day)}"
      else -> dayShort.format(day)
    }
  }

  /** Projected end-of-day steps, assuming a 07:00–23:00 waking day. Null before 09:00. */
  private fun paceProjection(steps: Double): Double? {
    val time = now().atZone(zone).toLocalTime()
    val elapsed = (time.hour * 60 + time.minute - 7 * 60) / (16.0 * 60)
    if (elapsed < 0.125 || elapsed >= 1) return null
    return (steps / elapsed / 100).roundToInt() * 100.0
  }

  private fun zoneOf(label: String?): Zone = when (label) {
    "FAT_BURN" -> Zone.FAT_BURN
    "CARDIO" -> Zone.CARDIO
    "PEAK" -> Zone.PEAK
    else -> Zone.BELOW
  }

  companion object {
    const val STEP_GOAL = 10_000.0
    const val AZM_GOAL = 150.0
    /** Days with fewer steps are treated as "device not worn" in averages. */
    const val MIN_WORN_STEPS = 500.0
    /** An hour with fewer steps than this counts as quiet (mostly sitting). */
    const val QUIET_STEPS = 250.0
    /** Daily distance goal used by the Distance widget. */
    const val DISTANCE_GOAL_KM = 5.0
    /** Sleep need used for the Sleep ring and target bedtime. */
    const val SLEEP_NEED = 480.0
    private val NOON: LocalTime = LocalTime.NOON

    /** Typical adult share of sleep per stage, as shown in Fitbit / Google Health. */
    val TYPICAL = mapOf(
      Stage.DEEP to Range(13.0, 23.0),
      Stage.REM to Range(20.0, 31.0),
      Stage.LIGHT to Range(40.0, 60.0),
      Stage.AWAKE to Range(0.0, 10.0),
    )
    val STAGE_NAMES = mapOf(Stage.DEEP to "Deep", Stage.REM to "REM", Stage.LIGHT to "Light", Stage.AWAKE to "Awake")
    val ZONE_NAMES = mapOf(Zone.BELOW to "Below zones", Zone.FAT_BURN to "Fat burn", Zone.CARDIO to "Cardio", Zone.PEAK to "Peak")

    fun toneOf(v: Double, good: Double, bad: Double): Tone = when {
      v >= good -> Tone.GOOD
      v <= bad -> Tone.BAD
      else -> Tone.NEUTRAL
    }

    /**
     * Recovery ring, 0–100: 65 is an ordinary day. Each available signal adds
     * up to ±30: an hour more sleep than usual, 5 bpm lower resting HR, or
     * 10 ms higher HRV each count as the full amount.
     */
    fun recoveryScore(sleepDelta: Double?, rhrDelta: Double?, hrvDelta: Double?): Int? {
      val parts = listOfNotNull(
        sleepDelta?.let { (it / 60).coerceIn(-1.0, 1.0) },
        rhrDelta?.let { (-it / 5).coerceIn(-1.0, 1.0) },
        hrvDelta?.let { (it / 10).coerceIn(-1.0, 1.0) },
      )
      if (parts.isEmpty()) return null
      return (65 + 30 * parts.average()).roundToInt().coerceIn(0, 100)
    }

    fun frac(v: Double?, min: Double, max: Double): Float =
      if (v == null) 0f else ((v - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()
  }
}
