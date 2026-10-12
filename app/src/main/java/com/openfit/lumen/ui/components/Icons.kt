package com.openfit.lumen.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Stroke icons from the design (24×24 grid), tinted by the Icon composable. */
object LumenIcons {
  private fun stroke(name: String, width: Float = 1.8f, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
      paths.forEach { d ->
        addPath(
          PathParser().parsePathString(d).toNodes(),
          fill = null,
          stroke = SolidColor(Color.Black),
          strokeLineWidth = width,
          strokeLineCap = StrokeCap.Round,
          strokeLineJoin = StrokeJoin.Round,
        )
      }
    }.build()

  val Today = stroke("today", 1.8f, "M4 11l8-7 8 7v9h-5v-6H9v6H4z")
  val Sleep = stroke("sleep", 1.8f, "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")
  val Heart = stroke("heart", 1.8f, "M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z")
  val Activity = stroke("activity", 1.8f, "M3 12h4l3-7 4 14 3-7h4")
  val Trends = stroke("trends", 1.8f, "M4 4v16h16M8 15l4-4 3 3 5-6")
  val Sliders = stroke(
    "sliders", 1.8f,
    "M4 7h10M18 7h2M4 17h4M12 17h8",
    "M14 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
    "M8 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
  )
  val Back = stroke("back", 2f, "M15 5l-7 7 7 7")
  val Prev = stroke("prev", 2f, "M15 5l-7 7 7 7")
  val Next = stroke("next", 2f, "M9 5l7 7-7 7")
  val Shield = stroke("shield", 1.8f, "M12 3l8 4v5c0 4.5-3.4 8.3-8 9-4.6-.7-8-4.5-8-9V7z", "M8.5 12l2.5 2.5 4.5-5")
  val Bolt = stroke("bolt", 2.2f, "M13 3L4 14h7l-1 7 9-11h-7z")
  val Sparkle = stroke("sparkle", 2f, "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z")
  val CheckCircle = stroke("check-circle", 2.2f, "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M8 12.5l2.5 2.5 5-5.5")
  val Alert = stroke("alert", 2.2f, "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M12 7.5v5.5", "M12 16.5v.01")
  val ChevronRight = stroke("chevron-right", 2f, "M9 6l6 6-6 6")
  val Runner = stroke("runner", 2.2f, "M11.2 4.5a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0", "M9 21l2.5-6.5L14 16v5M7 12l3-4 4 1 2 3h3")
  val Person = stroke("person", 2f, "M8 8a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M4 20c1.5-3.5 4.5-5 8-5s6.5 1.5 8 5")
  val NavHome = stroke("nav-home", 2.1f, "M4 11l8-7 8 7v9h-5v-6H9v6H4z")
  val NavSleep = stroke("nav-sleep", 2.1f, "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")
  val NavFitness = stroke("nav-fitness", 2.1f, "M3 12h4l3-7 4 14 3-7h4")
  val NavBiology = stroke("nav-biology", 2.1f, "M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z")
  val NavTrends = stroke("nav-trends", 2.1f, "M4 4v16h16M8 15l4-4 3 3 5-6")
  val Sync = stroke("sync", 1.8f, "M20 11a8 8 0 0 0-14.3-4.9L4 8", "M4 4v4h4", "M4 13a8 8 0 0 0 14.3 4.9L20 16", "M20 20v-4h-4")
}
