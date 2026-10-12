package com.openfit.lumen.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.openfit.lumen.data.SleepStages.Stage
import com.openfit.lumen.insights.Fmt
import com.openfit.lumen.insights.HourBar
import com.openfit.lumen.insights.HrSlot
import com.openfit.lumen.insights.HypnoSpan
import com.openfit.lumen.insights.Range
import com.openfit.lumen.insights.Zone
import com.openfit.lumen.ui.theme.P
import com.openfit.lumen.ui.theme.T

fun stageColor(s: Stage): Color = when (s) {
  Stage.AWAKE -> P.Awake
  Stage.REM -> P.Rem
  Stage.LIGHT -> P.Light
  Stage.DEEP -> P.Deep
}

fun stageName(s: Stage) = when (s) {
  Stage.AWAKE -> "Awake"
  Stage.REM -> "REM"
  Stage.LIGHT -> "Light"
  Stage.DEEP -> "Deep"
}

fun zoneColor(z: Zone): Color = when (z) {
  Zone.BELOW -> P.Calm
  Zone.FAT_BURN -> P.Amber
  Zone.CARDIO -> P.Cardio
  Zone.PEAK -> P.Peak
}

@Composable
fun AxisLabels(labels: List<String>, startPad: Dp = 0.dp) {
  Row(Modifier.fillMaxWidth().padding(start = startPad), horizontalArrangement = Arrangement.SpaceBetween) {
    labels.forEach { Text(it, style = T.Micro) }
  }
}

/** Sleep stages through the night: a row per stage, blocks along time. */
@Composable
fun Hypnogram(spans: List<HypnoSpan>, axis: List<String>, description: String) {
  val rows = listOf(Stage.AWAKE, Stage.REM, Stage.LIGHT, Stage.DEEP)
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Column(Modifier.width(40.dp).height(120.dp), verticalArrangement = Arrangement.SpaceAround) {
        rows.forEach { Text(stageName(it), style = T.Micro) }
      }
      Canvas(Modifier.weight(1f).height(120.dp).clip(RoundedCornerShape(10.dp)).background(P.Plot).semantics { contentDescription = description }) {
        val rowH = size.height / 4
        val barH = rowH * 0.72f
        spans.forEach { s ->
          drawRoundRect(
            stageColor(s.stage),
            Offset(s.start * size.width, rows.indexOf(s.stage) * rowH + (rowH - barH) / 2),
            Size((s.width * size.width).coerceAtLeast(3f), barH),
            CornerRadius(6.dp.toPx()),
          )
        }
      }
    }
    AxisLabels(axis, startPad = 48.dp)
  }
}

/** Heart rate min–max per 30-minute slot, coloured by zone. */
@Composable
fun HeartRangeChart(slots: List<HrSlot>, description: String) {
  val lo = 40.0
  val hi = maxOf(160.0, (slots.maxOfOrNull { it.high } ?: 0.0) + 10)
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Canvas(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)).background(P.Plot).semantics { contentDescription = description }) {
      val slotW = size.width / 48
      val barW = 4.dp.toPx().coerceAtMost(slotW * 0.8f)
      fun y(v: Double) = size.height * (1 - ((v - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f))
      slots.forEach { s ->
        val top = y(s.high)
        val bottom = y(s.low)
        drawRoundRect(zoneColor(s.zone), Offset(s.index * slotW + (slotW - barW) / 2, top), Size(barW, (bottom - top).coerceAtLeast(4f)), CornerRadius(2.dp.toPx()))
      }
    }
    AxisLabels(listOf("00", "06", "12", "18", "24"))
  }
}

/** 24 hourly step bars; dim = quiet waking hour. */
@Composable
fun HourBars(hours: List<HourBar>, labels: List<String>, description: String) {
  val max = (hours.maxOfOrNull { it.steps } ?: 0.0).coerceAtLeast(500.0)
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(Modifier.fillMaxWidth().height(100.dp).semantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
      hours.forEach { h ->
        val c = when {
          h.low || h.steps <= 0 -> P.QuietHour
          h.steps > 1500 -> P.Orange
          else -> P.StepHot
        }
        Box(Modifier.weight(1f).fillMaxHeight((h.steps / max).toFloat().coerceIn(0.04f, 1f)).clip(RoundedCornerShape(4.dp)).background(c))
      }
    }
    AxisLabels(labels)
  }
}

/** 30 days of resting HR with the personal normal band shaded green. */
@Composable
fun RestingHrBars(days: List<Double?>, range: Range?, description: String) {
  val known = days.filterNotNull()
  val lo = minOf(known.minOrNull() ?: 50.0, range?.low ?: 50.0) - 4
  val hi = maxOf(known.maxOrNull() ?: 70.0, range?.high ?: 70.0) + 2
  Canvas(Modifier.fillMaxWidth().height(72.dp).semantics { contentDescription = description }) {
    fun y(v: Double) = size.height * (1 - ((v - lo) / (hi - lo)).toFloat())
    if (range != null) {
      drawRoundRect(P.Good.copy(alpha = 0.10f), Offset(0f, y(range.high)), Size(size.width, (y(range.low) - y(range.high)).coerceAtLeast(6f)), CornerRadius(8.dp.toPx()))
    }
    val gap = 3.dp.toPx()
    val w = (size.width - gap * (days.size - 1)) / days.size
    days.forEachIndexed { i, v ->
      if (v == null) return@forEachIndexed
      val top = y(v)
      drawRoundRect(if (i == days.lastIndex) P.Good else Color(0xFF4A4F5C), Offset(i * (w + gap), top), Size(w, size.height - top), CornerRadius(3.dp.toPx()))
    }
  }
}

/** Centered sparkline bars in a tinted pill, as on the Trends rows. */
@Composable
fun Spark(values: List<Double?>, modifier: Modifier = Modifier) {
  val known = values.filterNotNull()
  val min = known.minOrNull() ?: 0.0
  val max = known.maxOrNull() ?: 1.0
  Row(
    modifier.height(44.dp).clip(RoundedCornerShape(10.dp)).background(P.Light.copy(alpha = 0.16f)).padding(horizontal = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    values.forEach { v ->
      val f = if (v == null) 0.08f else if (max == min) 0.5f else 0.2f + 0.75f * ((v - min) / (max - min)).toFloat()
      Box(Modifier.weight(1f).fillMaxHeight(f * 0.85f).clip(RoundedCornerShape(2.dp)).background(if (v == null) P.Empty else P.Spark))
    }
  }
}

/** Monthly biological-age bars; the latest is highlighted. */
@Composable
fun AgeTrendBars(points: List<Pair<String, Double>>, description: String) {
  val min = points.minOf { it.second }
  val max = points.maxOf { it.second }
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(Modifier.fillMaxWidth().height(116.dp).semantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
      points.forEachIndexed { i, (_, v) ->
        val last = i == points.lastIndex
        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom)) {
          Text(Fmt.dec(v), style = T.Tiny.copy(fontWeight = FontWeight.Bold, color = if (last) P.GoodText else P.Muted))
          val f = if (max == min) 0.6f else 0.25f + 0.55f * ((v - min) / (max - min)).toFloat()
          Box(Modifier.fillMaxWidth().fillMaxHeight(f).clip(RoundedCornerShape(10.dp)).background(if (last) P.Good else P.Empty))
        }
      }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      points.forEach { (m, _) -> Text(m, style = T.Tiny, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
    }
  }
}

/** Proportional bar of time in fat burn / cardio / peak. */
@Composable
fun ZoneStackBar(parts: List<Pair<Color, Float>>, description: String) {
  Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(P.Empty).semantics { contentDescription = description }) {
    parts.filter { it.second > 0f }.forEach { (c, f) -> Box(Modifier.weight(f).fillMaxHeight().background(c)) }
    val rest = 1f - parts.sumOf { it.second.toDouble() }.toFloat()
    if (rest > 0.001f) Box(Modifier.weight(rest).fillMaxHeight())
  }
}

/**
 * Steps per day (or week) as bars: green where the goal was hit, amber below it,
 * with a dashed goal line and a thin average line. Bars grow in, left to right.
 */
@Composable
fun StepsBarChart(bars: List<Double?>, average: Double, goal: Double, labels: List<String>, description: String) {
  val p = rememberEntrance(1f, durationMs = 1100)
  val top = maxOf(goal * 1.15, (bars.filterNotNull().maxOrNull() ?: 0.0) * 1.05)
  val many = bars.size > 20
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Box(Modifier.fillMaxWidth().height(130.dp)) {
      Canvas(Modifier.fillMaxWidth().height(130.dp).padding(top = 6.dp).semantics { contentDescription = description }) {
        fun y(v: Double) = size.height * (1 - (v / top).toFloat())
        val gap = (if (many) 2 else 8).dp.toPx()
        val w = (size.width - gap * (bars.size - 1)) / bars.size
        val r = (if (many) 3 else 8).dp.toPx()
        bars.forEachIndexed { i, v ->
          // stagger: each bar starts a little after the one before it
          val local = ((p * 1.4f) - i.toFloat() / bars.size * 0.4f).coerceIn(0f, 1f)
          val x = i * (w + gap)
          if (v == null) {
            drawRoundRect(P.Empty, Offset(x, size.height - 4.dp.toPx()), Size(w, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
            return@forEachIndexed
          }
          val h = ((size.height - y(v)) * local).coerceAtLeast(4.dp.toPx() * local)
          val hit = v >= goal
          val brush = Brush.verticalGradient(
            if (hit) listOf(Color(0xFF8EF5B5), Color(0xFF3FBF6A)) else listOf(Color(0xFFFFD27A), Color(0xFFF08A3C)),
            startY = size.height - h, endY = size.height,
          )
          drawRoundRect(brush, Offset(x, size.height - h), Size(w, h), CornerRadius(r))
        }
        val goalY = y(goal)
        drawLine(P.GoodText.copy(alpha = 0.7f), Offset(0f, goalY), Offset(size.width, goalY), 1.5.dp.toPx(),
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
        if (average > 0) {
          val avgY = y(average)
          drawLine(Color.White.copy(alpha = 0.35f * p), Offset(0f, avgY), Offset(size.width, avgY), 1.dp.toPx())
        }
      }
      Text(
        "${(goal / 1000).toInt()}k goal",
        style = T.Micro.copy(fontWeight = FontWeight.Bold, color = P.GoodText),
        modifier = Modifier.align(Alignment.TopEnd),
      )
    }
    AxisLabels(labels)
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
      Legend(P.GoodText, "Goal hit")
      Legend(Color(0xFFF7B548), "Below goal")
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.width(12.dp).height(1.dp).background(Color.White.copy(alpha = 0.5f)))
        Text("Average", style = T.Tiny)
      }
    }
  }
}

@Composable
private fun Legend(color: Color, label: String) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Box(Modifier.width(10.dp).height(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
    Text(label, style = T.Tiny)
  }
}

/**
 * A smooth area sparkline: purple gradient fill under a light line, drawn in
 * from left to right. Missing points are skipped and the curve joins across them.
 */
@Composable
fun AreaSpark(values: List<Double?>, labels: List<String>, description: String) {
  val p = rememberEntrance(1f, durationMs = 1200)
  val raw = values.withIndex().filter { it.value != null }.map { it.index to it.value!! }
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    if (raw.size < 2) {
      Box(Modifier.fillMaxWidth().height(84.dp), contentAlignment = Alignment.Center) { Text("Not enough data yet", style = T.Tiny.copy(color = Color(0xFF9A93B8))) }
    } else {
      // light smoothing for long series, like the design
      val pts = if (raw.size > 10) raw.mapIndexed { i, (x, v) ->
        val prev = raw[maxOf(i - 1, 0)].second
        val next = raw[minOf(i + 1, raw.lastIndex)].second
        x to (prev + v * 2 + next) / 4
      } else raw
      val vmin = pts.minOf { it.second }
      val vmax = pts.maxOf { it.second }
      val span = (vmax - vmin).takeIf { it > 0 } ?: 1.0
      val lo = vmin - span * 0.45
      val hi = vmax + span * 0.08
      val n = (values.size - 1).coerceAtLeast(1)
      Canvas(Modifier.fillMaxWidth().height(84.dp).semantics { contentDescription = description }) {
        val top = 6.dp.toPx()
        val o = pts.map { (i, v) -> Offset(i.toFloat() / n * size.width, top + (1 - ((v - lo) / (hi - lo)).toFloat()) * (size.height - top)) }
        val line = Path().apply {
          moveTo(o[0].x, o[0].y)
          for (i in 0 until o.lastIndex) {
            val p0 = o[maxOf(i - 1, 0)]
            val p1 = o[i]
            val p2 = o[i + 1]
            val p3 = o[minOf(i + 2, o.lastIndex)]
            cubicTo(p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6, p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6, p2.x, p2.y)
          }
        }
        val area = Path().apply {
          addPath(line)
          lineTo(o.last().x, size.height)
          lineTo(o.first().x, size.height)
          close()
        }
        clipRect(right = size.width * p) {
          drawPath(area, Brush.verticalGradient(
            0f to Color(0xFFB08CFF).copy(alpha = 0.95f), 0.55f to Color(0xFF6E4FD0).copy(alpha = 0.55f), 1f to Color(0xFF2A1F55).copy(alpha = 0.15f),
          ))
          drawPath(line, Color(0xFFD9C8FF), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawLine(Color(0xFF4A4566), Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx())
      }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      labels.forEach { Text(it, style = T.Micro.copy(color = Color(0xFF9A93B8))) }
    }
  }
}
