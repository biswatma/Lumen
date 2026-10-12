package com.openfit.lumen.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import com.openfit.lumen.data.hc.HealthConnectSource
import com.openfit.lumen.ui.components.GlowRing
import com.openfit.lumen.ui.components.LumenIcons
import com.openfit.lumen.ui.components.PCard
import com.openfit.lumen.ui.components.PrimaryButton
import com.openfit.lumen.ui.components.PulseScreen
import com.openfit.lumen.ui.theme.P
import com.openfit.lumen.ui.theme.T

@Composable
fun ConnectScreen(sdkStatus: Int, message: String?, onConnect: () -> Unit) {
  val context = LocalContext.current
  PulseScreen(P.SleepBg, gap = 18.dp) {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy((-18).dp), verticalAlignment = Alignment.CenterVertically) {
      GlowRing(0.72f, P.Move, 92.dp, 9.dp, description = "") {}
      GlowRing(0.86f, P.Recovery, 92.dp, 9.dp, description = "") {}
      GlowRing(0.92f, P.Sleep, 92.dp, 9.dp, description = "") {}
    }
    Text("Lumen", style = T.Display.copy(fontSize = 44.sp, lineHeight = 48.sp))
    Text("Rings, coaching and a biological age estimate from your Google Health data — worked out on this phone.", style = T.Body.copy(fontSize = 17.sp, lineHeight = 25.sp, color = P.Soft))

    PCard(gap = 14.dp) {
      Point("No sign-in, no internet", "Lumen reads what Google Health shares with Health Connect. It has no internet permission.")
      Point("Read-only, one source", "Only data written by the Google Health app. Other apps are ignored, and nothing is written back.")
      Point("Stays on this phone", "Readings are copied to a local database, excluded from backup, and erased when you disconnect.")
    }
    message?.let { PCard(color = P.BadBg) { Text(it, style = T.Body14.copy(color = P.Bad)) } }

    when (sdkStatus) {
      HealthConnectClient.SDK_AVAILABLE -> PrimaryButton("Connect to Health Connect", onClick = onConnect)
      HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
        PCard { Text("Health Connect needs to be installed or updated before Lumen can read your data.", style = T.Body14.copy(color = P.Soft)) }
        PrimaryButton("Get Health Connect") {
          val uri = "market://details?id=${HealthConnectSource.PROVIDER}&url=healthconnect%3A%2F%2Fonboarding".toUri()
          runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending")) }
        }
      }
      else -> PCard(color = P.BadBg) {
        Text("Health Connect isn't available on this phone, so Lumen can't read Google Health data here. It needs Android 9 or newer with Google Play.", style = T.Body14.copy(color = P.Bad))
      }
    }
  }
}

@Composable
private fun Point(title: String, body: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
    Box(Modifier.padding(top = 2.dp)) { Icon(LumenIcons.CheckCircle, contentDescription = null, tint = P.GoodText, modifier = Modifier.size(20.dp)) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = T.Label.copy(fontWeight = FontWeight.Bold))
      Text(body, style = T.Small.copy(color = Color(0xFFB4B9C2)))
    }
  }
}
