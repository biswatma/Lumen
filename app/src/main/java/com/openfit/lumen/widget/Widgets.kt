package com.openfit.lumen.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.openfit.lumen.LumenApp
import com.openfit.lumen.MainActivity
import com.openfit.lumen.R
import com.openfit.lumen.insights.Clock24
import com.openfit.lumen.insights.Insights
import com.openfit.lumen.insights.Tone
import com.openfit.lumen.insights.WidgetModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

// ======================================================================
// Data + updates
// ======================================================================

/** What a widget renders: the model, the last sync time, or null when Lumen isn't connected yet. */
data class WidgetState(val model: WidgetModel, val synced: String)

private suspend fun loadState(context: Context): WidgetState? {
  val app = context.applicationContext as LumenApp
  val info = app.settings.info.value
  if (!info.connected) return null
  val clock = Clock24(DateFormat.is24HourFormat(context))
  val insights = Insights(app.database.healthDao(), clock, age = { app.settings.info.value.age })
  val synced = info.lastSyncMs?.let { ms ->
    val day = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    if (day == LocalDate.now()) clock.time(ms) else "${day.dayOfMonth}/${day.monthValue} ${clock.time(ms)}"
  } ?: "not synced"
  return WidgetState(insights.widgetModel(), synced)
}

object LumenWidgets {
  private val all: List<GlanceAppWidget> = listOf(
    TodayWidget(), StepsRingWidget(), DistanceWidget(), GlanceStripWidget(),
    OverviewWidget(), SleepWidget(), BodyAgeWidget(), StepsWeekWidget(),
  )

  /** Redraw every placed widget, e.g. after a sync. Cheap when none are placed. */
  suspend fun updateAll(context: Context) {
    val manager = GlanceAppWidgetManager(context)
    all.forEach { w -> if (manager.getGlanceIds(w.javaClass).isNotEmpty()) w.updateAll(context) }
  }
}

/** Base for every Lumen widget: loads the state once and shows a connect prompt if needed. */
abstract class LumenWidget(private val opens: String?) : GlanceAppWidget() {
  override val sizeMode = SizeMode.Exact

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val state = runCatching { loadState(context) }.getOrNull()
    provideContent {
      if (state == null) NotConnected() else Content(state)
    }
  }

  @Composable abstract fun Content(state: WidgetState)

  /** Tapping a widget opens Lumen, on the matching screen where there is one. */
  protected fun open(context: Context) = actionStartActivity(
    Intent(context, MainActivity::class.java)
      .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
      .apply { opens?.let { putExtra(MainActivity.EXTRA_SCREEN, it) } },
  )

  @Composable
  private fun NotConnected() {
    val context = LocalContext.current
    Box(
      GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).clickable(open(context)).padding(14.dp),
      contentAlignment = Alignment.Center,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(ImageProvider(R.mipmap.ic_launcher), contentDescription = null, modifier = GlanceModifier.size(32.dp))
        Spacer(GlanceModifier.height(6.dp))
        Text("Open Lumen to connect", style = text(12.sp, MUTED))
      }
    }
  }
}

// ======================================================================
// Shared bits
// ======================================================================

private val INK = Color(0xFFF2F3F5)
private val MUTED = Color(0xFF9AA0AA)
private val FAINT = Color(0xFF6E747E)
private val GOOD = Color(0xFF6EE7A0)
private val BAD = Color(0xFFFF9B6B)

private fun text(size: TextUnit, color: Color, weight: FontWeight = FontWeight.Normal) =
  TextStyle(color = ColorProvider(color), fontSize = size, fontWeight = weight)

private fun toneColor(t: Tone) = when (t) {
  Tone.GOOD -> GOOD
  Tone.BAD -> BAD
  Tone.NEUTRAL -> MUTED
}

@Composable
private fun px(dp: Dp): Int = (dp.value * LocalContext.current.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)

@Composable
private fun Bitmap.Show(w: Dp, h: Dp, description: String? = null) =
  Image(ImageProvider(this), contentDescription = description, modifier = GlanceModifier.width(w).height(h))

@Composable
private fun Label(icon: Int, label: String, color: Color = MUTED) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Image(ImageProvider(icon), contentDescription = null, modifier = GlanceModifier.size(14.dp))
    Spacer(GlanceModifier.width(6.dp))
    Text(label, style = text(12.sp, color, FontWeight.Medium))
  }
}

/** A ring image with text centred on top of it. */
@Composable
private fun Ring(size: Dp, fraction: Float, colors: IntArray, value: String, valueSize: TextUnit, caption: String? = null, description: String) {
  Box(GlanceModifier.size(size), contentAlignment = Alignment.Center) {
    WidgetArt.ring(px(size), fraction, colors).Show(size, size, description)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(value, style = text(valueSize, INK, FontWeight.Bold))
      caption?.let { Text(it, style = text(10.sp, MUTED)) }
    }
  }
}

/** Inner width available for full-width bars: widget width minus horizontal padding. */
@Composable
private fun innerWidth(padding: Dp): Dp = (LocalSize.current.width - padding * 2).coerceAtLeast(40.dp)

// ======================================================================
// 4×2 Today
// ======================================================================

class TodayWidget : LumenWidget(opens = null) {
  @Composable override fun Content(state: WidgetState) {
    val m = state.model
    val context = LocalContext.current
    val leftWidth = (LocalSize.current.width - 32.dp - 128.dp - 29.dp).coerceAtLeast(60.dp)
    Row(
      GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg_plain)).clickable(open(context)).padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
        Label(R.drawable.ic_w_runner, "Steps")
        Spacer(GlanceModifier.defaultWeight())
        Row(verticalAlignment = Alignment.Bottom) {
          Text(m.steps, style = text(32.sp, INK, FontWeight.Bold))
          Spacer(GlanceModifier.width(4.dp))
          Text("/ 10k", style = text(12.sp, MUTED), modifier = GlanceModifier.padding(bottom = 5.dp))
        }
        Spacer(GlanceModifier.height(6.dp))
        WidgetArt.segments(px(leftWidth), px(12.dp), m.stepsFraction, 20).Show(leftWidth, 12.dp, "${m.stepsPct}% of 10,000 steps")
        Spacer(GlanceModifier.height(6.dp))
        Text(m.onPace?.let { "On pace for $it" } ?: "${m.stepsPct}% of goal", style = text(11.sp, GOOD, FontWeight.Medium))
      }
      Spacer(GlanceModifier.width(14.dp))
      Box(GlanceModifier.width(1.dp).fillMaxHeight().background(ImageProvider(R.drawable.widget_divider))) {}
      Spacer(GlanceModifier.width(14.dp))
      Column(GlanceModifier.width(128.dp).fillMaxHeight()) {
        MiniRow(Color(0xFF4ADE80), "Distance", m.distance)
        Spacer(GlanceModifier.defaultWeight())
        MiniRow(Color(0xFF9DB8FF), "Sleep", m.sleep)
        Spacer(GlanceModifier.defaultWeight())
        MiniRow(BAD, "Rest HR", m.restingHr)
        Spacer(GlanceModifier.defaultWeight())
        Text("synced ${state.synced}", style = text(10.sp, FAINT), modifier = GlanceModifier.fillMaxWidth())
      }
    }
  }

  @Composable private fun MiniRow(dot: Color, label: String, value: String) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Box(GlanceModifier.size(8.dp).background(ImageProvider(dotDrawable(dot)))) {}
      Spacer(GlanceModifier.width(8.dp))
      Text(label, style = text(12.sp, MUTED), modifier = GlanceModifier.defaultWeight())
      Text(value, style = text(15.sp, INK, FontWeight.Bold))
    }
  }
}

/** Small round dots are drawn as bitmaps so they stay circular on every launcher. */
private val dotCache = HashMap<Long, Bitmap>()
private fun dotDrawable(c: Color): Bitmap = dotCache.getOrPut(c.value.toLong()) {
  val size = 24
  Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { b ->
    android.graphics.Canvas(b).drawCircle(size / 2f, size / 2f, size / 2f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.argb((c.alpha * 255).roundToInt(), (c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt())
    })
  }
}

// ======================================================================
// 2×2 Steps ring
// ======================================================================

class StepsRingWidget : LumenWidget(opens = "FITNESS") {
  @Composable override fun Content(state: WidgetState) {
    val m = state.model
    val context = LocalContext.current
    Column(
      GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg_plain)).clickable(open(context)).padding(10.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val ring = (minOf(LocalSize.current.width, LocalSize.current.height) - 44.dp).coerceIn(64.dp, 120.dp)
      Ring(ring, m.stepsFraction, WidgetArt.MOVE, m.steps, 20.sp, "steps", "${m.steps} steps, ${m.stepsPct}% of goal")
      Spacer(GlanceModifier.height(6.dp))
      Text("${m.stepsPct}% of 10,000", style = text(11.sp, MUTED))
    }
  }
}

// ======================================================================
// 2×2 Distance
// ======================================================================

class DistanceWidget : LumenWidget(opens = "FITNESS") {
  @Composable override fun Content(state: WidgetState) {
    val m = state.model
    val context = LocalContext.current
    val barWidth = innerWidth(14.dp)
    Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg_plain)).clickable(open(context)).padding(14.dp)) {
      Label(R.drawable.ic_w_pin, "Distance")
      Spacer(GlanceModifier.defaultWeight())
      Row(verticalAlignment = Alignment.Bottom) {
        Text(m.distanceUnitless, style = text(36.sp, INK, FontWeight.Bold))
        Spacer(GlanceModifier.width(4.dp))
        Text("km", style = text(15.sp, MUTED, FontWeight.Bold), modifier = GlanceModifier.padding(bottom = 6.dp))
      }
      Spacer(GlanceModifier.defaultWeight())
      WidgetArt.progress(px(barWidth), px(8.dp), m.distanceFraction, 0xFF1FB257.toInt(), 0xFFE4F75A.toInt()).Show(barWidth, 8.dp, m.distanceNote)
      Spacer(GlanceModifier.height(8.dp))
      Text(m.distanceNote, style = text(11.sp, MUTED), maxLines = 1)
    }
  }
}

// ======================================================================
// 4×1 Glance
// ======================================================================

class GlanceStripWidget : LumenWidget(opens = null) {
  @Composable override fun Content(state: WidgetState) {
    val m = state.model
    val context = LocalContext.current
    Row(
      GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).clickable(open(context)).padding(horizontal = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Cell(m.steps, "steps", Color(0xFFFFD27A))
      Cell(m.distance, "distance", GOOD)
      Cell(m.sleep, "sleep", Color(0xFFB4CCFF))
      Cell(m.bodyAge ?: "—", "body age", Color(0xFF4FD1C5))
    }
  }

  @Composable private fun androidx.glance.layout.RowScope.Cell(value: String, label: String, color: Color) {
    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
      Text(value, style = text(16.sp, color, FontWeight.Bold), maxLines = 1)
      Text(label, style = text(10.sp, MUTED))
    }
  }
}

// ======================================================================
// 4×4 Overview
// ======================================================================

class OverviewWidget : LumenWidget(opens = null) {
  @Composable override fun Content(state: WidgetState) {
    val m = state.model
    val context = LocalContext.current
    val panelWidth = innerWidth(16.dp) - 24.dp
    Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).clickable(open(context)).padding(16.dp)) {
      Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(R.mipmap.ic_launcher), contentDescription = null, modifier = GlanceModifier.size(22.dp))
        Spacer(GlanceModifier.width(8.dp))
        Text("Today", style = text(14.sp, INK, FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
        Text(state.synced, style = text(11.sp, FAINT))
      }
      Spacer(GlanceModifier.defaultWeight())
      Row(GlanceModifier.fillMaxWidth()) {
        RingCell(m.stepsFraction, WidgetArt.MOVE, m.steps, "Steps")
        RingCell(m.distanceFraction, WidgetArt.RECOVERY, m.distance, "Distance")
        RingCell(m.sleepFraction, WidgetArt.SLEEP, m.sleep, "Sleep")
      }
      Spacer(GlanceModifier.defaultWeight())
      Column(GlanceModifier.fillMaxWidth().background(ImageProvider(R.drawable.widget_panel)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(GlanceModifier.fillMaxWidth()) {
          Text("Steps · ${m.stepsPct}% of 10,000", style = text(11.sp, MUTED, FontWeight.Medium), modifier = GlanceModifier.defaultWeight())
          m.onPace?.let { Text("on pace for $it", style = text(11.sp, GOOD, FontWeight.Bold)) }
        }
        Spacer(GlanceModifier.height(6.dp))
        WidgetArt.segments(px(panelWidth), px(10.dp), m.stepsFraction, 30).Show(panelWidth, 10.dp, "${m.stepsPct}% of 10,000 steps")
      }
      Spacer(GlanceModifier.height(10.dp))
      Row(GlanceModifier.fillMaxWidth()) {
        Vital(m.restingHr, "RHR", INK)
        Spacer(GlanceModifier.width(6.dp))
        Vital(m.hrv, "HRV", INK)
        Spacer(GlanceModifier.width(6.dp))
        Vital(m.spo2, "SpO₂", INK)
        Spacer(GlanceModifier.width(6.dp))
        Vital(m.bodyAge ?: "—", "Body age", GOOD)
      }
    }
  }

  @Composable private fun androidx.glance.layout.RowScope.RingCell(fraction: Float, colors: IntArray, value: String, label: String) {
    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
      Ring(78.dp, fraction, colors, value, 13.sp, description = "$label $value")
      Spacer(GlanceModifier.height(4.dp))
      Text(label, style = text(11.sp, Color(0xFFC4C8D0), FontWeight.Medium))
    }
  }

  @Composable private fun androidx.glance.layout.RowScope.Vital(value: String, label: String, color: Color) {
    Column(GlanceModifier.defaultWeight().background(ImageProvider(R.drawable.widget_panel_small)).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Text(value, style = text(15.sp, color, FontWeight.Bold), maxLines = 1)
      Text(label, style = text(10.sp, MUTED), maxLines = 1)
    }
  }
}

// ======================================================================
// 2×2 Sleep
// ======================================================================

class SleepWidget : LumenWidget(opens = "SLEEP") {
  @Composable override fun Content(state: WidgetState) {
    val m = state.model
    val context = LocalContext.current
    val barWidth = innerWidth(14.dp)
    Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg_sleep)).clickable(open(context)).padding(14.dp)) {
      Label(R.drawable.ic_w_moon, "Sleep", Color(0xFFA9B0D6))
      Spacer(GlanceModifier.defaultWeight())
      Text(m.sleep, style = text(30.sp, INK, FontWeight.Bold), maxLines = 1)
      Spacer(GlanceModifier.defaultWeight())
      WidgetArt.stageBar(px(barWidth), px(8.dp), m.sleepStages).Show(barWidth, 8.dp, "Sleep stages")
      Spacer(GlanceModifier.height(8.dp))
      Text(m.sleepNote, style = text(11.sp, toneColor(m.sleepNoteTone), FontWeight.Medium), maxLines = 1)
    }
  }
}

// ======================================================================
// 2×2 Body age
// ======================================================================

class BodyAgeWidget : LumenWidget(opens = "BIOLOGY") {
  @Composable override fun Content(state: WidgetState) {
    val m = state.model
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg_age)).clickable(open(context)).padding(14.dp)) {
      Label(R.drawable.ic_w_heart, "Body age", Color(0xFF9FD8C8))
      Spacer(GlanceModifier.defaultWeight())
      Text(m.bodyAge ?: "—", style = text(40.sp, INK, FontWeight.Bold))
      Spacer(GlanceModifier.defaultWeight())
      if (m.bodyAge == null) {
        Text("Set your age in Lumen", style = text(11.sp, MUTED))
      } else {
        Text(m.bodyAgeNote.orEmpty(), style = text(12.sp, if ((m.bodyAgeDelta ?: 0.0) <= 0) GOOD else BAD, FontWeight.Bold))
        Text("calendar age ${m.calendarAge}", style = text(11.sp, MUTED))
      }
    }
  }
}

// ======================================================================
// 4×2 Steps week
// ======================================================================

class StepsWeekWidget : LumenWidget(opens = "TRENDS") {
  @Composable override fun Content(state: WidgetState) {
    val m = state.model
    val context = LocalContext.current
    val chartWidth = innerWidth(16.dp)
    val chartHeight = (LocalSize.current.height - 28.dp - 18.dp - 26.dp).coerceIn(40.dp, 160.dp)
    Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).clickable(open(context)).padding(horizontal = 16.dp, vertical = 14.dp)) {
      Row(GlanceModifier.fillMaxWidth()) {
        Text("Steps · last 7 days", style = text(12.sp, MUTED, FontWeight.Medium), modifier = GlanceModifier.defaultWeight())
        Text("avg ${m.weekAverage}", style = text(12.sp, GOOD, FontWeight.Bold))
      }
      Spacer(GlanceModifier.defaultWeight())
      WidgetArt.week(px(chartWidth), px(chartHeight), m.week.map { it.second }, Insights.STEP_GOAL)
        .Show(chartWidth, chartHeight, "Steps for the last 7 days, average ${m.weekAverage}; dashed line is the 10k goal")
      Spacer(GlanceModifier.height(4.dp))
      Row(GlanceModifier.fillMaxWidth()) {
        m.week.forEach { (d, _) ->
          Text(d, style = TextStyle(color = ColorProvider(MUTED), fontSize = 10.sp, textAlign = androidx.glance.text.TextAlign.Center), modifier = GlanceModifier.defaultWeight())
        }
      }
    }
  }
}

// ======================================================================
// Receivers (one per widget, registered in the manifest)
// ======================================================================

class TodayWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TodayWidget() }
class StepsRingWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StepsRingWidget() }
class DistanceWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DistanceWidget() }
class GlanceStripWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = GlanceStripWidget() }
class OverviewWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = OverviewWidget() }
class SleepWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = SleepWidget() }
class BodyAgeWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = BodyAgeWidget() }
class StepsWeekWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StepsWeekWidget() }
