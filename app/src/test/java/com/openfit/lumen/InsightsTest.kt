package com.openfit.lumen

import com.openfit.lumen.data.HealthTypes
import com.openfit.lumen.data.db.DataPointEntity
import com.openfit.lumen.data.db.DayValue
import com.openfit.lumen.data.db.HealthDao
import com.openfit.lumen.data.db.SyncStateEntity
import com.openfit.lumen.data.db.TypeCount
import com.openfit.lumen.insights.Clock24
import com.openfit.lumen.insights.Insights
import com.openfit.lumen.insights.Tone
import com.openfit.lumen.insights.TrendRange
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory stand-in for the Room DAO with the same query semantics. */
class FakeDao : HealthDao {
  val points = linkedMapOf<String, DataPointEntity>()
  override suspend fun upsert(points: List<DataPointEntity>) = points.forEach { this.points[it.name] = it }
  override suspend fun inRange(type: String, fromMs: Long, toMs: Long) = points.values.filter { it.type == type && it.startMs >= fromMs && it.startMs < toMs }.sortedBy { it.startMs }
  override suspend fun onDays(type: String, fromDay: String, toDay: String) = points.values.filter { it.type == type && it.day in fromDay..toDay }.sortedBy { it.startMs }
  override suspend fun dailySums(type: String, fromDay: String, toDay: String) =
    onDays(type, fromDay, toDay).groupBy { it.day }.map { (d, l) -> DayValue(d, l.sumOf { it.value ?: 0.0 }) }.sortedBy { it.day }
  override fun observeCounts(): Flow<List<TypeCount>> = flowOf(emptyList())
  override suspend fun pruneBefore(type: String, beforeMs: Long) { points.values.removeIf { it.type == type && it.startMs < beforeMs } }
  override suspend fun deleteRange(type: String, fromMs: Long, toMs: Long) { points.values.removeIf { it.type == type && it.startMs >= fromMs && it.startMs < toMs } }
  override suspend fun replaceRange(type: String, fromMs: Long, toMs: Long, rows: List<DataPointEntity>) { deleteRange(type, fromMs, toMs); upsert(rows) }
  override suspend fun latestEnd(types: List<String>, beforeMs: Long) = points.values.filter { it.type in types && it.endMs <= beforeMs }.maxOfOrNull { it.endMs }
  override suspend fun deleteAllPoints() = points.clear()
  override suspend fun upsertState(state: SyncStateEntity) {}
  override suspend fun state(type: String): SyncStateEntity? = null
  override fun observeStates(): Flow<List<SyncStateEntity>> = flowOf(emptyList())
  override suspend fun deleteAllStates() {}
}

class InsightsTest {
  private val zone = ZoneId.of("Europe/London")
  private val today = LocalDate.of(2026, 10, 9)
  private val now = LocalDateTime.of(2026, 10, 9, 14, 0).atZone(zone).toInstant()
  private val dao = FakeDao()
  private val insights = Insights(dao, Clock24(true, zone)) { now }

  private fun ms(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

  private fun daily(type: String, d: LocalDate, v: Double) =
    DataPointEntity("$type/$d", type, ms(d, 0), ms(d.plusDays(1), 0), d.toString(), v, null, null, "{}")

  /** A main sleep from 23:00 the day before [wake] lasting [minutes]. */
  private fun sleep(wake: LocalDate, minutes: Int, bedHour: Int = 23): DataPointEntity {
    val start = ms(wake.minusDays(1), bedHour)
    val end = start + minutes * 60_000L
    return DataPointEntity("sleep/$wake", "sleep", start, end, wake.toString(), minutes.toDouble(), 10.0, "sleep", "{}")
  }

  private fun steps(d: LocalDate, hour: Int, count: Double) =
    DataPointEntity("steps/$d/$hour", "steps", ms(d, hour), ms(d, hour) + 60_000, d.toString(), count, null, null, "{}")

  private fun seedBaseline() = runBlocking {
    val pts = mutableListOf<DataPointEntity>()
    for (i in 1..35) {
      val d = today.minusDays(i.toLong())
      pts += daily(HealthTypes.RESTING_HR.id, d, 60.0 + (i % 3))
      pts += daily(HealthTypes.HRV.id, d, 40.0 + (i % 4))
      pts += daily(HealthTypes.BREATHING.id, d, 14.5 + (i % 2) * 0.2)
      pts += sleep(d, 390 + (i % 5) * 5)
      pts += steps(d, 9, 3000.0)
      pts += steps(d, 18, 4000.0 + (i % 2) * 2000)
    }
    dao.upsert(pts)
  }

  @Test fun recoveredWellWhenSleepLongerAndRestingHrLower() = runBlocking {
    seedBaseline()
    dao.upsert(listOf(sleep(today, 450), daily(HealthTypes.RESTING_HR.id, today, 57.0), daily(HealthTypes.HRV.id, today, 48.0)))
    val m = insights.todayModel()
    assertEquals("You recovered well.", m.recovery.headline)
    assertEquals(3, m.recovery.signals.size)
    assertTrue(m.recovery.signals.all { it.tone == Tone.GOOD })
    assertTrue(m.recovery.body, m.recovery.body.startsWith("You slept"))
    assertEquals("7h 30m", m.cards.first { it.label == "Sleep" }.value)
  }

  @Test fun stillRecoveringWhenShortSleepAndHighRestingHr() = runBlocking {
    seedBaseline()
    dao.upsert(listOf(sleep(today, 300), daily(HealthTypes.RESTING_HR.id, today, 66.0), daily(HealthTypes.HRV.id, today, 33.0)))
    assertEquals("Your body is still recovering.", insights.todayModel().recovery.headline)
  }

  @Test fun noDataGivesFriendlyEmptyState() = runBlocking {
    val m = insights.todayModel()
    assertEquals("Not enough data yet", m.recovery.headline)
    assertEquals("—", m.cards.first { it.label == "Resting HR" }.value)
  }

  @Test fun activityBucketsStepsByHour() = runBlocking {
    seedBaseline()
    val y = today.minusDays(1)
    val m = insights.activityModel(y)
    assertEquals(3000.0, m.hours[9].steps, 0.0)
    assertTrue(m.hours[10].low) // waking hour with no steps
    assertTrue(m.canGoForward)
  }

  @Test fun trendsCompareWithPreviousPeriod() = runBlocking {
    seedBaseline()
    val t = insights.trendsModel(TrendRange.D7)
    val rows = t.rows.associateBy { it.label }
    assertNotNull(rows["Resting HR"])
    assertEquals(7, t.steps.bars.size)
    assertEquals(7, rows.getValue("Resting HR").spark.size) // a point per day
    assertEquals(4, t.xLabels.size)
    assertEquals("now", t.xLabels.last())
    assertEquals(13, insights.trendsModel(TrendRange.D90).rows.first { it.label == "HRV" }.spark.size) // a point per week
    assertTrue(rows.getValue("Bedtime").average.contains("23:00"))
  }

  @Test fun sleepScreenUsesLatestNight() = runBlocking {
    seedBaseline()
    dao.upsert(listOf(sleep(today, 431)))
    val s = insights.sleepModel()!!
    assertEquals("7h 11m", s.asleep)
    assertEquals(7, s.nights.size)
    assertTrue(s.nights.last().latest)
  }


  @Test fun dailyVitalsAverageSeveralReadings() = runBlocking {
    seedBaseline()
    // Two SpO2 samples last night average to 95.
    dao.upsert(
      listOf(
        DataPointEntity("spo2/a", HealthTypes.SPO2.id, ms(today, 2), ms(today, 2), today.toString(), 94.0, null, null, "{}"),
        DataPointEntity("spo2/b", HealthTypes.SPO2.id, ms(today, 4), ms(today, 4), today.toString(), 96.0, null, null, "{}"),
      ),
    )
    assertEquals("95", insights.todayModel().cards.first { it.label == "Blood oxygen" }.value)
  }

  @Test fun multiHourStepRecordIsSplitAcrossHours() = runBlocking {
    val y = today.minusDays(1)
    // 600 steps recorded 09:30–11:30 → 150 / 300 / 150.
    dao.upsert(listOf(DataPointEntity("steps/x", "steps", ms(y, 9, 30), ms(y, 11, 30), y.toString(), 600.0, null, null, "{}")))
    val h = insights.activityModel(y).hours
    assertEquals(150.0, h[9].steps, 0.01)
    assertEquals(300.0, h[10].steps, 0.01)
    assertEquals(150.0, h[11].steps, 0.01)
  }

  @Test fun quietStretchFromHourlySteps() = runBlocking {
    seedBaseline() // steps only at 09:00 and 18:00, so 10:00–18:00 is quiet
    val note = insights.todayModel().notes.first { it.text.startsWith("Yesterday was quietest") }
    assertTrue(note.text, note.text.contains("10:00 to 18:00"))
    assertEquals("8h", insights.activityModel(today.minusDays(1)).stats.first { it.label == "Longest quiet stretch" }.value)
  }

  @Test fun napsDoNotCountAsTheNight() = runBlocking {
    seedBaseline()
    dao.upsert(listOf(sleep(today, 420), DataPointEntity("sleep/nap", "sleep", ms(today, 13), ms(today, 13, 40), today.toString(), 40.0, 0.0, "nap", "{}")))
    assertEquals("7h 00m", insights.sleepModel()!!.asleep)
  }

  @Test fun zoneMinuteTrendIgnoresDaysBeforeDataExists() = runBlocking {
    // Zone minutes only for the last 5 days: the previous 7-day window has no data, so no comparison.
    dao.upsert((1..5).map { i ->
      val d = today.minusDays(i.toLong())
      DataPointEntity("azm/$d", HealthTypes.ACTIVE_ZONE_MINUTES.id, ms(d, 18), ms(d, 18, 1), d.toString(), 10.0, null, "FAT_BURN", "{}")
    })
    val row = insights.trendsModel(TrendRange.D7).rows.first { it.label == "Zone minutes" }
    assertEquals("new", row.delta)
  }

  @Test fun todayRingsAndMonitor() = runBlocking {
    seedBaseline()
    dao.upsert(listOf(sleep(today, 432), daily(HealthTypes.RESTING_HR.id, today, 57.0), daily(HealthTypes.HRV.id, today, 48.0)))
    val m = insights.todayModel()
    assertEquals(90, m.sleepScore) // 7h12m of an 8h need
    assertTrue(m.recoveryScore!! > 65)
    assertEquals(listOf("RR", "RHR", "HRV", "SpO₂", "Temp"), m.monitor.map { it.key })
    assertTrue(m.coaching, m.coaching.contains("Aim for bed by"))
  }

  @Test fun bioAgeNeedsCalendarAge() = runBlocking {
    seedBaseline()
    assertEquals(null, insights.bioAgeModel())
    val withAge = Insights(dao, Clock24(true, zone), age = { 34 }) { now }
    val bio = withAge.bioAgeModel()!!
    assertEquals(34, bio.calendarAge)
    assertTrue(bio.factors.any { it.name == "Resting heart rate" })
  }

  @Test fun homeShowsHowCurrentGoogleHealthDataIs() = runBlocking {
    dao.upsert(listOf(steps(today, 13, 500.0))) // a reading from 13:00 to 13:01
    assertEquals("13:01", insights.todayModel().dataUpTo)
    assertEquals(null, Insights(FakeDao(), Clock24(true, zone)) { now }.todayModel().dataUpTo)
  }
}
