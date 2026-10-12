package com.openfit.lumen.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openfit.lumen.insights.ActivityModel
import com.openfit.lumen.insights.Insights
import com.openfit.lumen.ui.components.DayPicker
import com.openfit.lumen.ui.components.DetailHeader
import com.openfit.lumen.ui.components.GlowRing
import com.openfit.lumen.ui.components.HourBars
import com.openfit.lumen.ui.components.LumenIcons
import com.openfit.lumen.ui.components.MiniTile
import com.openfit.lumen.ui.components.PCard
import com.openfit.lumen.ui.components.PulseScreen
import com.openfit.lumen.ui.components.SegmentBar
import com.openfit.lumen.ui.components.countUp
import com.openfit.lumen.ui.theme.P
import com.openfit.lumen.ui.theme.T

@Composable
fun MoveScreen(model: ActivityModel, onBack: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit) {
  PulseScreen(P.MoveBg) {
    DetailHeader("Fitness", onBack)
    DayPicker(model.dayLabel, model.canGoForward, onPrev, onNext)

    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
      GlowRing(
        fraction = model.stepsFraction,
        colors = P.Move,
        size = 220.dp,
        stroke = 15.dp,
        trackColor = Color.White.copy(alpha = 0.08f),
        discTop = Color(0xFF3D3329),
        discBottom = Color(0xFF211D19),
        description = "${model.steps} steps, ${model.stepsPct} of goal",
      ) { p ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(countUp(model.steps, p), style = T.Display.copy(fontSize = 46.sp, lineHeight = 48.sp))
          Text("steps · ${model.stepsPct} of goal", style = T.Small.copy(color = Color(0xFFC9B9A6)))
        }
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      val byLabel = model.stats.associateBy { it.label }
      MiniTile("Distance", byLabel["Distance"]?.value ?: "—", Modifier.weight(1f))
      MiniTile("Calories", byLabel["Calories burned"]?.value ?: "—", Modifier.weight(1f))
      MiniTile("Active", byLabel["Active calories"]?.value ?: "—", Modifier.weight(1f))
      MiniTile("Quiet hrs", model.quietHours.toString(), Modifier.weight(1f))
    }

    PCard {
      Row(verticalAlignment = Alignment.Bottom) {
        Text("Steps by hour", style = T.Title, modifier = Modifier.weight(1f))
        model.stats.firstOrNull { it.label == "Longest quiet stretch" }?.takeIf { it.value != "—" }?.let { Text("longest quiet ${it.value}", style = T.Small) }
      }
      HourBars(model.hours, model.hourLabels, "Steps by hour. ${model.hoursNote}")
      Text(model.hoursNote, style = T.Small)
    }

    PCard(gap = 12.dp) {
      Row(verticalAlignment = Alignment.Bottom) {
        Text("Active Zone Minutes", style = T.Title, modifier = Modifier.weight(1f))
        Text("${model.azm7Total}", style = T.Body14.copy(fontWeight = FontWeight.Bold))
        Text(" / ${Insights.AZM_GOAL.toInt()} · 7 days", style = T.Body14.copy(color = P.Muted))
      }
      SegmentBar(model.azm7Total / Insights.AZM_GOAL.toFloat(), 40, 22.dp, hot = P.StepHot, warm = P.Orange, description = "${model.azm7Total} zone minutes in 7 days")
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        model.azm7.forEach { (d, v) ->
          Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(P.Inner).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (v > 0) v.toString() else "–", style = T.Title, textAlign = TextAlign.Center)
            Text(d, style = T.Micro)
          }
        }
      }
      Text(model.azm7Note, style = T.Small)
    }

    Text("Workouts", style = T.H2.copy(fontSize = 20.sp), modifier = Modifier.padding(start = 4.dp, top = 4.dp))
    if (model.workouts.isEmpty()) PCard { Text("No workouts recorded for this day.", style = T.Body14.copy(color = P.Muted)) }
    model.workouts.forEach { w ->
      Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(P.Card).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Box(
          Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(listOf(P.StepHot, P.Orange))),
          contentAlignment = Alignment.Center,
        ) { Icon(LumenIcons.Runner, contentDescription = null, tint = Color(0xFF2A1A08), modifier = Modifier.size(24.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(w.name, style = T.Title)
          Text(w.whenText, style = T.Small)
        }
        Column(horizontalAlignment = Alignment.End) {
          Text(w.duration, style = T.H2)
          if (w.heartRate.isNotEmpty()) Text(w.heartRate, style = T.Tiny)
        }
      }
    }
  }
}
