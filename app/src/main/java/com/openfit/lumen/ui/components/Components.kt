package com.openfit.lumen.ui.components

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openfit.lumen.insights.Tone
import com.openfit.lumen.ui.theme.P
import com.openfit.lumen.ui.theme.T
import kotlin.math.cos
import kotlin.math.sin

/**
 * Scrollable screen body: 16dp gutters, tinted background, room at the bottom
 * for the floating nav. With [onRefresh], pulling down from the top refreshes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseScreen(
  background: Brush,
  gap: Dp = 14.dp,
  refreshing: Boolean = false,
  onRefresh: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val body = @Composable {
    Column(
      Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
        .padding(PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp)),
      verticalArrangement = Arrangement.spacedBy(gap),
      content = content,
    )
  }
  Box(Modifier.fillMaxSize().background(background)) {
    if (onRefresh == null) body()
    else {
      val state = rememberPullToRefreshState()
      PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = Modifier.fillMaxSize(),
        indicator = {
          PullToRefreshDefaults.Indicator(
            state = state,
            isRefreshing = refreshing,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
            containerColor = P.Card,
            color = P.Good,
          )
        },
      ) { body() }
    }
  }
}

/** Back button · centered title, as on every Pulse detail screen. */
@Composable
fun DetailHeader(title: String, onBack: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
  Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(
      Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
        .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack).semantics { contentDescription = "Back" },
      contentAlignment = Alignment.Center,
    ) { Icon(LumenIcons.Back, contentDescription = null, tint = P.Text, modifier = Modifier.size(18.dp)) }
    Text(title, style = T.NavTitle, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).semantics { heading() })
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { trailing?.invoke() }
  }
}

@Composable
fun PCard(
  modifier: Modifier = Modifier,
  color: Color = P.Card,
  radius: Dp = 22.dp,
  padding: PaddingValues = PaddingValues(16.dp),
  gap: Dp = 10.dp,
  onClick: (() -> Unit)? = null,
  clickLabel: String? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = RoundedCornerShape(radius)
  Column(
    modifier.fillMaxWidth().clip(shape).background(color)
      .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onClick) else Modifier)
      .padding(padding),
    verticalArrangement = Arrangement.spacedBy(gap),
    content = content,
  )
}

@Composable
fun StatusDot(tone: Tone, size: Dp = 9.dp) {
  Box(Modifier.size(size).clip(CircleShape).background(toneDot(tone)))
}

fun toneDot(tone: Tone): Color = when (tone) {
  Tone.GOOD -> P.Good
  Tone.BAD -> P.Bad
  Tone.NEUTRAL -> P.Calm
}

fun toneText(tone: Tone): Color = when (tone) {
  Tone.GOOD -> P.GoodText
  Tone.BAD -> P.Bad
  Tone.NEUTRAL -> P.Muted
}

/** A small rounded tile: label on top, value below. */
@Composable
fun MiniTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, subColor: Color = P.Muted, dot: Color? = null, color: Color = P.Card) {
  Column(
    modifier.clip(RoundedCornerShape(18.dp)).background(color).padding(horizontal = 12.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(label, style = T.Tiny.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
      dot?.let { Box(Modifier.size(7.dp).clip(CircleShape).background(it)) }
    }
    Text(value, style = T.Num20.copy(fontSize = 18.sp))
    sub?.let { Text(it, style = T.Micro.copy(color = subColor)) }
  }
}

/**
 * The Pulse ring: a dark track, a glowing gradient arc with a round cap and a
 * white dot at its tip, and a domed inner disc with a dotted inner circle.
 * [fraction] 0..1 of [sweep] degrees starting at [startAngle] (0° = 3 o'clock).
 */
@Composable
fun GlowRing(
  fraction: Float,
  colors: List<Color>,
  size: Dp,
  stroke: Dp,
  modifier: Modifier = Modifier,
  startAngle: Float = -90f,
  sweep: Float = 360f,
  trackColor: Color = P.Track,
  discTop: Color = Color(0xFF3A3D47),
  discBottom: Color = Color(0xFF202228),
  marker: Float? = null,
  description: String,
  /** Sweep in from 0 when the ring appears; [content] receives progress 0..1 to count numbers up in step. */
  animate: Boolean = true,
  content: @Composable BoxScope.(progress: Float) -> Unit,
) {
  val target = fraction.coerceIn(0f, 1f)
  val shown = if (animate) rememberEntrance(target) else target
  val progress = if (target <= 0f) 1f else (shown / target).coerceIn(0f, 1f)
  Box(modifier.size(size).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
    Canvas(Modifier.size(size)) {
      val sw = stroke.toPx()
      val r = this.size.minDimension / 2 - sw / 2 - 4.dp.toPx()
      val c = center
      val arcTopLeft = Offset(c.x - r, c.y - r)
      val arcSize = Size(r * 2, r * 2)
      val f = shown
      val swept = sweep * f
      // track
      drawArc(trackColor, startAngle, sweep, false, arcTopLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
      if (f > 0.002f) {
        // glow
        glowArc(lerp(colors.first(), colors.last(), 0.5f).copy(alpha = 0.6f), startAngle, swept, arcTopLeft, arcSize, sw)
        // gradient arc: rotate so the sweep gradient's 0° lines up with the arc start
        rotate(startAngle, c) {
          val brush = Brush.sweepGradient(0f to colors.first(), (swept / 360f).coerceAtLeast(0.01f) to colors.last(), 1f to colors.last(), center = c)
          drawArc(brush, 0f, swept, false, arcTopLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        }
      }
      // inner disc
      val discR = r - sw / 2 - 2.dp.toPx()
      drawCircle(Brush.radialGradient(listOf(discTop, discBottom), center = Offset(c.x, c.y - discR * 0.4f), radius = discR * 1.4f), discR, c)
      drawCircle(Color.White.copy(alpha = 0.07f), discR, c, style = Stroke(1.dp.toPx()))
      drawCircle(
        Color.White.copy(alpha = 0.12f), discR * 0.87f, c,
        style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.6f, 7f))),
      )
      marker?.let { m -> // a ring marker (e.g. calendar age on the age gauge)
        val a = Math.toRadians((startAngle + sweep * m.coerceIn(0f, 1f)).toDouble())
        val p = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
        drawCircle(P.Ground, 3.4.dp.toPx(), p)
        drawCircle(colors.last(), 3.4.dp.toPx(), p, style = Stroke(1.6.dp.toPx()))
      }
      if (f > 0.002f) {
        val a = Math.toRadians((startAngle + swept).toDouble())
        drawCircle(Color.White, sw * 0.27f, Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()))
      }
    }
    content(progress)
  }
}

/**
 * A value that animates from 0 to [target] when it first appears (each time a
 * screen opens), and glides to any new target after that — e.g. after a sync.
 */
@Composable
fun rememberEntrance(target: Float, durationMs: Int = 1300, delayMs: Int = 0): Float {
  val anim = remember { Animatable(0f) }
  LaunchedEffect(target) { anim.animateTo(target, tween(durationMs, delayMs, FastOutSlowInEasing)) }
  return anim.value
}

/**
 * Count a displayed number up with [progress]: "6,822" → "3,411" at 0.5, "47.9"
 * keeps one decimal, "+0.2" keeps its sign. Text that isn't a number is returned as is.
 */
fun countUp(text: String, progress: Float, from: Double = 0.0): String {
  if (progress >= 1f) return text
  val m = Regex("^([+−-]?)([\\d,]+)(\\.(\\d+))?$").matchEntire(text) ?: return text
  val sign = m.groupValues[1]
  val decimals = m.groupValues[4].length
  val value = (m.groupValues[2].replace(",", "") + (if (decimals > 0) "." + m.groupValues[4] else "")).toDoubleOrNull() ?: return text
  val v = from + (value - from) * progress
  val body = if (decimals == 0) java.text.NumberFormat.getIntegerInstance().format(kotlin.math.round(v).toLong())
  else String.format(java.util.Locale.getDefault(), "%.${decimals}f", v)
  return sign + body
}

private fun DrawScope.glowArc(color: Color, start: Float, sweep: Float, topLeft: Offset, size: Size, width: Float) {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return // blur needs hardware support from Android 9
  drawIntoCanvas { canvas ->
    val paint = Paint()
    paint.asFrameworkPaint().apply {
      isAntiAlias = true
      style = android.graphics.Paint.Style.STROKE
      strokeWidth = width
      strokeCap = android.graphics.Paint.Cap.ROUND
      this.color = color.toArgb()
      maskFilter = BlurMaskFilter(width * 0.7f, BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawArc(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height, start, sweep, false, paint)
  }
}

/** Big number with a small trailing unit, used inside rings. */
@Composable
fun RingValue(value: String, unit: String, valueSize: Int, unitSize: Int = (valueSize * 0.45).toInt(), unitColor: Color = P.Soft) {
  Row(verticalAlignment = Alignment.Top) {
    Text(value, style = T.Display.copy(fontSize = valueSize.sp, lineHeight = valueSize.sp))
    if (unit.isNotEmpty()) Text(unit, style = T.Label.copy(fontSize = unitSize.sp, fontWeight = FontWeight.Bold, color = unitColor), modifier = Modifier.padding(top = (valueSize * 0.2).dp))
  }
}

/** A row of [count] small segments, the first [filled] coloured: used for steps and zone minutes. */
@Composable
fun SegmentBar(fraction: Float, count: Int, height: Dp, modifier: Modifier = Modifier, hot: Color = P.StepHot, warm: Color = P.StepWarm, description: String) {
  val shown = rememberEntrance(fraction.coerceIn(0f, 1f))
  val total = (fraction.coerceIn(0f, 1f) * count).toInt()
  val filled = (shown * count).toInt()
  Row(modifier.height(height).semantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
    repeat(count) { i ->
      val c = when {
        i >= filled -> P.Empty
        i < total / 2 -> hot
        else -> warm
      }
      Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c))
    }
  }
}

/**
 * Compact floating pill navigation: centred and sized to its items. The active
 * item expands into a green pill with its label; the others are icon-only.
 */
data class NavItem(val label: String, val icon: ImageVector)

@Composable
fun PulseNav(items: List<NavItem>, selected: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
  val shape = RoundedCornerShape(999.dp)
  Row(
    modifier
      .shadow(28.dp, shape, ambientColor = Color.Black.copy(alpha = 0.45f), spotColor = Color.Black.copy(alpha = 0.45f))
      .clip(shape).background(P.Card).border(1.dp, P.Divider, shape)
      .padding(6.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    items.forEachIndexed { i, item ->
      val on = i == selected
      Row(
        Modifier.height(44.dp).widthIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
          .then(if (on) Modifier.background(P.NavActive) else Modifier)
          .clickable(role = Role.Tab, onClickLabel = item.label) { onSelect(i) }
          .semantics { contentDescription = item.label }
          .padding(start = if (on) 14.dp else 0.dp, end = if (on) 18.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
      ) {
        Icon(item.icon, contentDescription = null, tint = if (on) P.NavActiveInk else P.Faint, modifier = Modifier.size(20.dp))
        if (on) Text(item.label, style = T.Body14.copy(fontWeight = FontWeight.Bold, color = P.NavActiveInk))
      }
    }
  }
}

/** Two values side by side separated by a hairline, e.g. Wind down | Target bedtime. */
@Composable
fun SplitStat(items: List<Pair<String, String>>, valueColors: List<Color> = emptyList()) {
  Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(P.Card).padding(vertical = 14.dp)) {
    items.forEachIndexed { i, (label, value) ->
      if (i > 0) Box(Modifier.size(width = 1.dp, height = 44.dp).background(P.Divider).align(Alignment.CenterVertically))
      Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = T.Num24.copy(color = valueColors.getOrNull(i) ?: P.Text))
        Text(label, style = T.Small)
      }
    }
  }
}

/** Gradient primary button (Sync now) and a quiet secondary one. */
@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
  Box(
    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(18.dp))
      .background(if (enabled) P.Primary else Brush.horizontalGradient(listOf(P.Inner, P.Inner)))
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) { Text(text, style = T.Title.copy(fontWeight = FontWeight.ExtraBold, color = if (enabled) P.PrimaryInk else P.Muted)) }
}

@Composable
fun SecondaryButton(text: String, color: Color = P.Text, onClick: () -> Unit) {
  Box(
    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(18.dp)).background(P.Inner).clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) { Text(text, style = T.Label.copy(fontWeight = FontWeight.Bold, color = color)) }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (RowScope.() -> Unit)? = null) {
  Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(text, style = T.H2.copy(fontSize = 20.sp), modifier = Modifier.weight(1f).semantics { heading() })
    trailing?.invoke(this)
  }
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

/** "‹ Yesterday · Sun 11 Oct ›" in a quiet pill, for Heart and Fitness. */
@Composable
fun DayPicker(label: String, canGoForward: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.05f)),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Previous day", onClick = onPrev).semantics { contentDescription = "Previous day" },
      contentAlignment = Alignment.Center,
    ) { Icon(LumenIcons.Prev, contentDescription = null, tint = P.Text, modifier = Modifier.size(16.dp)) }
    Text(label, style = T.Label, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
    Box(
      Modifier.size(44.dp).clip(CircleShape).clickable(enabled = canGoForward, role = Role.Button, onClickLabel = "Next day", onClick = onNext).semantics { contentDescription = "Next day" },
      contentAlignment = Alignment.Center,
    ) { Icon(LumenIcons.Next, contentDescription = null, tint = if (canGoForward) P.Text else P.Calm, modifier = Modifier.size(16.dp)) }
  }
}
