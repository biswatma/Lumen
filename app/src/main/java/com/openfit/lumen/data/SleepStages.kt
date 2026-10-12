package com.openfit.lumen.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** Sleep stages as Lumen shows them, and reading them back from a stored sleep row's JSON. */
object SleepStages {
  enum class Stage { AWAKE, REM, LIGHT, DEEP }

  data class StageSpan(val stage: Stage, val startMs: Long, val endMs: Long) {
    val minutes: Double get() = (endMs - startMs) / 60_000.0
  }

  val json = Json { ignoreUnknownKeys = true }

  /** Reads the `stages` array written by the Health Connect mapper: `[{"stage","startMs","endMs"}]`. */
  fun fromStored(raw: String): List<StageSpan> {
    val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyList()
    val stages = obj["stages"] as? JsonArray ?: return emptyList()
    return stages.mapNotNull { el ->
      val s = el as? JsonObject ?: return@mapNotNull null
      val stage = (s["stage"] as? JsonPrimitive)?.content?.let { name -> Stage.entries.firstOrNull { it.name == name } } ?: return@mapNotNull null
      val start = (s["startMs"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
      val end = (s["endMs"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
      StageSpan(stage, start, end)
    }.sortedBy { it.startMs }
  }
}
