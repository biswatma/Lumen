package com.openfit.lumen.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConnectionInfo(
  val connected: Boolean = false,
  /** Health Connect access was turned off (all permissions revoked) after connecting. */
  val accessOff: Boolean = false,
  val deviceName: String? = null,
  val lastSyncMs: Long? = null,
  /** Age used to estimate heart-rate zones; null until the person sets it. */
  val age: Int? = null,
  /** Other apps with data in Health Connect, which Lumen ignores. */
  val ignoredSources: Set<String> = emptySet(),
)

/** Small key-value state kept in app-private SharedPreferences. */
class Settings(context: Context) {
  private val prefs = context.getSharedPreferences("lumen", Context.MODE_PRIVATE)
  private val _info = MutableStateFlow(read())
  val info: StateFlow<ConnectionInfo> = _info.asStateFlow()

  private fun read() = ConnectionInfo(
    connected = prefs.getBoolean(K_CONNECTED, false),
    accessOff = prefs.getBoolean(K_ACCESS_OFF, false),
    deviceName = prefs.getString(K_DEVICE, null),
    lastSyncMs = prefs.getLong(K_LAST_SYNC, 0L).takeIf { it > 0 },
    age = prefs.getInt(K_AGE, 0).takeIf { it > 0 },
    ignoredSources = prefs.getStringSet(K_IGNORED, emptySet()).orEmpty(),
  )

  fun update(block: (ConnectionInfo) -> ConnectionInfo) = synchronized(this) {
    val next = block(read())
    prefs.edit {
      putBoolean(K_CONNECTED, next.connected)
      putBoolean(K_ACCESS_OFF, next.accessOff)
      putString(K_DEVICE, next.deviceName)
      putLong(K_LAST_SYNC, next.lastSyncMs ?: 0L)
      putInt(K_AGE, next.age ?: 0)
      putStringSet(K_IGNORED, next.ignoredSources)
    }
    _info.value = next
  }

  fun clear() = synchronized(this) {
    prefs.edit { clear() }
    _info.value = ConnectionInfo()
  }

  private companion object {
    const val K_CONNECTED = "connected"
    const val K_ACCESS_OFF = "access_off"
    const val K_DEVICE = "device"
    const val K_LAST_SYNC = "last_sync"
    const val K_AGE = "age"
    const val K_IGNORED = "ignored_sources"
  }
}
