package com.openfit.lumen

import com.openfit.lumen.data.SleepStages
import com.openfit.lumen.data.hc.HeartZones
import com.openfit.lumen.data.hc.HeartZones.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartZonesTest {
  // Age 40 → max 180; resting 60 → reserve 120.
  @Test fun zonesFollowHeartRateReserve() {
    assertNull(HeartZones.zone(107.0, 60.0, 40)) // 39 %
    assertEquals(Zone.FAT_BURN, HeartZones.zone(108.0, 60.0, 40)) // 40 %
    assertEquals(Zone.CARDIO, HeartZones.zone(132.0, 60.0, 40)) // 60 %
    assertEquals(Zone.PEAK, HeartZones.zone(162.0, 60.0, 40)) // 85 %
  }

  @Test fun cardioAndPeakCountDouble() {
    assertEquals(1, Zone.FAT_BURN.credit)
    assertEquals(2, Zone.CARDIO.credit)
    assertEquals(2, Zone.PEAK.credit)
  }

  @Test fun samplesAreBucketedIntoMinutes() {
    val m = HeartZones.minutes(listOf(0L to 60.0, 30_000L to 70.0, 60_000L to 90.0))
    assertEquals(2, m.size)
    assertEquals(65.0, m[0].avg, 0.0)
    assertEquals(70.0, m[0].max, 0.0)
    assertEquals(60_000L, m[1].startMs)
  }

  @Test fun storedSleepStagesRoundTrip() {
    val raw = """{"stages":[{"stage":"DEEP","startMs":60000,"endMs":180000},{"stage":"LIGHT","startMs":0,"endMs":60000},{"stage":"BOGUS","startMs":1,"endMs":2}]}"""
    val s = SleepStages.fromStored(raw)
    assertEquals(listOf(SleepStages.Stage.LIGHT, SleepStages.Stage.DEEP), s.map { it.stage })
    assertEquals(2.0, s[1].minutes, 0.0)
  }
}
