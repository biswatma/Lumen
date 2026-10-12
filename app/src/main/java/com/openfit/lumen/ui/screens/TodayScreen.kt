package com.openfit.lumen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openfit.lumen.insights.BioAgeModel
import com.openfit.lumen.insights.Fmt
import com.openfit.lumen.insights.NoteKind
import com.openfit.lumen.insights.Tab
import com.openfit.lumen.insights.TodayModel
import com.openfit.lumen.ui.components.GlowRing
import com.openfit.lumen.ui.components.LumenIcons
import com.openfit.lumen.ui.components.PCard
import com.openfit.lumen.ui.components.PulseScreen
import com.openfit.lumen.ui.components.RingValue
import com.openfit.lumen.ui.components.SectionTitle
import com.openfit.lumen.ui.components.SegmentBar
import com.openfit.lumen.ui.components.StatusDot
import com.openfit.lumen.ui.components.countUp
import com.openfit.lumen.ui.components.rememberEntrance
import com.openfit.lumen.insights.Insights
import com.openfit.lumen.ui.theme.P
import com.openfit.lumen.ui.theme.T

@Composable
fun TodayScreen(
  model: TodayModel,
  sourceLine: String,
  syncLine: String,
  accessOff: Boolean,
  onOpen: (Tab) -> Unit,
  onOpenBiology: () -> Unit,
  onOpenSource: () -> Unit,
  refreshing: Boolean,
  onRefresh: () -> Unit,
) {
  PulseScreen(P.PlainBg, gap = 18.dp, refreshing = refreshing, onRefresh = onRefresh) {
    // header
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(model.dateLabel, style = T.Header, modifier = Modifier.weight(1f).semantics { heading() })
      Box(
        Modifier.size(44.dp).clip(CircleShape).background(P.Avatar).clickable(role = Role.Button, onClickLabel = "Profile and data source", onClick = onOpenSource)
          .semantics { contentDescription = "Profile and data source" },
        contentAlignment = Alignment.Center,
      ) { Icon(LumenIcons.Person, contentDescription = null, tint = P.AvatarInk, modifier = Modifier.size(22.dp)) }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(P.Divider))

    // rings + coaching
    PCard(radius = 26.dp, padding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 18.dp), gap = 16.dp) {
      Row(Modifier.fillMaxWidth()) {
        RingCell("Move", model.moveScore, P.Move, Modifier.weight(1f)) { onOpen(Tab.ACTIVITY) }
        Box(Modifier.width(1.dp).height(130.dp).background(P.Divider))
        RingCell("Recovery", model.recoveryScore, P.Recovery, Modifier.weight(1f)) { onOpen(Tab.HEART) }
        Box(Modifier.width(1.dp).height(130.dp).background(P.Divider))
        RingCell("Sleep", model.sleepScore, P.Sleep, Modifier.weight(1f)) { onOpen(Tab.SLEEP) }
      }
      Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("COACHING", style = T.Eyebrow)
        Text(model.coaching, style = T.Body)
      }
    }

    BioAgeCard(model.bio, onOpenBiology)

    StepsCard(model) { onOpen(Tab.ACTIVITY) }

    // health monitor
    SectionTitle("Health Monitor", Modifier.padding(top = 6.dp)) {
      Row(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onOpen(Tab.TRENDS) }.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Trends", style = T.Body.copy(color = P.Muted))
        Icon(LumenIcons.ChevronRight, contentDescription = null, tint = P.Muted, modifier = Modifier.size(16.dp))
      }
    }
    Row(
      Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      model.monitor.forEach { m ->
        Column(
          Modifier.width(116.dp).clip(RoundedCornerShape(22.dp)).background(P.Card).clickable(role = Role.Button, onClickLabel = "Open ${m.key}") { onOpen(m.tab) }.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.key, style = T.Label.copy(color = P.Muted), modifier = Modifier.weight(1f))
            StatusDot(m.tone)
          }
          Column {
            Text(m.value, style = T.Big.copy(fontSize = 30.sp))
            Text(m.unit, style = T.Label.copy(fontWeight = FontWeight.Normal, color = P.Muted))
          }
        }
      }
    }
    model.monitorNote?.let { note ->
      val ok = note.kind == NoteKind.OK
      Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (ok) P.GoodBg else P.BadBg).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(if (ok) LumenIcons.CheckCircle else LumenIcons.Alert, contentDescription = null, tint = if (ok) P.GoodText else P.Bad, modifier = Modifier.size(20.dp))
        Text(
          if (ok) "All metrics are within your normal range." else note.text,
          style = T.Label.copy(color = if (ok) P.GoodText else P.Bad, lineHeight = 21.sp),
        )
      }
    }

    // source footer
    Row(
      Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(P.Card).clickable(role = Role.Button, onClickLabel = "Open data source", onClick = onOpenSource)
        .padding(horizontal = 18.dp, vertical = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(Modifier.size(8.dp).clip(CircleShape).background(if (accessOff) P.Bad else P.Good))
      Text(sourceLine, style = T.Body14.copy(color = P.Muted), modifier = Modifier.weight(1f))
      Text(syncLine, style = T.Body14.copy(color = if (accessOff) P.Bad else P.Muted))
    }
  }
}

@Composable
private fun StepsCard(model: TodayModel, onClick: () -> Unit) {
  PCard(gap = 14.dp, padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp), onClick = onClick, clickLabel = "Open fitness") {
    val p = rememberEntrance(1f)
    Row(verticalAlignment = Alignment.Top) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(LumenIcons.Runner, contentDescription = null, tint = P.StepHot, modifier = Modifier.size(18.dp))
          Text("Steps today", style = T.Label.copy(color = P.Muted))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(countUp(model.steps, p), style = T.Display.copy(fontSize = 36.sp, lineHeight = 38.sp))
          Text("/ ${Fmt.int(Insights.STEP_GOAL)}", style = T.Label.copy(fontWeight = FontWeight.Normal, color = P.Muted), modifier = Modifier.padding(bottom = 4.dp))
        }
      }
      Text(
        "${model.moveScore}%",
        style = T.Body14.copy(fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFD27A)),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF3A2E1A)).padding(horizontal = 10.dp, vertical = 6.dp),
      )
    }
    SegmentBar(model.stepsFraction, 40, 22.dp, Modifier.fillMaxWidth(), description = "${model.steps} steps today, ${model.moveScore}% of goal")
    Box(Modifier.fillMaxWidth().height(1.dp).background(P.Divider))
    Row(Modifier.fillMaxWidth()) {
      StepStat(model.distance, "Distance", Alignment.Start, P.Text, Modifier.weight(1f))
      StepStat(model.activeKcal, "Active kcal", Alignment.CenterHorizontally, P.Text, Modifier.weight(1f))
      StepStat(model.onPace ?: "—", "On pace for", Alignment.End, P.GoodText, Modifier.weight(1f))
    }
  }
}

@Composable
private fun StepStat(value: String, label: String, align: Alignment.Horizontal, color: Color, modifier: Modifier) {
  Column(modifier, horizontalAlignment = align, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(value, style = T.Label.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = color))
    Text(label, style = T.Tiny)
  }
}

@Composable
private fun RingCell(label: String, score: Int?, colors: List<Color>, modifier: Modifier, onClick: () -> Unit) {
  Column(
    modifier.clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClickLabel = "Open $label", onClick = onClick),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    GlowRing(
      fraction = (score ?: 0) / 100f,
      colors = colors,
      size = 104.dp,
      stroke = 10.dp,
      description = if (score == null) "$label: not enough data yet" else "$label $score percent",
    ) { p ->
      if (score == null) Text("—", style = T.H1) else RingValue(countUp(score.toString(), p), "%", 27, 13)
    }
    Text(label, style = T.Label)
  }
}

@Composable
private fun BioAgeCard(bio: BioAgeModel?, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(P.Card).clickable(role = Role.Button, onClickLabel = "Open biological age", onClick = onClick).padding(18.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (bio == null) P.Calm else if (bio.delta <= 0) P.Good else P.Bad))
        Text("Biological age", style = T.Title.copy(fontWeight = FontWeight.SemiBold))
      }
      Text(
        if (bio == null) "Set your age and keep wearing your device — needs a week of heart and sleep data." else "From cardio fitness, heart, sleep and activity",
        style = T.Small,
      )
      if (bio != null) {
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
          Column {
            Text(Fmt.signed(bio.delta, 1), style = T.Big.copy(color = if (bio.delta <= 0) P.Good else P.Bad))
            Text("vs calendar", style = T.Small)
          }
          Box(Modifier.width(1.dp).height(48.dp).background(P.Divider))
          Column {
            Text(bio.calendarAge.toString(), style = T.Big.copy(color = P.Teal))
            Text("calendar", style = T.Small)
          }
        }
      }
    }
    GlowRing(
      fraction = bio?.let { ((it.age - 20) / 40).toFloat() } ?: 0f,
      colors = P.Age,
      size = 112.dp,
      stroke = 9.dp,
      startAngle = 130f,
      sweep = 280f,
      marker = bio?.let { ((it.calendarAge - 20) / 40.0).toFloat() },
      description = bio?.let { "Biological age ${Fmt.dec(it.age)}" } ?: "Biological age not available yet",
    ) { p ->
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(bio?.let { countUp(Fmt.dec(it.age), p, from = 20.0) } ?: "—", style = T.Display.copy(fontSize = 25.sp, lineHeight = 26.sp))
        bio?.let {
          Text(if (it.delta <= 0) "Younger" else "Older", style = T.Micro.copy(fontWeight = FontWeight.Bold, color = if (it.delta <= 0) P.GoodText else P.Bad))
        }
      }
    }
  }
}
