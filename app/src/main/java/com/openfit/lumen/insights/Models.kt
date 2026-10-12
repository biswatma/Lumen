package com.openfit.lumen.insights

import com.openfit.lumen.data.SleepStages.Stage

/** Screen models. Every string here is ready to show; the UI does no math. */

enum class Tone { GOOD, BAD, NEUTRAL }

enum class Accent { RECOVERY, ACTIVITY }

enum class Tab { TODAY, SLEEP, HEART, ACTIVITY, TRENDS }

enum class NoteKind { OK, NUDGE, INFO }

enum class Zone { BELOW, FAT_BURN, CARDIO, PEAK }

// ---- Today ----

data class Signal(val label: String, val delta: String, val tone: Tone)

data class Recovery(val headline: String, val body: String, val signals: List<Signal>)

data class MetricCard(
  val label: String,
  val value: String,
  val unit: String,
  val fraction: Float,
  val accent: Accent,
  val note: String,
  val tab: Tab,
)

data class PlainNote(val kind: NoteKind, val text: String)

/** A Health Monitor tile: one overnight vital against the person's own normal. */
data class MonitorItem(val key: String, val value: String, val unit: String, val tone: Tone, val tab: Tab)

data class TodayModel(
  val dateLabel: String,
  val recovery: Recovery,
  val cards: List<MetricCard>,
  val notes: List<PlainNote>,
  /** Ring scores, 0–100. Recovery and Sleep are null until there's enough data. */
  val moveScore: Int,
  val recoveryScore: Int?,
  val sleepScore: Int?,
  val coaching: String,
  val steps: String,
  val stepsFraction: Float,
  val distance: String,
  val activeKcal: String,
  /** Projected end-of-day steps, or null early in the day / once the day is mostly over. */
  val onPace: String?,
  val monitor: List<MonitorItem>,
  /** "All metrics are within your normal range." or what's outside it; null without enough data. */
  val monitorNote: PlainNote?,
  val bio: BioAgeModel?,
)

// ---- Sleep ----

data class HypnoSpan(val stage: Stage, val start: Float, val width: Float)

data class StageRow(val stage: Stage, val name: String, val duration: String, val pct: Int, val typical: Range, val note: String)

data class NightBar(val dayLabel: String, val valueLabel: String, val hours: Double, val short: Boolean, val latest: Boolean)

data class Vital(val label: String, val value: String, val status: String, val tone: Tone)

data class SleepModel(
  val dateLabel: String,
  val asleep: String,
  val window: String,
  val awake: String,
  val summary: String,
  val spans: List<HypnoSpan>,
  val axis: List<String>,
  val stages: List<StageRow>,
  val nights: List<NightBar>,
  val avgHours: Double?,
  val nightsNote: String,
  val vitals: List<Vital>,
  /** Time asleep as a share of an 8-hour need, 0–100. */
  val score: Int,
  val timeInBed: String,
  val insightTitle: String,
  val insightBody: String,
  val windDown: String?,
  val targetBedtime: String?,
)

// ---- Heart ----

data class HrSlot(val index: Int, val low: Double, val high: Double, val zone: Zone)

data class ZoneRow(val zone: Zone, val name: String, val minutes: Int, val fraction: Float)

data class HeartModel(
  val dayLabel: String,
  val canGoForward: Boolean,
  val rhr: String,
  val rhrNote: String,
  val rhrTone: Tone,
  val hrv: String,
  val hrvNote: String,
  val hrvTone: Tone,
  val slots: List<HrSlot>,
  val highlight: String,
  val zones: List<ZoneRow>,
  val rhrDays: List<Double?>,
  val rhrRange: Range?,
  val rhrTrend: String,
  val zonesNote: String,
  val peakLabel: String?,
  val rhrDeltaShort: String,
  val hrvDeltaShort: String,
  val cardio: CardioFitness?,
)

/** Latest VO₂ max and what it does to the biological age estimate. */
data class CardioFitness(val vo2: String, val note: String)

// ---- Activity ----

data class HourBar(val hour: Int, val steps: Double, val low: Boolean)

data class AzmDay(val label: String, val fatBurn: Double, val cardio: Double, val selected: Boolean) {
  val total: Double get() = fatBurn + cardio
}

data class Stat(val label: String, val value: String, val note: String)

data class Workout(val name: String, val whenText: String, val duration: String, val heartRate: String)

data class ActivityModel(
  val dayLabel: String,
  val canGoForward: Boolean,
  val steps: String,
  val stepsFraction: Float,
  val stepsPct: String,
  val stepsGoalNote: String,
  val hours: List<HourBar>,
  val hourLabels: List<String>,
  val hoursNote: String,
  val azmWeek: List<AzmDay>,
  val azmTotal: String,
  val azmNote: String,
  val stats: List<Stat>,
  val workouts: List<Workout>,
  /** Zone minutes for the 7 days ending on the shown day. */
  val azm7: List<Pair<String, Int>>,
  val azm7Total: Int,
  val azm7Note: String,
  val quietHours: Int,
)

// ---- Trends ----

enum class TrendRange(val days: Int, val label: String) { D7(7, "7 days"), D30(30, "30 days"), D90(90, "90 days") }

data class TrendRow(val label: String, val average: String, val delta: String, val tone: Tone, val spark: List<Double?>)

data class Pattern(val title: String, val aLabel: String, val aValue: String, val bLabel: String, val bValue: String, val basis: String)

/** Steps per day (7/30 days) or per week (90 days), with the goal and average lines. */
data class StepsChart(
  val title: String,
  val average: String,
  val averageValue: Double,
  val delta: String,
  val tone: Tone,
  val goalNote: String,
  val bars: List<Double?>,
  val labels: List<String>,
)

data class TrendsModel(
  val range: TrendRange,
  val steps: StepsChart,
  val rows: List<TrendRow>,
  /** Four evenly spaced labels under each sparkline, ending in "now". */
  val xLabels: List<String>,
  val patterns: List<Pattern>,
)

// ---- Biological age ----

data class BioFactorRow(val name: String, val reading: String, val years: Double, val tab: Tab?)

data class BioAgeModel(
  val age: Double,
  val calendarAge: Int,
  /** Biological minus calendar age; negative = younger. */
  val delta: Double,
  val factors: List<BioFactorRow>,
  /** Monthly estimates, oldest first, for months with enough data. */
  val trend: List<Pair<String, Double>>,
  val sinceLabel: String?,
  val sinceDelta: Double?,
  val daysUsed: Int,
  val lever: String,
)
