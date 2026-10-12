package com.openfit.lumen.widget

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Widgets can't draw custom graphics, so the Pulse rings, segment bars and
 * charts are drawn here into bitmaps (software canvas, so the glow blur works
 * on every Android version) and shown as images.
 */
object WidgetArt {
  // Pulse colours as ARGB ints
  const val TRACK = 0xFF2C2F37.toInt()
  const val EMPTY = 0xFF353841.toInt()
  const val HOT = 0xFFF7C548.toInt()
  const val WARM = 0xFFF0A040.toInt()
  val MOVE = intArrayOf(0xFFF0702E.toInt(), 0xFFFFD966.toInt())
  val RECOVERY = intArrayOf(0xFFE4F75A.toInt(), 0xFF1FB257.toInt())
  val SLEEP = intArrayOf(0xFF5A48FF.toInt(), 0xFFB4CCFF.toInt())
  /** Deep, REM, light, awake. */
  val STAGES = intArrayOf(0xFF4B3FD1.toInt(), 0xFFB9B2FF.toInt(), 0xFF7C93F5.toInt(), 0xFFFF9B6B.toInt())

  /** A glowing gradient progress ring with a domed centre disc and a white tip dot. */
  fun ring(sizePx: Int, fraction: Float, colors: IntArray, strokeRatio: Float = 0.1f): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val stroke = sizePx * strokeRatio
    val pad = stroke / 2 + sizePx * 0.05f
    val oval = RectF(pad, pad, sizePx - pad, sizePx - pad)
    val cx = sizePx / 2f
    val r = oval.width() / 2
    val f = fraction.coerceIn(0f, 1f)
    val sweep = 360f * f

    val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; color = TRACK }
    c.drawArc(oval, -90f, 360f, false, track)

    if (f > 0.003f) {
      val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
        color = blend(colors[0], colors[1], 0.5f); alpha = 150
        maskFilter = BlurMaskFilter(stroke * 0.7f, BlurMaskFilter.Blur.NORMAL)
      }
      c.drawArc(oval, -90f, sweep, false, glow)
      val shader = SweepGradient(cx, cx, intArrayOf(colors[0], colors[1], colors[1]), floatArrayOf(0f, (sweep / 360f).coerceAtLeast(0.01f), 1f))
      shader.setLocalMatrix(Matrix().apply { setRotate(-90f, cx, cx) })
      val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND; this.shader = shader }
      c.drawArc(oval, -90f, sweep, false, arc)
    }

    val discR = r - stroke / 2 - sizePx * 0.015f
    val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = RadialGradient(cx, cx - discR * 0.4f, discR * 1.4f, 0xFF3A3D47.toInt(), 0xFF202228.toInt(), Shader.TileMode.CLAMP)
    }
    c.drawCircle(cx, cx, discR, disc)

    if (f > 0.003f) {
      val a = Math.toRadians((-90f + sweep).toDouble())
      val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
      c.drawCircle(cx + r * cos(a).toFloat(), cx + r * sin(a).toFloat(), stroke * 0.27f, dot)
    }
    return bmp
  }

  /** A row of [count] small rounded segments, the first part filled yellow → amber. */
  fun segments(widthPx: Int, heightPx: Int, fraction: Float, count: Int): Bitmap {
    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val gap = heightPx * 0.18f
    val w = (widthPx - gap * (count - 1)) / count
    val filled = (fraction.coerceIn(0f, 1f) * count).roundToInt()
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    repeat(count) { i ->
      p.color = when {
        i >= filled -> EMPTY
        i < filled * 0.55f -> HOT
        else -> WARM
      }
      val x = i * (w + gap)
      c.drawRoundRect(RectF(x, 0f, x + w, heightPx.toFloat()), w * 0.3f, w * 0.3f, p)
    }
    return bmp
  }

  /** A rounded progress bar with a gradient fill. */
  fun progress(widthPx: Int, heightPx: Int, fraction: Float, from: Int, to: Int): Bitmap {
    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val r = heightPx / 2f
    c.drawRoundRect(RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = EMPTY })
    val w = widthPx * fraction.coerceIn(0f, 1f)
    if (w > 0) {
      val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, widthPx.toFloat(), 0f, from, to, Shader.TileMode.CLAMP) }
      c.drawRoundRect(RectF(0f, 0f, w.coerceAtLeast(heightPx.toFloat()), heightPx.toFloat()), r, r, fill)
    }
    return bmp
  }

  /** Sleep stages as one rounded bar split by share of the night. */
  fun stageBar(widthPx: Int, heightPx: Int, shares: List<Float>): Bitmap {
    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val r = heightPx / 2f
    val clip = android.graphics.Path().apply { addRoundRect(RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()), r, r, android.graphics.Path.Direction.CW) }
    c.clipPath(clip)
    if (shares.isEmpty()) {
      c.drawColor(EMPTY)
      return bmp
    }
    var x = 0f
    val p = Paint()
    shares.forEachIndexed { i, s ->
      val w = widthPx * s
      p.color = STAGES[i]
      c.drawRect(x, 0f, x + w, heightPx.toFloat(), p)
      x += w
    }
    return bmp
  }

  /** Seven day bars with the dashed goal line: green on goal days, amber below. */
  fun week(widthPx: Int, heightPx: Int, values: List<Double?>, goal: Double): Bitmap {
    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val top = maxOf(goal * 1.12, (values.filterNotNull().maxOrNull() ?: 0.0) * 1.05)
    val gap = widthPx * 0.035f
    val w = (widthPx - gap * (values.size - 1)) / values.size
    val r = w * 0.22f
    values.forEachIndexed { i, v ->
      val x = i * (w + gap)
      if (v == null || v <= 0) {
        c.drawRoundRect(RectF(x, heightPx - heightPx * 0.04f, x + w, heightPx.toFloat()), r / 2, r / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = EMPTY })
        return@forEachIndexed
      }
      val h = (heightPx * (v / top)).toFloat().coerceAtLeast(heightPx * 0.04f)
      val hit = v >= goal
      val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
          0f, heightPx - h, 0f, heightPx.toFloat(),
          if (hit) 0xFF8EF5B5.toInt() else 0xFFFFD27A.toInt(),
          if (hit) 0xFF3FBF6A.toInt() else 0xFFF08A3C.toInt(),
          Shader.TileMode.CLAMP,
        )
      }
      c.drawRoundRect(RectF(x, heightPx - h, x + w, heightPx.toFloat()), r, r, p)
    }
    val y = (heightPx * (1 - goal / top)).toFloat()
    val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0x996EE7A0.toInt(); style = Paint.Style.STROKE; strokeWidth = heightPx * 0.02f
      pathEffect = android.graphics.DashPathEffect(floatArrayOf(heightPx * 0.06f, heightPx * 0.04f), 0f)
    }
    c.drawLine(0f, y, widthPx.toFloat(), y, dash)
    return bmp
  }

  private fun blend(a: Int, b: Int, t: Float): Int {
    fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).roundToInt()
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
  }
}
