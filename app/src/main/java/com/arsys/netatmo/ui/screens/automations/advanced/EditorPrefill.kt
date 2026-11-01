package com.arsys.netatmo.ui.screens.automations.advanced

import org.json.JSONArray
import java.util.Calendar

// ── Prefill helpers ───────────────────────────────────────────────────────────
// Pure conversions from a stored trigger/condition/action map to the editor
// sheets' initial field values, so an item that is already configured can be
// edited instead of deleted and recreated. Maps come either straight from the
// "add" sheets (Kotlin types: List<Int>, Int, Long, Double) or from a JSON
// round trip in AdvancedAutomationViewModel (Integer/Long/Double, JSONArray),
// so every reader accepts both shapes.

internal fun mapString(map: Map<String, Any>, vararg keys: String): String {
    for (key in keys) {
        (map[key] as? String)?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return ""
}

internal fun mapNumber(map: Map<String, Any>, vararg keys: String): Number? {
    for (key in keys) {
        (map[key] as? Number)?.let { return it }
        (map[key] as? String)?.toDoubleOrNull()?.let { return it }
    }
    return null
}

internal fun mapIntList(value: Any?): List<Int> = when (value) {
    is List<*> -> value.mapNotNull { (it as? Number)?.toInt() }
    is JSONArray -> (0 until value.length()).mapNotNull {
        val v = value.opt(it)
        (v as? Number)?.toInt()
    }
    else -> emptyList()
}

/** "yyyy-MM-dd" in local time, for the date field of the datetime trigger. */
internal fun msToLocalYmd(ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

/** "HH:mm" in local time, for the time field of the datetime trigger. */
internal fun msToLocalHm(ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

/** Hours as typed in the duration field: "3" for 3.0, "3,5" for 3.5. */
internal fun formatHoursInput(hours: Double): String =
    if (hours == hours.toLong().toDouble()) hours.toLong().toString()
    else hours.toString().replace('.', ',')

// ── Trigger ───────────────────────────────────────────────────────────────────

data class TriggerPrefill(
    val type: String,
    val time: String,
    val days: List<Int>,
    val numericEntity: String,
    val numericCondition: String,
    val threshold: Float,
    val sunEvent: String,
    val sunOffset: String,
    val geofenceEvent: String,
    val geofenceZoneId: String,
    val windowStart: String,
    val windowEnd: String,
    val date: String,
    val dateTime: String,
    val calendarId: Long,
    val calendarName: String,
    val calendarTitle: String,
    val calendarMinutesBefore: String
)

internal fun prefillTrigger(map: Map<String, Any>): TriggerPrefill {
    val type = mapString(map, "type", "platform")
    val at = mapNumber(map, "at")?.toLong() ?: 0L
    val numericEntity = mapString(map, "entity").ifBlank { "outdoor_temp" }
    return TriggerPrefill(
        type = type,
        time = mapString(map, "time").ifBlank {
            val h = mapNumber(map, "hour")?.toInt() ?: 7
            val m = mapNumber(map, "minute")?.toInt() ?: 0
            "%02d:%02d".format(h, m)
        },
        days = mapIntList(map["days"]),
        numericEntity = numericEntity,
        numericCondition = if (map.containsKey("below")) "below" else "above",
        threshold = (mapNumber(map, "below", "above", "value")?.toFloat()
            ?: if (numericEntity == "outdoor_temp") 5f else 20f),
        sunEvent = if (type == "sun") mapString(map, "event").ifBlank { "sunset" } else "sunset",
        sunOffset = (mapNumber(map, "offset")?.toInt() ?: 0).toString(),
        // Zone triggers store enter/exit under "event"; older geofence triggers used "action".
        geofenceEvent = (if (type == "geofence") mapString(map, "event") else "")
            .ifBlank { mapString(map, "action") }.ifBlank { "enter" },
        geofenceZoneId = mapString(map, "zoneId"),
        windowStart = mapString(map, "start").ifBlank { "22:00" },
        windowEnd = mapString(map, "end").ifBlank { "07:00" },
        date = if (at > 0) msToLocalYmd(at) else "",
        dateTime = if (at > 0) msToLocalHm(at) else "12:00",
        calendarId = mapNumber(map, "calendarId")?.toLong() ?: -1L,
        calendarName = mapString(map, "calendarName"),
        calendarTitle = mapString(map, "title"),
        calendarMinutesBefore = (mapNumber(map, "minutesBefore")?.toInt() ?: 30).toString()
    )
}

// ── Condition ─────────────────────────────────────────────────────────────────

data class ConditionPrefill(
    val type: String,
    val fromTime: String,
    val toTime: String,
    val entity: String,
    val condition: String,
    val threshold: Float
)

internal fun prefillCondition(map: Map<String, Any>): ConditionPrefill = ConditionPrefill(
    type = mapString(map, "type"),
    fromTime = mapString(map, "after", "from").ifBlank { "06:00" },
    toTime = mapString(map, "before", "to").ifBlank { "22:00" },
    entity = mapString(map, "entity").ifBlank { "outdoor_temp" },
    condition = if (map.containsKey("below")) "below" else "above",
    threshold = mapNumber(map, "below", "above", "value")?.toFloat() ?: 20f
)

// ── Action ────────────────────────────────────────────────────────────────────

data class ActionPrefill(
    val type: String,
    val temperature: Float,
    val homeId: String,
    val roomId: String,
    val mode: String,
    val durationText: String,
    /** Fixed end kept from before durations existed; preserved while no duration is set. */
    val legacyEndAt: Long,
    val notifyTitle: String,
    val notifyMessage: String,
    val delayMinutes: String,
    val scenarioId: Long,
    val scenarioName: String
)

internal fun prefillAction(map: Map<String, Any>): ActionPrefill {
    val duration = mapNumber(map, "durationHours")?.toDouble() ?: 0.0
    val endAt = mapNumber(map, "endAt")?.toLong() ?: 0L
    return ActionPrefill(
        type = mapString(map, "type"),
        temperature = mapNumber(map, "temperature")?.toFloat() ?: 20f,
        homeId = mapString(map, "homeId"),
        roomId = mapString(map, "roomId"),
        mode = mapString(map, "mode").ifBlank { "schedule" },
        durationText = if (duration > 0.0) formatHoursInput(duration) else "",
        legacyEndAt = if (duration <= 0.0 && endAt > 0L) endAt else 0L,
        notifyTitle = mapString(map, "title"),
        notifyMessage = mapString(map, "message"),
        delayMinutes = (mapNumber(map, "minutes")?.toInt() ?: 5).toString(),
        scenarioId = mapNumber(map, "scenarioId")?.toLong() ?: -1L,
        scenarioName = mapString(map, "scenarioName")
    )
}
