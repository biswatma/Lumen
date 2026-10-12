package com.openfit.lumen

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.openfit.lumen.ui.components.PCard
import com.openfit.lumen.ui.components.PulseScreen
import com.openfit.lumen.ui.theme.P
import com.openfit.lumen.ui.theme.LumenTheme
import com.openfit.lumen.ui.theme.T

/** Lumen's privacy policy, opened from the Health Connect permission screen. */
class PermissionsRationaleActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
    setContent {
      LumenTheme {
        PulseScreen(P.PlainBg) {
          Text("How Lumen uses your health data", style = T.H1, modifier = Modifier.padding(top = 16.dp))
          POLICY.forEach { (title, body) ->
            PCard(gap = 6.dp) {
              Text(title, style = T.Label.copy(fontWeight = FontWeight.Bold))
              Text(body, style = T.Body14.copy(color = P.Soft))
            }
          }
        }
      }
    }
  }

  private companion object {
    val POLICY = listOf(
      "What Lumen reads" to "Steps, distance, calories, exercise, heart rate, resting heart rate, heart rate variability, sleep, blood oxygen, breathing rate, skin temperature, cardio fitness (VO₂ max), weight and height — only records written to Health Connect by the Google Health app.",
      "Why" to "To show your daily rings and coaching, sleep, heart and fitness screens, trends against your own normal ranges, and an estimated biological age.",
      "Where it goes" to "Nowhere. Lumen copies readings into a database inside the app on this phone. It has no internet permission, sends nothing to any server, and excludes its data from cloud backup.",
      "Writing" to "Lumen never writes, edits or deletes anything in Health Connect or Google Health.",
      "Removing your data" to "Disconnect on Lumen's Data source screen deletes every stored reading and gives back Lumen's Health Connect access. Uninstalling Lumen also deletes everything it stored.",
    )
  }
}
