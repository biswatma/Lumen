package com.openfit.lumen.ui

import android.app.Application
import android.text.format.DateFormat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openfit.lumen.LumenApp
import com.openfit.lumen.data.HealthTypes
import com.openfit.lumen.data.sync.SyncWorker
import com.openfit.lumen.insights.ActivityModel
import com.openfit.lumen.insights.Clock24
import com.openfit.lumen.insights.HeartModel
import com.openfit.lumen.insights.Insights
import com.openfit.lumen.insights.SleepModel
import com.openfit.lumen.insights.TodayModel
import com.openfit.lumen.insights.TrendRange
import com.openfit.lumen.insights.TrendsModel
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Loading wrapper so screens can tell "still computing" from "no data". */
sealed interface Load<out T> {
  data object Loading : Load<Nothing>
  data class Ready<T>(val value: T) : Load<T>
}

@OptIn(ExperimentalCoroutinesApi::class)
class LumenViewModel(application: Application) : AndroidViewModel(application) {
  private val app = application as LumenApp
  private val dao = app.database.healthDao()
  private val hc = app.healthConnect
  private val insights = Insights(dao, Clock24(DateFormat.is24HourFormat(application)), age = { app.settings.info.value.age })

  val connection = app.settings.info
  val syncStatus = app.sync.status
  val syncStates = dao.observeStates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
  val counts = dao.observeCounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  /** Re-emits whenever stored data or the zone age changes, so every screen recomputes. */
  private val dataVersion: Flow<Any> = combine(
    dao.observeCounts(),
    dao.observeStates().map { s -> s.maxOfOrNull { it.lastAttemptMs } },
    connection.map { it.age }.distinctUntilChanged(),
  ) { c, attempt, age -> Triple(c, attempt, age) }

  private val _heartDay = MutableStateFlow(insights.today().minusDays(1))
  /** Fitness opens on today's steps; Trends opens on the last 7 days. */
  private val _activityDay = MutableStateFlow(insights.today())
  private val _trendRange = MutableStateFlow(TrendRange.D7)
  val trendRange: StateFlow<TrendRange> = _trendRange.asStateFlow()

  val today: StateFlow<Load<TodayModel>> = dataVersion.mapLatest { Load.Ready(insights.todayModel()) }.asLoad()
  val sleep: StateFlow<Load<SleepModel?>> = dataVersion.mapLatest { Load.Ready(insights.sleepModel()) }.asLoad()
  val heart: StateFlow<Load<HeartModel>> = combine(dataVersion, _heartDay) { _, d -> d }.mapLatest { Load.Ready(insights.heartModel(it)) }.asLoad()
  val activity: StateFlow<Load<ActivityModel>> = combine(dataVersion, _activityDay) { _, d -> d }.mapLatest { Load.Ready(insights.activityModel(it)) }.asLoad()
  val trends: StateFlow<Load<TrendsModel>> = combine(dataVersion, _trendRange) { _, r -> r }.mapLatest { Load.Ready(insights.trendsModel(it)) }.asLoad()

  private fun <T> Flow<Load<T>>.asLoad(): StateFlow<Load<T>> =
    flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Loading)

  // ---- Health Connect access ----

  private val _sdkStatus = MutableStateFlow(hc.sdkStatus())
  /** HealthConnectClient.SDK_AVAILABLE, SDK_UNAVAILABLE or SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED. */
  val sdkStatus: StateFlow<Int> = _sdkStatus.asStateFlow()

  private val _granted = MutableStateFlow<Set<String>>(emptySet())

  /** Ids of the data types Lumen currently has read permission for. */
  val allowedTypes: StateFlow<Set<String>> = _granted
    .map { g -> HealthTypes.READ.filter { hc.permissionFor(it) in g }.map { it.id }.toSet() }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

  /** True when some data types Lumen could read haven't been allowed (e.g. types added in an update). */
  val canAskMore: StateFlow<Boolean> = _granted
    .map { g -> g.isNotEmpty() && !g.containsAll(runCatching { hc.requestedPermissions() }.getOrDefault(emptySet())) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

  /** Background reads and full history are separate Health Connect permissions. */
  val backgroundAllowed: StateFlow<Boolean> = _granted
    .map { HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in it }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
  val historyAllowed: StateFlow<Boolean> = _granted
    .map { HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in it }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

  private val _message = MutableStateFlow<String?>(null)
  val message: StateFlow<String?> = _message.asStateFlow()

  /** Permissions to ask Health Connect for. Only valid when [sdkStatus] is available. */
  fun permissionsToRequest(): Set<String> = hc.requestedPermissions()

  /** Re-check Health Connect on every resume: the person may have installed it or changed permissions. */
  fun refreshAccess() = viewModelScope.launch {
    _sdkStatus.value = hc.sdkStatus()
    if (_sdkStatus.value != HealthConnectClient.SDK_AVAILABLE) return@launch
    val granted = runCatching { hc.grantedPermissions() }.getOrDefault(emptySet())
    _granted.value = granted
    if (connection.value.connected) {
      val anyRead = HealthTypes.READ.any { hc.permissionFor(it) in granted }
      app.settings.update { it.copy(accessOff = !anyRead) }
      if (anyRead) syncIfStale(nothingStored = dao.state(HealthTypes.STEPS.id) == null)
    }
  }

  fun onPermissionsResult(granted: Set<String>) {
    _granted.value = granted
    if (HealthTypes.READ.none { hc.permissionFor(it) in granted }) {
      _message.value = "Lumen needs access to at least one type of data. Tap Connect and choose what to share."
      return
    }
    _message.value = null
    app.settings.update { it.copy(connected = true, accessOff = false) }
    SyncWorker.schedule(app)
    syncNow()
  }

  // ---- sync ----

  fun syncNow() = viewModelScope.launch {
    if (!app.sync.syncAll()) _message.value = "Lumen can't read Health Connect. Allow access under Manage permissions."
    else _message.value = null
  }

  /** Today's steps three ways, to explain a difference from the Google Health app. */
  data class StepsCheck(val googleHealth: Long?, val allApps: Long?, val stored: Long, val newestRecord: String?)

  private val _stepsCheck = MutableStateFlow<StepsCheck?>(null)
  val stepsCheck: StateFlow<StepsCheck?> = _stepsCheck.asStateFlow()

  fun checkSteps() = viewModelScope.launch {
    val today = java.time.LocalDate.now().toString()
    val stored = dao.dailySums(HealthTypes.STEPS.id, today, today).sumOf { it.total }.toLong()
    val clock = Clock24(DateFormat.is24HourFormat(getApplication()))
    _stepsCheck.value = StepsCheck(
      googleHealth = runCatching { hc.googleHealthStepsToday() }.getOrNull(),
      allApps = runCatching { hc.allAppsStepsToday() }.getOrNull(),
      stored = stored,
      newestRecord = runCatching { hc.newestStepsRecordEnd() }.getOrNull()?.let { clock.time(it.toEpochMilli()) },
    )
  }

  private val _refreshing = MutableStateFlow(false)
  /** True while a pull-to-refresh sync is running. */
  val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

  /**
   * Pull to refresh: read the latest Google Health data from Health Connect.
   * Waits for any sync already running, and keeps the spinner up for at least
   * a moment so a fast sync doesn't flicker.
   */
  fun refresh() {
    if (_refreshing.value) return
    _refreshing.value = true
    viewModelScope.launch {
      val started = System.currentTimeMillis()
      try {
        _granted.value = runCatching { hc.grantedPermissions() }.getOrDefault(_granted.value)
        if (!app.sync.syncAll(waitIfRunning = true)) _message.value = "Lumen can't read Health Connect. Allow access under Manage permissions."
        else _message.value = null
      } finally {
        val elapsed = System.currentTimeMillis() - started
        if (elapsed < 700) delay(700 - elapsed)
        _refreshing.value = false
      }
    }
  }

  /** Sync on app open if the last sync is older than 15 minutes, or nothing is stored yet. */
  private fun syncIfStale(nothingStored: Boolean) {
    val last = connection.value.lastSyncMs ?: 0L
    if (nothingStored || System.currentTimeMillis() - last > 15 * 60_000L) syncNow()
  }

  private var zonesJob: Job? = null

  /** Save the age used for zones, then re-derive zone minutes (only the latest change is applied). */
  fun setAge(age: Int) {
    app.settings.update { it.copy(age = age.coerceIn(13, 100)) }
    zonesJob?.cancel()
    zonesJob = viewModelScope.launch {
      delay(800)
      app.sync.recomputeZones()
    }
  }

  fun disconnect() = viewModelScope.launch {
    _message.value = null
    _granted.value = emptySet()
    app.disconnect()
  }

  // ---- screen controls ----

  fun shiftHeartDay(days: Long) = shift(_heartDay, days)

  fun shiftActivityDay(days: Long) = shift(_activityDay, days)

  private fun shift(flow: MutableStateFlow<LocalDate>, days: Long) {
    val next = flow.value.plusDays(days)
    if (next <= insights.today()) flow.value = next
  }

  fun setTrendRange(r: TrendRange) {
    _trendRange.value = r
  }
}
