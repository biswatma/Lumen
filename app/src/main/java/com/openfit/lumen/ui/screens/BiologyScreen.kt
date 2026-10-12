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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openfit.lumen.insights.BioAgeModel
import com.openfit.lumen.insights.Fmt
import com.openfit.lumen.insights.Tab
import com.openfit.lumen.ui.components.AgeTrendBars
import com.openfit.lumen.ui.components.DetailHeader
import com.openfit.lumen.ui.components.GlowRing
import com.openfit.lumen.ui.components.LumenIcons
import com.openfit.lumen.ui.components.PCard
import com.openfit.lumen.ui.components.PrimaryButton
import com.openfit.lumen.ui.components.PulseScreen
import com.openfit.lumen.ui.components.SectionTitle
import com.openfit.lumen.ui.components.SplitStat
import com.openfit.lumen.ui.components.countUp
import com.openfit.lumen.ui.theme.P
import com.openfit.lumen.ui.theme.T

@Composable
fun BiologyScreen(model: BioAgeModel?, ageSet: Boolean, onBack: () -> Unit, onOpen: (Tab) -> Unit, onSetAge: () -> Unit) {
  PulseScreen(P.AgeBg, gap = 18.dp) {
    DetailHeader("Biological age", onBack)
    if (model == null) {
      PCard(gap = 12.dp) {
        Text(if (ageSet) "Not enough data yet" else "Add your age", style = T.H2)
        Text(
          if (ageSet) "Biological age needs at least two of: cardio fitness (VO₂ max), a week of resting heart rate or HRV, zone minutes, a week of sleep, or weight and height from Google Health."
          else "Biological age compares your Google Health readings with typical values for your calendar age. Set your age on the Profile & data screen.",
          style = T.Body14.copy(color = P.Soft),
        )
        if (!ageSet) PrimaryButton("Set my age", onClick = onSetAge)
      }
      Disclaimer()
      return@PulseScreen
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
      GlowRing(
        fraction = ((model.age - 20) / 40).toFloat(),
        colors = P.Age,
        size = 230.dp,
        stroke = 15.dp,
        startAngle = 130f,
        sweep = 280f,
        trackColor = Color.White.copy(alpha = 0.08f),
        discTop = Color(0xFF33433E),
        discBottom = Color(0xFF1D2124),
        marker = ((model.calendarAge - 20) / 40.0).toFloat(),
        description = "Biological age ${Fmt.dec(model.age)} years, calendar age ${model.calendarAge}",
      ) { p ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(countUp(Fmt.dec(model.age), p, from = 20.0), style = T.Display)
          Text("years", style = T.Body14.copy(color = P.Muted))
          val younger = model.delta <= 0
          Text(
            "${Fmt.dec(kotlin.math.abs(model.delta))} yrs ${if (younger) "younger" else "older"}",
            style = T.Small.copy(fontWeight = FontWeight.Bold, color = if (younger) P.GoodText else P.Bad),
            modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(999.dp)).background(if (younger) P.GoodBg else P.BadBg).padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }
      SplitStat(
        buildList {
          add("Calendar" to model.calendarAge.toString())
          model.sinceDelta?.let { add((model.sinceLabel ?: "Since") to Fmt.signed(it, 1)) }
          add("Data used" to "${model.daysUsed}d")
        },
        valueColors = buildList {
          add(P.Teal)
          model.sinceDelta?.let { add(if (it <= 0) P.Good else P.Bad) }
          add(P.Text)
        },
      )
    }

    if (model.trend.size >= 2) {
      PCard(gap = 12.dp, radius = 24.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("${model.trend.size}-month trend", style = T.NavTitle, modifier = Modifier.weight(1f))
          Text("monthly", style = T.Small)
        }
        AgeTrendBars(model.trend, "Biological age by month: " + model.trend.joinToString { "${it.first} ${Fmt.dec(it.second)}" })
      }
    }

    SectionTitle("What’s driving it")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      model.factors.sortedBy { it.years }.forEach { f ->
        val good = f.years <= 0
        Row(
          Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.Card)
            .then(if (f.tab != null) Modifier.clickable(role = Role.Button, onClickLabel = "Open ${f.name}") { onOpen(f.tab) } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(Modifier.size(10.dp).clip(CircleShape).background(if (good) P.GoodText else P.Bad))
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(f.name, style = T.Title.copy(fontWeight = FontWeight.SemiBold))
            Text(f.reading, style = T.Small)
          }
          Text(
            "${Fmt.signed(f.years, 1)} yrs",
            style = T.Body14.copy(fontWeight = FontWeight.Bold, color = if (good) P.GoodText else P.Bad),
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (good) P.GoodBg else P.BadBg).padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }
      }
    }

    Row(
      Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(P.InsightBg).padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(LumenIcons.Sparkle, contentDescription = null, tint = P.Lilac, modifier = Modifier.padding(top = 2.dp).size(20.dp))
      val lever = model.lever
      Text(
        buildAnnotatedString {
          if (lever.startsWith("Biggest lever:")) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Biggest lever:") }
            append(lever.removePrefix("Biggest lever:"))
          } else append(lever)
        },
        style = T.Label.copy(fontWeight = FontWeight.Normal, lineHeight = 22.sp),
      )
    }
    Disclaimer()
  }
}

@Composable
private fun Disclaimer() {
  Text(
    "Estimated on this phone from Google Health readings and your age. An estimate for motivation — not a medical test.",
    style = T.Tiny.copy(color = P.Faint, lineHeight = 18.sp),
    modifier = Modifier.padding(horizontal = 4.dp),
  )
}
