package com.openfit.lumen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openfit.lumen.data.ConnectionInfo
import com.openfit.lumen.data.HealthType
import com.openfit.lumen.data.HealthTypes
import com.openfit.lumen.data.db.SyncStateEntity
import com.openfit.lumen.data.db.TypeCount
import com.openfit.lumen.data.hc.HealthConnectSource
import com.openfit.lumen.data.hc.HeartZones
import com.openfit.lumen.data.sync.SyncEngine
import com.openfit.lumen.data.sync.SyncStatus
import com.openfit.lumen.ui.components.DetailHeader
import com.openfit.lumen.ui.components.LumenIcons
import com.openfit.lumen.ui.components.PCard
import com.openfit.lumen.ui.components.PrimaryButton
import com.openfit.lumen.ui.components.PulseScreen
import com.openfit.lumen.ui.components.SecondaryButton
import com.openfit.lumen.ui.theme.P
import com.openfit.lumen.ui.theme.T

private data class ReadGroup(val group: String, val items: String, val types: List<HealthType>, val color: Color)

private val GROUPS = listOf(
  ReadGroup("Activity", "Steps, distance, calories, exercise sessions",
    listOf(HealthTypes.STEPS, HealthTypes.DISTANCE, HealthTypes.ACTIVE_CALORIES, HealthTypes.TOTAL_CALORIES, HealthTypes.EXERCISE), P.StepHot),
  ReadGroup("Heart rate", "Through the day and resting; zone minutes are worked out from these", listOf(HealthTypes.HEART_RATE, HealthTypes.RESTING_HR), P.Cardio),
  ReadGroup("Heart rate variability", "Nightly HRV (RMSSD)", listOf(HealthTypes.HRV), P.Good),
  ReadGroup("Cardio fitness", "VO₂ max estimate — for biological age", listOf(HealthTypes.VO2_MAX), P.Teal),
  ReadGroup("Sleep", "Sessions and stages", listOf(HealthTypes.SLEEP), P.Spark),
  ReadGroup("Overnight vitals", "Blood oxygen, breathing rate, skin temperature", listOf(HealthTypes.SPO2, HealthTypes.BREATHING, HealthTypes.SKIN_TEMP), P.Lilac),
  ReadGroup("Body", "Weight and height — for BMI", listOf(HealthTypes.WEIGHT, HealthTypes.HEIGHT), P.Avatar),
)

@Composable
fun SourceScreen(
  connection: ConnectionInfo,
  status: SyncStatus,
  states: List<SyncStateEntity>,
  counts: List<TypeCount>,
  allowedTypes: Set<String>,
  backgroundAllowed: Boolean,
  historyAllowed: Boolean,
  canAskMore: Boolean,
  lastSyncText: String,
  message: String?,
  onBack: () -> Unit,
  onSync: () -> Unit,
  onAllowAccess: () -> Unit,
  onManagePermissions: () -> Unit,
  onAge: (Int) -> Unit,
  onDisconnect: () -> Unit,
) {
  var confirm by remember { mutableStateOf(false) }
  val countByType = counts.associate { it.type to it.count }
  val stateByType = states.associateBy { it.type }

  PulseScreen(P.PlainBg) {
    DetailHeader("Profile & data", onBack)

    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Box(Modifier.size(76.dp).clip(CircleShape).background(P.Avatar), contentAlignment = Alignment.Center) {
        Icon(LumenIcons.Person, contentDescription = null, tint = P.AvatarInk, modifier = Modifier.size(36.dp))
      }
      Text(connection.deviceName ?: "Your Google Health data", style = T.H2.copy(fontSize = 20.sp))
      Text("Synced ${lastSyncText.replaceFirstChar { it.lowercase() }}", style = T.Body14.copy(color = P.Muted))
    }

    PCard {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Google Health", style = T.H2.copy(fontSize = 18.sp), modifier = Modifier.weight(1f))
        val ok = !connection.accessOff
        Row(
          Modifier.clip(RoundedCornerShape(999.dp)).background(if (ok) P.GoodBg else P.BadBg).padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Box(Modifier.size(7.dp).clip(CircleShape).background(if (ok) P.Good else P.Bad))
          Text(if (ok) "Connected" else "Access off", style = T.Small.copy(fontWeight = FontWeight.Bold, color = if (ok) P.GoodText else P.Bad))
        }
      }
      Text(
        "The only data source, read through Health Connect on this phone. Read-only — nothing is written back, and every score (Recovery, Sleep, Move, biological age) is computed here. Lumen has no internet access.",
        style = T.Body14.copy(color = P.Soft),
      )
    }

    PCard(gap = 0.dp) {
      AgeRow(connection.age, onAge)
      Divider()
      InfoRow("Background sync", if (backgroundAllowed) "Every 6 hours" else "Off · when you open Lumen")
      Divider()
      InfoRow("History", if (historyAllowed) "Up to 180 days" else "Last 30 days")
      if (status is SyncStatus.Running) {
        Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          LinearProgressIndicator(progress = { status.done / status.total.toFloat() }, modifier = Modifier.fillMaxWidth(), color = P.Good, trackColor = P.Inner)
          Text("Reading ${status.step}…", style = T.Tiny)
        }
      }
    }

    message?.let { PCard(color = P.BadBg) { Text(it, style = T.Body14.copy(color = P.Bad)) } }

    Text("DATA WE READ", style = T.Eyebrow.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 4.dp, top = 6.dp))
    PCard(gap = 0.dp) {
      GROUPS.forEachIndexed { i, g ->
        if (i > 0) Divider()
        val ids = g.types.map { it.id } + if (HealthTypes.HEART_RATE in g.types) listOf(HealthTypes.ACTIVE_ZONE_MINUTES.id) else emptyList()
        val stored = ids.sumOf { countByType[it] ?: 0 }
        val allowed = g.types.count { it.id in allowedTypes }
        val errors = g.types.mapNotNull { t -> stateByType[t.id]?.lastError?.takeIf { it != SyncEngine.NOT_ALLOWED }?.let { "${t.id}: $it" } }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Box(Modifier.size(10.dp).clip(CircleShape).background(g.color))
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(g.group, style = T.Label)
            Text(g.items, style = T.Tiny)
            Text("${"%,d".format(stored)} readings on this phone", style = T.Tiny.copy(color = P.Soft))
            errors.forEach { Text(it, style = T.Tiny.copy(color = P.Bad)) }
          }
          val (label, color) = when (allowed) {
            g.types.size -> "READ" to P.Muted
            0 -> "OFF" to P.Bad
            else -> "PARTLY" to P.StepHot
          }
          Text(label, style = T.Tiny.copy(fontWeight = FontWeight.Bold, color = color))
        }
      }
    }
    Text(
      buildString {
        append("Data appears only if the Google Health app shares it: Settings › Health Connect › App permissions › Google Health. Expected source: ${HealthConnectSource.GOOGLE_HEALTH_PACKAGE}.")
        if (connection.ignoredSources.isNotEmpty()) append(" Also in Health Connect and ignored: ${connection.ignoredSources.sorted().joinToString(", ")}.")
      },
      style = T.Tiny.copy(lineHeight = 18.sp),
      modifier = Modifier.padding(horizontal = 4.dp),
    )

    if (connection.accessOff) PrimaryButton("Allow access in Health Connect", onClick = onAllowAccess)
    else PrimaryButton(if (status is SyncStatus.Running) "Syncing…" else "Sync now", enabled = status !is SyncStatus.Running, onClick = onSync)
    if (canAskMore && !connection.accessOff) SecondaryButton("Allow more data types", P.GoodText, onAllowAccess)
    SecondaryButton("Manage permissions", P.Text, onManagePermissions)
    SecondaryButton("Disconnect and erase local data", P.Bad) { confirm = true }
  }

  if (confirm) {
    AlertDialog(
      onDismissRequest = { confirm = false },
      title = { Text("Disconnect?", style = T.H2.copy(fontSize = 20.sp)) },
      text = { Text("Lumen will erase every reading stored on this phone and give back its Health Connect access. Nothing in Health Connect or Google Health is deleted.", style = T.Body14.copy(color = P.Soft)) },
      confirmButton = { TextButton(onClick = { confirm = false; onDisconnect() }) { Text("Disconnect", style = T.Label.copy(color = P.Bad, fontWeight = FontWeight.Bold)) } },
      dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel", style = T.Label) } },
      containerColor = P.Card,
    )
  }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(P.Divider))

@Composable
private fun InfoRow(label: String, value: String) {
  Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = T.Body14.copy(color = P.Muted), modifier = Modifier.weight(1f))
    Text(value, style = T.Body14.copy(fontWeight = FontWeight.SemiBold))
  }
}

@Composable
private fun AgeRow(age: Int?, onAge: (Int) -> Unit) {
  val shown = age ?: HeartZones.DEFAULT_AGE
  Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Text("Age", style = T.Body14.copy(color = P.Muted))
      Text(if (age == null) "Not set · needed for biological age" else "For zones and biological age", style = T.Tiny.copy(color = if (age == null) P.Bad else P.Faint))
    }
    StepButton("−", "Decrease age") { onAge(shown - 1) }
    Text(shown.toString(), style = T.Num20, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 44.dp).semantics { contentDescription = "Age $shown" })
    StepButton("+", "Increase age") { onAge(shown + 1) }
  }
}

@Composable
private fun StepButton(symbol: String, label: String, onClick: () -> Unit) {
  Box(
    Modifier.size(44.dp).clip(CircleShape).border(1.dp, P.Divider, CircleShape).clickable(role = Role.Button, onClickLabel = label, onClick = onClick).semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
  ) { Text(symbol, style = T.H2.copy(fontSize = 20.sp)) }
}
