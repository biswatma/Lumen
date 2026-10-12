package com.openfit.lumen.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * After Lumen is updated, launchers keep showing the widget loading placeholder
 * until something redraws it. This redraws every placed widget from stored data
 * as soon as the update is installed.
 */
class WidgetRefreshReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
      try {
        LumenWidgets.updateAll(context.applicationContext)
      } finally {
        pending.finish()
      }
    }
  }
}
