package com.openfit.lumen.ui

import android.content.Intent
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openfit.lumen.data.ConnectionInfo
import com.openfit.lumen.data.sync.SyncStatus
import com.openfit.lumen.insights.Clock24
import com.openfit.lumen.insights.Tab
import com.openfit.lumen.ui.components.LumenIcons
import com.openfit.lumen.ui.components.NavItem
import com.openfit.lumen.ui.components.PulseNav
import com.openfit.lumen.ui.screens.BiologyScreen
import com.openfit.lumen.ui.screens.ConnectScreen
import com.openfit.lumen.ui.screens.HeartScreen
import com.openfit.lumen.ui.screens.MoveScreen
import com.openfit.lumen.ui.screens.SleepScreen
import com.openfit.lumen.ui.screens.SourceScreen
import com.openfit.lumen.ui.screens.TodayScreen
import com.openfit.lumen.ui.screens.TrendsScreen
import com.openfit.lumen.ui.theme.P
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Every place in the app. The first five are the pill-nav tabs; Heart and Source open from Today. */
enum class Screen { HOME, SLEEP, FITNESS, BIOLOGY, TRENDS, HEART, SOURCE }

private val NAV = listOf(
  NavItem("Home", LumenIcons.NavHome),
  NavItem("Sleep", LumenIcons.NavSleep),
  NavItem("Fitness", LumenIcons.NavFitness),
  NavItem("Biology", LumenIcons.NavBiology),
  NavItem("Trends", LumenIcons.NavTrends),
)

private fun Tab.toScreen() = when (this) {
  Tab.TODAY -> Screen.HOME
  Tab.SLEEP -> Screen.SLEEP
  Tab.HEART -> Screen.HEART
  Tab.ACTIVITY -> Screen.FITNESS
  Tab.TRENDS -> Screen.TRENDS
}

@Composable
fun LumenRoot(requested: Screen? = null, onRequestHandled: () -> Unit = {}, vm: LumenViewModel = viewModel()) {
  val connection by vm.connection.collectAsStateWithLifecycle()
  val sdkStatus by vm.sdkStatus.collectAsStateWithLifecycle()
  val message by vm.message.collectAsStateWithLifecycle()

  // Health Connect's own permission screen; the result is the set of permissions granted.
  val requestPermissions = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
    vm.onPermissionsResult(granted)
  }
  val ask = { requestPermissions.launch(vm.permissionsToRequest()) }

  // The person may install Health Connect or change permissions in Settings and come back.
  LifecycleResumeEffect(Unit) {
    vm.refreshAccess()
    onPauseOrDispose {}
  }

  Box(Modifier.fillMaxSize().background(P.Ground)) {
    if (!connection.connected) ConnectScreen(sdkStatus, message, onConnect = ask)
    else ConnectedApp(vm, connection, message, onAllowAccess = ask, requested = requested, onRequestHandled = onRequestHandled)
  }
}

@Composable
private fun ConnectedApp(
  vm: LumenViewModel,
  connection: ConnectionInfo,
  message: String?,
  onAllowAccess: () -> Unit,
  requested: Screen?,
  onRequestHandled: () -> Unit,
) {
  var stack by rememberSaveable { mutableStateOf(listOf(Screen.HOME)) }
  val screen = stack.last()
  fun go(s: Screen) {
    // Tabs reset the stack to Home → tab; detail screens push on top.
    stack = if (s.ordinal <= Screen.TRENDS.ordinal) listOf(Screen.HOME, s).distinct() else stack + s
  }
  fun back() {
    stack = if (stack.size > 1) stack.dropLast(1) else listOf(Screen.HOME)
  }
  BackHandler(enabled = stack.size > 1) { back() }

  // A widget tap asks for a screen (e.g. Sleep): open it once, on top of Home.
  LaunchedEffect(requested) {
    requested?.let { go(it); onRequestHandled() }
  }

  val status by vm.syncStatus.collectAsStateWithLifecycle()
  val refreshing by vm.refreshing.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val clock = Clock24(DateFormat.is24HourFormat(context))
  val lastSync = lastSyncText(connection, status, clock)

  Box(Modifier.fillMaxSize()) {
    when (screen) {
      Screen.HOME -> Loaded(vm.today.collectAsStateWithLifecycle().value) {
        TodayScreen(
          model = it,
          sourceLine = listOfNotNull("Google Health", connection.deviceName).joinToString(" · "),
          syncLine = when {
            connection.accessOff -> "access off"
            status is SyncStatus.Running -> "syncing…"
            else -> "synced ${lastSync.replaceFirstChar { c -> c.lowercase() }}"
          },
          accessOff = connection.accessOff,
          onOpen = { t -> go(t.toScreen()) },
          onOpenBiology = { go(Screen.BIOLOGY) },
          onOpenSource = { go(Screen.SOURCE) },
          refreshing = refreshing,
          onRefresh = vm::refresh,
        )
      }
      Screen.SLEEP -> Loaded(vm.sleep.collectAsStateWithLifecycle().value) { SleepScreen(it, onBack = ::back) }
      Screen.HEART -> Loaded(vm.heart.collectAsStateWithLifecycle().value) {
        HeartScreen(it, onBack = ::back, onPrev = { vm.shiftHeartDay(-1) }, onNext = { vm.shiftHeartDay(1) }, onOpenBiology = { go(Screen.BIOLOGY) })
      }
      Screen.FITNESS -> Loaded(vm.activity.collectAsStateWithLifecycle().value) {
        MoveScreen(it, onBack = ::back, onPrev = { vm.shiftActivityDay(-1) }, onNext = { vm.shiftActivityDay(1) })
      }
      Screen.BIOLOGY -> Loaded(vm.today.collectAsStateWithLifecycle().value) {
        BiologyScreen(it.bio, ageSet = connection.age != null, onBack = ::back, onOpen = { t -> go(t.toScreen()) }, onSetAge = { go(Screen.SOURCE) })
      }
      Screen.TRENDS -> {
        val range by vm.trendRange.collectAsStateWithLifecycle()
        val trends = vm.trends.collectAsStateWithLifecycle().value
        TrendsScreen(range, (trends as? Load.Ready)?.value?.takeIf { it.range == range }, vm::setTrendRange)
      }
      Screen.SOURCE -> {
        val states by vm.syncStates.collectAsStateWithLifecycle()
        val counts by vm.counts.collectAsStateWithLifecycle()
        val allowed by vm.allowedTypes.collectAsStateWithLifecycle()
        val background by vm.backgroundAllowed.collectAsStateWithLifecycle()
        val history by vm.historyAllowed.collectAsStateWithLifecycle()
        val canAskMore by vm.canAskMore.collectAsStateWithLifecycle()
        SourceScreen(
          connection, status, states, counts, allowed, background, history, canAskMore, lastSync, message,
          onBack = ::back,
          onSync = vm::syncNow,
          onAllowAccess = onAllowAccess,
          onManagePermissions = { runCatching { context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) } },
          onAge = vm::setAge,
          onDisconnect = vm::disconnect,
        )
      }
    }

    if (screen != Screen.SOURCE) {
      // Fade the content out behind the floating nav and the system navigation bar.
      Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(88.dp).background(Brush.verticalGradient(listOf(Color.Transparent, P.Ground.copy(alpha = 0.92f)))))
        Box(Modifier.fillMaxWidth().background(P.Ground.copy(alpha = 0.92f)).windowInsetsBottomHeight(WindowInsets.navigationBars))
      }
      PulseNav(
        items = NAV,
        selected = screen.ordinal.takeIf { it <= Screen.TRENDS.ordinal },
        onSelect = { i -> go(Screen.entries[i]) },
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
      )
    }
  }
}

@Composable
private fun <T> Loaded(state: Load<T>, content: @Composable (T) -> Unit) {
  when (state) {
    Load.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = P.Good) }
    is Load.Ready -> content(state.value)
  }
}

private fun lastSyncText(connection: ConnectionInfo, status: SyncStatus, clock: Clock24): String {
  if (status is SyncStatus.Running) return "Syncing…"
  val ms = connection.lastSyncMs ?: return "Never"
  val day = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
  val today = LocalDate.now()
  val prefix = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> day.toString()
  }
  return "$prefix ${clock.time(ms)}"
}
