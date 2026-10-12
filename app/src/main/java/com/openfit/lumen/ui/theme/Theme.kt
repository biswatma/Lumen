package com.openfit.lumen.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.openfit.lumen.R

/** Pulse palette: dark, rings-first. Taken from the "Variant B · Pulse" design. */
object P {
  val Ground = Color(0xFF1B1D22)
  val Card = Color(0xFF24272E)
  val Inner = Color(0xFF2C2F37)
  val Track = Color(0xFF2C2F37)
  val Divider = Color(0xFF33363E)
  val Empty = Color(0xFF353841)
  val Plot = Color(0xFF1F2127)
  val Nav = Color(0xFF121418)
  val Text = Color(0xFFF2F3F5)
  val Muted = Color(0xFF9AA0AA)
  val Soft = Color(0xFFC4C8D0)
  val Faint = Color(0xFF8A909A)

  val Good = Color(0xFF4ADE80)
  val GoodText = Color(0xFF6EE7A0)
  val GoodBg = Color(0xFF1F3328)
  val Bad = Color(0xFFFF9B6B)
  val BadBg = Color(0xFF3A2620)
  val Lilac = Color(0xFFB9B2FF)
  val InsightBg = Color(0xFF2A2D44)
  val NightCard = Color(0xFF2C2F46)
  val NightMuted = Color(0xFFA9B0D6)
  val TrendRow = Color(0xFF262942)
  val Spark = Color(0xFF9DA8F5)
  val Avatar = Color(0xFFCFE3EC)
  val AvatarInk = Color(0xFF3B5866)

  // Ring gradients (start → end of the arc)
  val Move = listOf(Color(0xFFF0702E), Color(0xFFFFD966))
  val Recovery = listOf(Color(0xFFE4F75A), Color(0xFF1FB257))
  val Sleep = listOf(Color(0xFF5A48FF), Color(0xFFB4CCFF))
  val Age = listOf(Color(0xFF6EF09A), Color(0xFF4FD1C5))
  val Teal = Color(0xFF4FD1C5)

  val NavActive = Brush.horizontalGradient(listOf(Color(0xFFD4F04A), Color(0xFF4ADE80)))
  val NavActiveInk = Color(0xFF0F1A0C)
  val Primary = Brush.horizontalGradient(listOf(Color(0xFF3FBF5A), Color(0xFFB8E04A)))
  val PrimaryInk = Color(0xFF102010)

  val StepHot = Color(0xFFF7C548)
  val StepWarm = Color(0xFFF0A040)
  val Amber = Color(0xFFF7C548)
  val Orange = Color(0xFFF08A3C)
  val Cardio = Color(0xFFF06A3C)
  val Peak = Color(0xFFE8453C)
  val Calm = Color(0xFF5A6070)
  val QuietHour = Color(0xFF3A3D46)

  val Awake = Color(0xFFFF9B6B)
  val Rem = Color(0xFFB9B2FF)
  val Light = Color(0xFF7C93F5)
  val Deep = Color(0xFF4B3FD1)

  /** Screen backgrounds: a tinted glow at the top fading into the ground. */
  val SleepBg = Brush.verticalGradient(0f to Color(0xFF23264A), 0.3f to Color(0xFF1E2038), 0.55f to Ground)
  val HeartBg = Brush.verticalGradient(0f to Color(0xFF3A1F26), 0.32f to Ground)
  val MoveBg = Brush.verticalGradient(0f to Color(0xFF3A2C18), 0.32f to Ground)
  val AgeBg = Brush.verticalGradient(0f to Color(0xFF17302A), 0.34f to Ground)
  val TrendsBg = Brush.verticalGradient(0f to Color(0xFF1E2038), 0.4f to Ground)
  val PlainBg = Brush.verticalGradient(listOf(Ground, Ground))
}

@OptIn(ExperimentalTextApi::class)
private fun figtree(weight: Int) =
  Font(R.font.figtree, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Figtree = FontFamily(figtree(400), figtree(500), figtree(600), figtree(700), figtree(800))

/** Type scale used across the Pulse screens. */
object T {
  private val base = TextStyle(fontFamily = Figtree, color = P.Text)
  val Display = base.copy(fontSize = 60.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 60.sp, letterSpacing = (-0.03).em)
  val H1 = base.copy(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
  val H2 = base.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold)
  val Header = base.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold)
  val NavTitle = base.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold)
  val Title = base.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold)
  val Label = base.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
  val Body = base.copy(fontSize = 16.sp, lineHeight = 23.sp)
  val Body14 = base.copy(fontSize = 14.sp, lineHeight = 21.sp)
  val Small = base.copy(fontSize = 13.sp, lineHeight = 18.sp, color = P.Muted)
  val Tiny = base.copy(fontSize = 12.sp, lineHeight = 16.sp, color = P.Muted)
  val Micro = base.copy(fontSize = 11.sp, lineHeight = 14.sp, color = P.Muted)
  val Eyebrow = base.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em, color = P.Muted)
  val Big = base.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
  val Num24 = base.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold)
  val Num20 = base.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun LumenTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = darkColorScheme(
      primary = P.Good, background = P.Ground, surface = P.Card, onSurface = P.Text, onBackground = P.Text,
      surfaceContainerHigh = P.Card, onSurfaceVariant = P.Muted,
    ),
    typography = Typography(bodyLarge = T.Body, bodyMedium = T.Body14),
    content = content,
  )
}
