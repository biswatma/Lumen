package com.openfit.lumen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openfit.lumen.data.SleepStages.Stage
import com.openfit.lumen.insights.SleepModel
import com.openfit.lumen.ui.components.DetailHeader
import com.openfit.lumen.ui.components.GlowRing
import com.openfit.lumen.ui.components.Hypnogram
import com.openfit.lumen.ui.components.LumenIcons
import com.openfit.lumen.ui.components.MiniTile
import com.openfit.lumen.ui.components.PCard
import com.openfit.lumen.ui.components.PulseScreen
import com.openfit.lumen.ui.components.RingValue
import com.openfit.lumen.ui.components.SplitStat
import com.openfit.lumen.ui.components.countUp
import com.openfit.lumen.ui.components.stageColor
import com.openfit.lumen.ui.components.toneDot
import com.openfit.lumen.ui.theme.P
import com.openfit.lumen.ui.theme.T

@Composable
fun SleepScreen(model: SleepModel?, onBack: () -> Unit) {
  PulseScreen(P.SleepBg) {
    DetailHeader("Sleep", onBack)
    if (model == null) {
      PCard { Text("No sleep yet. Nights appear here after your device records one and Google Health shares it with Health Connect.", style = T.Body14) }
      return@PulseScreen
    }
    Text(model.dateLabel, style = T.Small.copy(color = P.NightMuted), modifier = Modifier.align(Alignment.CenterHorizontally))

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      GlowRing(
        fraction = model.score / 100f,
        colors = P.Sleep,
        size = 220.dp,
        stroke = 15.dp,
        trackColor = Color.White.copy(alpha = 0.08f),
        discTop = Color(0xFF34385E),
        discBottom = Color(0xFF1C1E36),
        description = "Slept ${model.asleep}, ${model.score} percent of an 8 hour need",
      ) { p ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          RingValue(countUp(model.score.toString(), p), "%", 60, 22, unitColor = Color(0xFFC9CEF0))
          Text("of your 8h need", style = T.Small.copy(color = P.NightMuted))
        }
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      NightTile("Time in bed", model.timeInBed, Modifier.weight(1f))
      NightTile("Time asleep", model.asleep, Modifier.weight(1f))
    }
    Text("${model.window} · ${model.awake}", style = T.Small.copy(color = P.NightMuted), modifier = Modifier.padding(horizontal = 4.dp))

    Column(
      Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.InsightBg).border(1.dp, P.Lilac.copy(alpha = 0.18f), RoundedCornerShape(20.dp)).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(LumenIcons.Sparkle, contentDescription = null, tint = P.Lilac, modifier = Modifier.size(16.dp))
        Text(model.insightTitle, style = T.Label.copy(fontWeight = FontWeight.Bold))
      }
      Text(model.insightBody, style = T.Body14.copy(color = Color(0xFFD5D8EA)))
    }

    if (model.spans.isNotEmpty()) {
      PCard {
        Text("Stages", style = T.Title)
        Hypnogram(model.spans, model.axis, "Sleep stages from ${model.window}")
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          model.stages.forEach { st ->
            val tag = when {
              st.stage == Stage.AWAKE -> st.note.substringBefore(",")
              st.pct > st.typical.high -> "High"
              st.pct < st.typical.low -> "Low"
              else -> "Normal"
            }
            val tagColor = when (tag) {
              "High" -> if (st.stage == Stage.DEEP || st.stage == Stage.REM) P.GoodText else P.Muted
              "Low" -> if (st.stage == Stage.DEEP || st.stage == Stage.REM) P.Bad else P.Muted
              else -> P.Muted
            }
            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(P.Inner).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(stageColor(st.stage)))
                Text(st.name, style = T.Tiny)
              }
              Text(st.duration, style = T.Title)
              Text(tag, style = T.Micro.copy(color = tagColor))
            }
          }
        }
      }
    }

    if (model.windDown != null && model.targetBedtime != null) {
      SplitStat(listOf("Wind down" to model.windDown, "Target bedtime" to model.targetBedtime))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      model.vitals.forEach { v ->
        val short = when (v.label) {
          "Lowest heart rate" -> "Low HR"
          "Blood oxygen avg" -> "SpO₂"
          "Breathing rate" -> "RR"
          "Skin temperature" -> "Temp"
          else -> v.label
        }
        MiniTile(short, v.value.replace(" bpm", "").replace("/min", "").replace(" °C", "°"), Modifier.weight(1f), dot = toneDot(v.tone))
      }
    }
    Text(model.nightsNote, style = T.Small.copy(color = P.NightMuted), modifier = Modifier.padding(horizontal = 4.dp))
  }
}

@Composable
private fun NightTile(label: String, value: String, modifier: Modifier) {
  Column(modifier.clip(RoundedCornerShape(18.dp)).background(P.NightCard).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(label, style = T.Small.copy(color = P.NightMuted))
    Text(value, style = T.Num24)
  }
}
