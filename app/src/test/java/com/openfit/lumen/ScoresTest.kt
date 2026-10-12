package com.openfit.lumen

import com.openfit.lumen.insights.BioAge
import com.openfit.lumen.insights.Insights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoresTest {
  @Test fun recoveryScoreCentresOnAnOrdinaryDay() {
    assertEquals(65, Insights.recoveryScore(0.0, 0.0, 0.0))
    assertEquals(95, Insights.recoveryScore(60.0, -5.0, 10.0)) // all at their best
    assertEquals(35, Insights.recoveryScore(-60.0, 5.0, -10.0)) // all at their worst
    assertNull(Insights.recoveryScore(null, null, null))
    // Extremes are capped, so one huge value can't run away.
    assertEquals(95, Insights.recoveryScore(600.0, null, null))
  }

  @Test fun bioAgeNeedsTwoFactors() {
    assertNull(BioAge.estimate(BioAge.Inputs(34, restingHr = 58.0)))
  }

  @Test fun fitPersonComesOutYounger() {
    val e = BioAge.estimate(BioAge.Inputs(34, vo2Max = 48.0, restingHr = 56.0, hrvMs = 50.0, zoneMinutesPerWeek = 180.0, bedtimeSdMinutes = 20.0, bmi = 23.0))!!
    assertTrue("expected younger, got ${e.age}", e.age < 34)
    assertNull(e.biggestLever) // nothing is adding years
  }

  @Test fun eachFactorIsCapped() {
    val e = BioAge.estimate(BioAge.Inputs(40, vo2Max = 10.0, restingHr = 120.0))!!
    // VO2 capped at +4, resting HR at +2.
    assertEquals(46.0, e.age, 1e-9)
    assertEquals(BioAge.Factor.CARDIO, e.biggestLever!!.factor)
  }

  @Test fun irregularSleepIsTheLeverWhenItAddsMost() {
    val e = BioAge.estimate(BioAge.Inputs(30, restingHr = 60.0, bedtimeSdMinutes = 90.0))!!
    assertEquals(BioAge.Factor.SLEEP_REGULARITY, e.biggestLever!!.factor)
    assertEquals(1.2, e.contributions.first { it.factor == BioAge.Factor.SLEEP_REGULARITY }.years, 1e-9)
  }
}
