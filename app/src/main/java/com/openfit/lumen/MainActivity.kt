package com.openfit.lumen

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.openfit.lumen.ui.LumenRoot
import com.openfit.lumen.ui.Screen
import com.openfit.lumen.ui.theme.LumenTheme

class MainActivity : ComponentActivity() {
  /** A screen a home-screen widget asked to open; consumed once shown. */
  private val requested = mutableStateOf<Screen?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Pulse is a dark design in every mode: light system-bar icons on a transparent bar.
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
    requested.value = screenFrom(intent)
    setContent { LumenTheme { LumenRoot(requested = requested.value, onRequestHandled = { requested.value = null }) } }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    screenFrom(intent)?.let { requested.value = it }
  }

  private fun screenFrom(intent: Intent?): Screen? =
    intent?.getStringExtra(EXTRA_SCREEN)?.let { name -> Screen.entries.firstOrNull { it.name == name } }

  companion object {
    const val EXTRA_SCREEN = "com.openfit.lumen.SCREEN"
  }
}
