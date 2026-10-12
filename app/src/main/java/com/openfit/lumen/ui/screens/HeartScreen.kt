package com.openfit.lumen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openfit.lumen.insights.HeartModel
import com.openfit.lumen.insights.Tone
import com.openfit.lumen.ui.components.DayPicker
import com.openfit.lumen.ui.components.DetailHeader
import com.openfit.lumen.ui.components.GlowRing
import com.openfit.lumen.ui.components.HeartRangeChart
import com.openfit.lumen.ui.components.LumenIcons
import com.openfit.lumen.ui.components.PCard
import com.openfit.lumen.ui.components.PulseScreen
import com.openfit.lumen.ui.components.RestingHrBars
import com.openfit.lumen.ui.components.StatusDot
import com.openfit.lumen.ui.components.ZoneStackBar
import com.openfit.lumen.ui.components.countUp
import com.openfit.lumen.ui.components.toneText
import com.openfit.lumen.ui.components.zoneColor
import com.openfit.lumen.ui.theme.P
import com.openfit.lumen.ui.theme.T

@Composable
fun HeartScreen(model: HeartModel, onBack: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit, onOpenBiology: () -> Unit) {
  PulseScreen(P.HeartBg) {
    DetailHeader("Heart", onBack)
    DayPicker(model.dayLabel, model.canGoForward, onPrev, onNext)

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      BigVital("RHR", model.rhr, "bpm", model.rhrDeltaShort, model.rhrTone, Modifier.weight(1f))
      BigVital("HRV", model.hrv, "ms", model.hrvDeltaShort, model.hrvTone, Modifier.weight(1f))
    }

    PCard {
      Row(verticalAlignment = Alignment.Bottom) {
        Text("Heart rate", style = T.Title, modifier = Modifier.weight(1f))
        model.peakLabel?.let { Text(it, style = T.Small) }
      }
      HeartRangeChart(model.slots, "Heart rate through the day. ${model.highlight}")
      Text(model.highlight, style = T.Small)
    }

    PCard(gap = 12.dp) {
      Text("Heart rate zones", style = T.Title)
      val total = model.zones.sumOf { it.minutes }.coerceAtLeast(60)
      ZoneStackBar(model.zones.map { zoneColor(it.zone) to it.minutes / total.toFloat() }, "Time in heart rate zones")
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        model.zones.forEach { z ->
          Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(P.Inner).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Box(Modifier.size(8.dp).clip(CircleShape).background(zoneColor(z.zone)))
              Text(z.name, style = T.Tiny)
            }
            Text("${z.minutes}m", style = T.Num20)
          }
        }
      }
      Text(model.zonesNote, style = T.Tiny)
    }

    PCard {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Resting HR · 30 days", style = T.Title, modifier = Modifier.weight(1f))
        model.rhrRange?.let {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(LumenIcons.CheckCircle, contentDescription = null, tint = P.GoodText, modifier = Modifier.size(14.dp))
            Text("Normal ${it.low.toInt()}–${it.high.toInt()}", style = T.Small.copy(color = P.GoodText, fontWeight = FontWeight.SemiBold))
          }
        }
      }
      RestingHrBars(model.rhrDays, model.rhrRange, "Resting heart rate for the last 30 days. ${model.rhrTrend}")
      Text(model.rhrTrend.removePrefix("Shaded band is your normal range. "), style = T.Small)
    }

    model.cardio?.let { c ->
      Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(P.Card).clickable(role = Role.Button, onClickLabel = "Open biological age", onClick = onOpenBiology).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        GlowRing(fraction = 0.72f, colors = P.Age, size = 72.dp, stroke = 8.dp, description = "Cardio fitness, VO2 max ${c.vo2}") { p ->
          Text(countUp(c.vo2, p), style = T.Num20.copy(fontWeight = FontWeight.ExtraBold))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text("Cardio fitness", style = T.Title)
          Text(c.note, style = T.Small)
        }
        Icon(LumenIcons.ChevronRight, contentDescription = null, tint = P.Muted, modifier = Modifier.size(16.dp))
      }
    }
  }
}

@Composable
private fun BigVital(label: String, value: String, unit: String, delta: String, tone: Tone, modifier: Modifier) {
  Column(modifier.clip(RoundedCornerShape(22.dp)).background(P.Card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(label, style = T.Label.copy(color = P.Muted), modifier = Modifier.weight(1f))
      StatusDot(tone)
    }
    Row(verticalAlignment = Alignment.Bottom) {
      Text(value, style = T.Display.copy(fontSize = 40.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold))
      Text(" $unit", style = T.Title.copy(fontWeight = FontWeight.Normal, color = P.Muted))
    }
    Text(delta, style = T.Small.copy(color = toneText(tone), fontWeight = FontWeight.SemiBold))
  }
}
