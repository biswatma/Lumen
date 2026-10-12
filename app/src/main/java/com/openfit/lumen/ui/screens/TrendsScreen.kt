package com.openfit.lumen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openfit.lumen.insights.Insights
import com.openfit.lumen.insights.StepsChart
import com.openfit.lumen.insights.Tone
import com.openfit.lumen.insights.TrendRange
import com.openfit.lumen.insights.TrendsModel
import com.openfit.lumen.ui.components.AreaSpark
import com.openfit.lumen.ui.components.LumenIcons
import com.openfit.lumen.ui.components.PCard
import com.openfit.lumen.ui.components.PulseScreen
import com.openfit.lumen.ui.components.SectionTitle
import com.openfit.lumen.ui.components.StepsBarChart
import com.openfit.lumen.ui.components.toneText
import com.openfit.lumen.ui.theme.P
import com.openfit.lumen.ui.theme.T

private val RowCard = Color(0xFF15131F)
private val RowLabel = Color(0xFFA9A2C8)

@Composable
fun TrendsScreen(range: TrendRange, model: TrendsModel?, onRange: (TrendRange) -> Unit) {
  PulseScreen(P.TrendsBg, gap = 12.dp) {
    Text("Trends", style = T.H1, modifier = Modifier.padding(start = 4.dp, top = 12.dp).semantics { heading() })

    Row(
      Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.Card).padding(4.dp).selectableGroup(),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      TrendRange.entries.forEach { r ->
        val on = r == range
        Text(
          r.label,
          style = T.Label.copy(fontWeight = FontWeight.Bold, color = if (on) Color.White else P.Muted),
          textAlign = TextAlign.Center,
          modifier = Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
            .background(if (on) Color(0xFF3A3F5C) else Color.Transparent)
            .selectable(selected = on, role = Role.Tab) { onRange(r) }
            .padding(vertical = 12.dp),
        )
      }
    }

    if (model == null) return@PulseScreen

    // key(range): charts re-run their entrance animation when the range changes
    key(range) {
      StepsCard(model.steps)

      model.rows.forEach { m ->
        Column(
          Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(RowCard).padding(PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp)),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(m.label, style = T.Small.copy(fontWeight = FontWeight.SemiBold, color = RowLabel))
              Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(m.average.removeSuffix(" avg"), style = T.Num24.copy(fontWeight = FontWeight.ExtraBold, fontSize = 22.sp))
                Text(m.delta, style = T.Small.copy(fontWeight = FontWeight.Bold, color = if (m.tone == Tone.NEUTRAL) P.NightMuted else toneText(m.tone)), modifier = Modifier.padding(bottom = 3.dp))
              }
            }
            val watch = m.tone == Tone.BAD
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) {
              Box(Modifier.size(7.dp).clip(CircleShape).background(if (watch) P.Bad else P.GoodText))
              Text(if (watch) "Worth watching" else "Normal range", style = T.Tiny.copy(fontWeight = FontWeight.SemiBold, color = if (watch) P.Bad else P.GoodText))
            }
          }
          AreaSpark(m.spark, model.xLabels, "${m.label} over ${range.label}: ${m.average}, ${m.delta}")
        }
      }
    }

    SectionTitle("Patterns in your data", Modifier.padding(top = 10.dp))
    if (model.patterns.isEmpty()) {
      PCard { Text("Patterns appear once Lumen has a few weeks of your sleep, heart and activity data to compare.", style = T.Body14.copy(color = P.Soft)) }
    }
    model.patterns.forEach { p ->
      PCard(radius = 20.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(LumenIcons.Sparkle, contentDescription = null, tint = P.Lilac, modifier = Modifier.padding(top = 2.dp).size(16.dp))
          Text(p.title, style = T.Label.copy(fontWeight = FontWeight.Bold, lineHeight = 20.sp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          PatternCell(p.aLabel, p.aValue, P.GoodText, Modifier.weight(1f))
          PatternCell(p.bLabel, p.bValue, P.Text, Modifier.weight(1f))
        }
        Text(p.basis, style = T.Tiny)
      }
    }
  }
}

@Composable
private fun StepsCard(s: StepsChart) {
  PCard(gap = 12.dp) {
    Row(verticalAlignment = Alignment.Top) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(LumenIcons.Runner, contentDescription = null, tint = P.StepHot, modifier = Modifier.size(16.dp))
          Text("Steps · ${s.title}", style = T.Body14.copy(fontWeight = FontWeight.SemiBold, color = P.Muted))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(s.average, style = T.Display.copy(fontSize = 30.sp, lineHeight = 32.sp))
          Text("avg / day", style = T.Body14.copy(color = P.Muted), modifier = Modifier.padding(bottom = 3.dp))
        }
      }
      Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val good = s.tone != Tone.BAD
        Text(
          s.delta,
          style = T.Small.copy(fontWeight = FontWeight.ExtraBold, color = if (good) P.GoodText else P.Bad),
          modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (good) P.GoodBg else P.BadBg).padding(horizontal = 10.dp, vertical = 5.dp),
        )
        Text(s.goalNote, style = T.Tiny)
      }
    }
    StepsBarChart(s.bars, s.averageValue, Insights.STEP_GOAL, s.labels, "Steps ${s.title}: ${s.average} a day on average, ${s.goalNote}")
  }
}

@Composable
private fun PatternCell(label: String, value: String, color: Color, modifier: Modifier) {
  Column(modifier.clip(RoundedCornerShape(14.dp)).background(P.Inner).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(label, style = T.Tiny)
    Text(value, style = T.Num20.copy(fontWeight = FontWeight.ExtraBold, color = color))
  }
}
