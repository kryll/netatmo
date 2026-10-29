package com.arsys.netatmo.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.arsys.netatmo.NetatmoApp
import com.arsys.netatmo.data.local.entities.AdvancedAutomationEntity
import com.arsys.netatmo.data.local.entities.AutomationLogEntity
import com.arsys.netatmo.data.repository.AdvancedAutomationRepository
import com.arsys.netatmo.data.repository.ApiResult
import com.arsys.netatmo.data.repository.AutomationRepository
import com.arsys.netatmo.data.repository.ThermostatRepository
import android.os.Build
import android.provider.CalendarContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

private data class RoomReading(val homeId: String, val roomId: String, val name: String, val temp: Double)

/** Temperature that triggered a "below X" condition, plus the room the quick buttons act on. */
private data class TemperatureAlert(
    val threshold: Double,
    val measuredLabel: String,
    val measured: Double,
    val room: RoomReading?
)

@Singleton
class AdvancedAutomationEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val advancedAutomationRepository: AdvancedAutomationRepository,
    private val thermostatRepository: ThermostatRepository,
    private val automationRepository: AutomationRepository,
    private val familyPresenceManager: FamilyPresenceManager
) {
    private companion object {
        const val STATE_PREFS = "advanced_automation_state"
        // Avoids re-running when a value hovers around its threshold.
        const val MIN_REPEAT_MS = 60L * 60 * 1000
        val BOOST_STEPS = listOf(1.0, 1.5, 2.0)
        const val MIN_SETPOINT = 7.0
        const val MAX_SETPOINT = 30.0
        // The periodic worker runs about every 15 min, so a time trigger counts as "now" for this long.
        const val TIME_TRIGGER_WINDOW_MIN = 20
    }

    private val statePrefs by lazy { context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE) }

    suspend fun evaluateAndExecuteAll() {
        val automations = advancedAutomationRepository.getEnabledAdvancedAutomations()
        automations.forEach { automation ->
            evaluateAndExecute(automation)
        }
    }

    /**
     * An automation runs when one of its triggers fires and its conditions hold. Without triggers
     * it runs when its conditions start being met. Never on every periodic check.
     */
    suspend fun evaluateAndExecute(automation: AdvancedAutomationEntity) {
        try {
            val triggers = JSONArray(automation.triggersJson)
            var firedTrigger: JSONObject? = null
            val shouldRun: Boolean
            if (triggers.length() == 0) {
                val metKey = "met_${automation.id}"
                val conditionsMet = evaluateConditions(automation)
                val wasMet = statePrefs.getBoolean(metKey, false)
                statePrefs.edit().putBoolean(metKey, conditionsMet).apply()
                shouldRun = conditionsMet && !wasMet
            } else {
                firedTrigger = findFiringTrigger(automation.id, triggers)
                shouldRun = firedTrigger != null && evaluateConditions(automation)
            }
            if (!shouldRun) return
            // A time window re-fires on every check while inside it, like the classic schedule did.
            val repeatMs = if (firedTrigger?.optString("type") == "time_window") 0L else MIN_REPEAT_MS
            runFired(automation, firedTrigger, repeatMs)
        } catch (e: Exception) {
            recordFailure(automation, e)
        }
    }

    /**
     * Called when the phone arrives at or leaves a saved zone. Runs every enabled automation that
     * has a zone trigger for that zone and event, if its conditions hold. The geofence state store
     * already ignores repeated or flapping transitions, so no hourly debounce is applied here.
     */
    suspend fun onZoneTransition(zoneId: String, entered: Boolean) {
        val event = if (entered) "enter" else "exit"
        advancedAutomationRepository.getEnabledAdvancedAutomations().forEach { automation ->
            try {
                val triggers = JSONArray(automation.triggersJson)
                val matching = (0 until triggers.length())
                    .map { triggers.getJSONObject(it) }
                    .firstOrNull {
                        it.optString("type") == "geofence" &&
                            it.optString("zoneId") == zoneId &&
                            it.optString("event", "enter") == event
                    } ?: return@forEach
                if (!evaluateConditions(automation)) return@forEach
                runFired(automation, matching, 0L)
            } catch (e: Exception) {
                recordFailure(automation, e)
            }
        }
    }

    private suspend fun runFired(automation: AdvancedAutomationEntity, firedTrigger: JSONObject?, minRepeatMs: Long) {
        val firedKey = "fired_${automation.id}"
        val now = System.currentTimeMillis()
        if (minRepeatMs > 0 && now - statePrefs.getLong(firedKey, 0L) < minRepeatMs) return
        statePrefs.edit().putLong(firedKey, now).apply()
        try {
            val alert = buildTemperatureAlert(automation, firedTrigger)
            executeActions(automation, alert)
            advancedAutomationRepository.updateLastTriggered(automation.id, now, "success")
            logExecution(automation, now, success = true, error = null)
        } catch (e: Exception) {
            recordFailure(automation, e)
        }
    }

    private suspend fun recordFailure(automation: AdvancedAutomationEntity, e: Exception) {
        val firedKey = "fired_${automation.id}"
        val now = System.currentTimeMillis()
        advancedAutomationRepository.updateLastTriggered(automation.id, now, "error")
        logExecution(automation, now, success = false, error = e.message ?: e.javaClass.simpleName)
        // Forget the consumed trigger so the next check tries again instead of waiting for a new crossing.
        val editor = statePrefs.edit().remove(firedKey)
        statePrefs.all.keys
            .filter {
                it.startsWith("tact_${automation.id}_") || it.startsWith("tday_${automation.id}_") ||
                    it.startsWith("tdt_${automation.id}_") || it.startsWith("tcal_${automation.id}_")
            }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    /**
     * Runs [automation] right now, ignoring the "already fired" state, and returns a plain-text
     * report of what it read and did. Used by the "Probar ahora" button.
     */
    suspend fun testRun(automation: AdvancedAutomationEntity): String {
        val report = StringBuilder()
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            report.appendLine("AVISO: las notificaciones de la app están desactivadas en Android (Ajustes del móvil > Aplicaciones > Netatmo Smart > Notificaciones). Sin eso no verás ningún aviso.")
        } else if (manager.getNotificationChannel(NetatmoApp.CHANNEL_ADVANCED)?.importance == android.app.NotificationManager.IMPORTANCE_NONE) {
            report.appendLine("AVISO: el canal \"Automatizaciones avanzadas\" está desactivado en los ajustes de notificaciones de Android.")
        }
        try {
            val triggers = JSONArray(automation.triggersJson)
            var triggerActive = triggers.length() == 0
            var activeTrigger: JSONObject? = null
            if (triggers.length() == 0) report.appendLine("Sin disparadores: se evalúan solo las condiciones.")
            for (i in 0 until triggers.length()) {
                val trigger = triggers.getJSONObject(i)
                when (trigger.optString("type")) {
                    "numeric_state" -> {
                        val (active, reading) = describeNumericState(trigger)
                        report.appendLine("Disparador ${i + 1} (${ruleText(trigger)}): $reading -> " +
                            when (active) { true -> "se cumple"; false -> "no se cumple"; null -> "no se pudo leer" })
                        if (active == true && activeTrigger == null) { triggerActive = true; activeTrigger = trigger }
                    }
                    "time" -> report.appendLine("Disparador ${i + 1}: a las ${trigger.optString("time")} (se evalúa a su hora, no se puede forzar aquí)")
                    "time_window" -> {
                        val inside = timeWindowActive(trigger)
                        report.appendLine("Disparador ${i + 1}: franja ${trigger.optString("start")}-${trigger.optString("end")} -> " +
                            (if (inside) "ahora dentro" else "ahora fuera"))
                        if (inside && activeTrigger == null) { triggerActive = true; activeTrigger = trigger }
                    }
                    "calendar" -> {
                        val next = queryUpcomingEvents(
                            trigger.optLong("calendarId", -1L),
                            trigger.optString("title").ifBlank { null },
                            System.currentTimeMillis(), 7L * 24 * 60 * 60 * 1000
                        ).firstOrNull()
                        report.appendLine("Disparador ${i + 1}: calendario «${trigger.optString("calendarName")}»" +
                            (trigger.optString("title").ifBlank { "" }.let { if (it.isEmpty()) "" else " con «$it»" }) +
                            ", ${trigger.optInt("minutesBefore", 30)} min antes: " +
                            (next?.let { "próximo evento «${it.title}» el ${formatDateTime(it.startMs)}" } ?: "sin eventos próximos en 7 días") +
                            " (se evalúa cuando toca, no se puede forzar aquí)")
                    }
                    "datetime" -> {
                        val at = trigger.optLong("at", 0L)
                        val consumed = statePrefs.getBoolean("tdt_${automation.id}_$i", false)
                        report.appendLine("Disparador ${i + 1}: el ${formatDateTime(at)}" +
                            (if (consumed) " (ya se ejecutó)" else " (se evalúa a su hora, no se puede forzar aquí)"))
                    }
                    "geofence" -> report.appendLine("Disparador ${i + 1}: ${if (trigger.optString("event") == "exit") "al salir de" else "al llegar a"} la zona \"${trigger.optString("zone")}\" (se evalúa cuando el móvil entra o sale, no se puede forzar aquí)")
                    else -> report.appendLine("Disparador ${i + 1}: tipo \"${trigger.optString("type")}\" todavía no se evalúa")
                }
            }
            val conditionsOk = evaluateConditions(automation)
            val conditions = JSONArray(automation.conditionsJson)
            report.appendLine(if (conditions.length() == 0) "Sin condiciones adicionales." else "Condiciones: " + if (conditionsOk) "se cumplen." else "NO se cumplen.")
            if (!triggerActive) { report.appendLine("Resultado: no se ejecuta, ningún disparador se cumple ahora."); return report.toString().trim() }
            if (!conditionsOk) { report.appendLine("Resultado: no se ejecuta, las condiciones no se cumplen."); return report.toString().trim() }

            val alert = buildTemperatureAlert(automation, activeTrigger)
            val postedIds = executeActions(automation, alert)
            val actionCount = JSONArray(automation.actionsJson).length()
            report.appendLine(if (actionCount == 1) "Resultado: 1 acción ejecutada." else "Resultado: $actionCount acciones ejecutadas.")
            if (postedIds.isNotEmpty()) {
                report.append(describeNotificationDelivery(postedIds))
            }
            if (alert == null && JSONArray(automation.actionsJson).let { a -> (0 until a.length()).any { a.getJSONObject(it).optString("type") == "notify" } }) {
                report.appendLine("La notificación salió sin botones: no hay un disparador de \"temperatura por debajo de\" con lectura disponible.")
            }
        } catch (e: Exception) {
            report.appendLine("ERROR: ${e.message ?: e.javaClass.simpleName}")
        }
        return report.toString().trim()
    }

    private fun ruleText(rule: JSONObject): String {
        val entity = if (rule.optString("entity") == "outdoor_temp") "temp. exterior" else "temp. interior"
        return when {
            rule.has("below") -> "$entity por debajo de ${formatTemp(rule.getDouble("below"))}°C"
            rule.has("above") -> "$entity por encima de ${formatTemp(rule.getDouble("above"))}°C"
            else -> entity
        }
    }

    private suspend fun describeNumericState(rule: JSONObject): Pair<Boolean?, String> {
        val active = numericStateActive(rule)
        val reading = when (rule.optString("entity")) {
            "outdoor_temp" -> {
                val homeId = resolveHomeId(rule.optString("home_id", ""))
                val temp = if (homeId.isEmpty()) null else thermostatRepository.getOutdoorTemperature(homeId)
                temp?.let { "exterior ${formatTemp(it)}°C" } ?: "sin lectura exterior"
            }
            else -> {
                val rooms = readIndoorRooms()
                if (rooms.isEmpty()) "sin lecturas de habitaciones (¿sesión de Netatmo caducada?)"
                else rooms.joinToString(", ") { "${it.name} ${formatTemp(it.temp)}°C" }
            }
        }
        return active to reading
    }

    // The list screen shows advanced automations with a negative id (-id) to tell them apart.
    private suspend fun logExecution(automation: AdvancedAutomationEntity, ts: Long, success: Boolean, error: String?) {
        automationRepository.logExecution(
            AutomationLogEntity(
                automationId = -automation.id,
                automationName = automation.name,
                triggerType = "ADVANCED",
                timestamp = ts,
                success = success,
                errorMessage = error
            )
        )
    }

    private suspend fun evaluateConditions(automation: AdvancedAutomationEntity): Boolean {
        val conditions = JSONArray(automation.conditionsJson)
        if (conditions.length() == 0) return true

        val results = mutableListOf<Boolean>()
        for (i in 0 until conditions.length()) {
            val condition = conditions.getJSONObject(i)
            results.add(evaluateCondition(condition))
        }

        return if (automation.triggerMode == "any") {
            results.any { it }
        } else {
            results.all { it }
        }
    }

    private suspend fun evaluateCondition(condition: JSONObject): Boolean {
        return when (condition.optString("type")) {
            "time_range" -> {
                val after = condition.optString("after", "00:00")
                val before = condition.optString("before", "23:59")
                val cal = Calendar.getInstance()
                val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                val afterMinutes = parseTimeToMinutes(after)
                val beforeMinutes = parseTimeToMinutes(before)
                if (afterMinutes <= beforeMinutes) {
                    currentMinutes in afterMinutes..beforeMinutes
                } else {
                    currentMinutes >= afterMinutes || currentMinutes <= beforeMinutes
                }
            }
            // A value that cannot be read counts as "not met", otherwise a failed request
            // would fire the automation.
            "numeric_state" -> numericStateActive(condition) ?: false
            "presence" -> {
                val state = condition.optString("state", "someone_home")
                val allAway = familyPresenceManager.allMembersAway()
                when (state) {
                    "everyone_away" -> allAway
                    else -> !allAway // "someone_home" and unrecognised states default to at-home check
                }
            }
            else -> true
        }
    }

    /** Whether a temperature rule holds right now; null when the value cannot be read. */
    private suspend fun numericStateActive(rule: JSONObject): Boolean? = when (rule.optString("entity")) {
        "outdoor_temp" -> {
            val homeId = resolveHomeId(rule.optString("home_id", ""))
            val temp = if (homeId.isEmpty()) null else thermostatRepository.getOutdoorTemperature(homeId)
            temp?.let { checkNumericCondition(rule, it) }
        }
        "indoor_temp" -> {
            val rooms = readIndoorRooms()
            // "below" holds if any room is below; "above" if any room is above.
            if (rooms.isEmpty()) null else rooms.any { checkNumericCondition(rule, it.temp) }
        }
        else -> null
    }

    /**
     * Returns the first trigger that fires now. Time triggers fire once per day inside a short
     * window after their time; temperature triggers fire when the value crosses the threshold.
     * Every trigger is evaluated so its stored state stays current.
     */
    private suspend fun findFiringTrigger(automationId: Long, triggers: JSONArray): JSONObject? {
        var fired: JSONObject? = null
        for (i in 0 until triggers.length()) {
            val trigger = triggers.getJSONObject(i)
            val fires = when (trigger.optString("type")) {
                "time" -> timeTriggerFires(automationId, i, trigger)
                "numeric_state" -> {
                    val active = numericStateActive(trigger)
                    val key = "tact_${automationId}_$i"
                    val wasActive = statePrefs.getBoolean(key, false)
                    if (active != null) statePrefs.edit().putBoolean(key, active).apply()
                    active == true && !wasActive
                }
                "time_window" -> timeWindowActive(trigger)
                "calendar" -> calendarTriggerFires(automationId, i, trigger)
                "datetime" -> datetimeTriggerFires(automationId, i, trigger)
                else -> false // "sun" is not evaluated yet; "geofence" runs from onZoneTransition
            }
            if (fires && fired == null) fired = trigger
        }
        return fired
    }

    private fun timeTriggerFires(automationId: Long, index: Int, trigger: JSONObject): Boolean {
        val target = try { parseTimeToMinutes(trigger.optString("time", "")) } catch (e: Exception) { return false }
        val days = trigger.optJSONArray("days")?.let { arr -> (0 until arr.length()).map { arr.getInt(it) }.toSet() }
        val cal = Calendar.getInstance()
        val isoDay = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1 // Calendar: 1=Sun -> ISO: 7
        if (days != null && isoDay !in days) return false
        val elapsed = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE) - target
        if (elapsed !in 0 until TIME_TRIGGER_WINDOW_MIN) return false
        val dayKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
        val key = "tday_${automationId}_$index"
        if (statePrefs.getString(key, null) == dayKey) return false
        statePrefs.edit().putString(key, dayKey).apply()
        return true
    }

    /**
     * Whether now is inside the window (days + start/end). While inside, the trigger fires on
     * every periodic check, matching the classic schedule automation's re-application.
     * Overnight windows (start > end) wrap past midnight.
     */
    private fun timeWindowActive(trigger: JSONObject): Boolean {
        val days = trigger.optJSONArray("days")?.let { arr -> (0 until arr.length()).map { arr.getInt(it) }.toSet() }
        val cal = Calendar.getInstance()
        val isoDay = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = try { parseTimeToMinutes(trigger.optString("start", "00:00")) } catch (e: Exception) { return false }
        val end = try { parseTimeToMinutes(trigger.optString("end", "23:59")) } catch (e: Exception) { return false }
        return AutomationTriggerMath.isInsideWindow(isoDay, nowMinutes, days, start, end)
    }

    /**
     * Fires once per matching calendar event, [minutesBefore] before it starts. The periodic
     * worker polls every ~15 min; a consumed event is remembered so it cannot fire twice.
     */
    private fun calendarTriggerFires(automationId: Long, index: Int, trigger: JSONObject): Boolean {
        val calendarId = trigger.optLong("calendarId", -1L)
        if (calendarId < 0) return false
        val minutesBefore = trigger.optInt("minutesBefore", 30).coerceAtLeast(0)
        val now = System.currentTimeMillis()
        val events = queryUpcomingEvents(
            calendarId,
            trigger.optString("title").ifBlank { null },
            now,
            (minutesBefore + 15) * 60_000L
        )
        var fires = false
        for (event in events) {
            if (AutomationTriggerMath.calendarShouldFire(event.startMs, minutesBefore, now)) {
                val key = "tcal_${automationId}_${index}_${event.startMs}"
                if (!statePrefs.getBoolean(key, false)) {
                    statePrefs.edit().putBoolean(key, true).apply()
                    fires = true
                }
            }
        }
        return fires
    }

    /** One-shot trigger for a fixed instant; fires on the first check at or after [at]. */
    private fun datetimeTriggerFires(automationId: Long, index: Int, trigger: JSONObject): Boolean {
        val at = trigger.optLong("at", 0L)
        val key = "tdt_${automationId}_$index"
        if (!AutomationTriggerMath.datetimeShouldFire(at, statePrefs.getBoolean(key, false), System.currentTimeMillis())) return false
        statePrefs.edit().putBoolean(key, true).apply()
        return true
    }

    private data class CalendarEventReading(val title: String, val startMs: Long)

    private fun queryUpcomingEvents(calendarId: Long, titleFilter: String?, nowMs: Long, withinMs: Long): List<CalendarEventReading> {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return emptyList()
        val events = mutableListOf<CalendarEventReading>()
        val projection = arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART)
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DTSTART} BETWEEN ? AND ?"
        val args = arrayOf(calendarId.toString(), nowMs.toString(), (nowMs + withinMs).toString())
        try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI, projection, selection, args,
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val title = cursor.getString(0) ?: ""
                    if (titleFilter == null || title.contains(titleFilter, ignoreCase = true)) {
                        events.add(CalendarEventReading(title, cursor.getLong(1)))
                    }
                }
            }
        } catch (e: SecurityException) {
            // Calendar permission revoked meanwhile.
        }
        return events
    }

    private fun formatDateTime(ms: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        return "%02d/%02d %02d:%02d".format(
            cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)
        )
    }

    private suspend fun resolveHomeId(homeIdFromCondition: String): String {
        if (homeIdFromCondition.isNotEmpty()) return homeIdFromCondition
        val result = thermostatRepository.getHomesData()
        return if (result is ApiResult.Success) result.data.firstOrNull()?.id ?: "" else ""
    }

    private suspend fun readIndoorRooms(): List<RoomReading> {
        val homesResult = thermostatRepository.getHomesData()
        if (homesResult !is ApiResult.Success) return emptyList()
        val readings = mutableListOf<RoomReading>()
        for (home in homesResult.data) {
            val status = thermostatRepository.getHomeStatus(home.id)
            if (status !is ApiResult.Success) continue
            val names = home.rooms.orEmpty().associate { it.id to it.name }
            status.data.rooms.orEmpty().forEach { room ->
                val temp = room.measuredTemperature ?: return@forEach
                readings.add(RoomReading(home.id, room.id, names[room.id] ?: "Habitación", temp))
            }
        }
        return readings
    }

    private fun parseTimeToMinutes(hhmm: String): Int {
        val parts = hhmm.split(":")
        require(parts.size >= 2) { "Invalid time format: $hhmm" }
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    private fun checkNumericCondition(condition: JSONObject, value: Double): Boolean {
        var result = true
        if (condition.has("below")) {
            result = result && value < condition.getDouble("below")
        }
        if (condition.has("above")) {
            result = result && value > condition.getDouble("above")
        }
        return result
    }

    /** For a "temperature below X" condition: what is the temperature now and which room to heat. */
    private suspend fun buildTemperatureAlert(
        automation: AdvancedAutomationEntity,
        firedTrigger: JSONObject?
    ): TemperatureAlert? {
        val conditions = JSONArray(automation.conditionsJson)
        val candidates = listOfNotNull(firedTrigger) +
            (0 until conditions.length()).map { conditions.getJSONObject(it) }
        for (condition in candidates) {
            if (condition.optString("type") != "numeric_state" || !condition.has("below")) continue
            val entity = condition.optString("entity")
            if (entity != "indoor_temp" && entity != "outdoor_temp") continue

            val threshold = condition.getDouble("below")
            val rooms = readIndoorRooms()
            return if (entity == "indoor_temp") {
                val coldest = rooms.filter { it.temp < threshold }.minByOrNull { it.temp }
                    ?: rooms.minByOrNull { it.temp }
                coldest?.let { TemperatureAlert(threshold, "Temperatura actual (${it.name})", it.temp, it) }
            } else {
                val homeId = resolveHomeId(condition.optString("home_id", ""))
                val outdoor = thermostatRepository.getOutdoorTemperature(homeId) ?: return null
                TemperatureAlert(threshold, "Temperatura exterior", outdoor, rooms.minByOrNull { it.temp })
            }
        }
        return null
    }

    private suspend fun executeActions(automation: AdvancedAutomationEntity, alert: TemperatureAlert?): List<Int> {
        val actions = JSONArray(automation.actionsJson)
        val postedNotificationIds = mutableListOf<Int>()
        for (i in 0 until actions.length()) {
            executeAction(actions.getJSONObject(i), alert, automation.name)?.let { postedNotificationIds.add(it) }
        }
        return postedNotificationIds
    }

    /** Returns the posted notification id for "notify" actions, null for anything else. */
    private suspend fun executeAction(action: JSONObject, alert: TemperatureAlert?, automationName: String): Int? {
        when (action.optString("type")) {
            "set_temperature" -> {
                val homeId = action.optString("homeId")
                val roomId = action.optString("roomId")
                val temperature = action.getDouble("temperature")
                thermostatRepository.setTemperature(homeId, roomId, temperature)
            }
            "set_mode" -> {
                // The editor saves set_mode without home/room: it is a home-level mode change
                // (same target as set_home_mode). Without resolving the home this always
                // failed with an API error on an empty home_id.
                val mode = action.optString("mode")
                val homeId = resolveHomeId(action.optString("homeId", ""))
                check(homeId.isNotEmpty()) { "No hay instalación disponible" }
                thermostatRepository.setHomeMode(homeId, mode)
            }
            "notify" -> {
                val rawTitle = action.optString("title", "Automation").ifBlank { "Automatización" }
                val rawMessage = action.optString("message", "")
                val requested = NotificationTemplate.variables(rawTitle) + NotificationTemplate.variables(rawMessage)
                val values = notificationValues(requested, automationName, alert)
                val title = NotificationTemplate.render(rawTitle, values)
                val message = NotificationTemplate.render(rawMessage, values)
                return if (alert != null) notifyTemperatureAlert(title, message, alert) else notifyPlain(title, message)
            }
            "set_home_mode" -> {
                val mode = action.optString("mode", "schedule")
                // Prefer durationHours (computed now, at trigger time). "endAt" remains for
                // automations saved before the fix, where it was frozen at save time.
                val hours = action.optLong("durationHours", 0L).takeIf { it > 0 }
                val endAt = hours?.let { System.currentTimeMillis() + it * 3_600_000L }
                    ?: action.optLong("endAt", 0L).takeIf { it > 0 }
                val homeId = resolveHomeId(action.optString("homeId", ""))
                check(homeId.isNotEmpty()) { "No hay instalación disponible" }
                thermostatRepository.setHomeMode(homeId, mode, endAt?.let { it / 1000 })
            }
            "delay" -> {
                val minutes = action.optLong("minutes", 1)
                delay(minutes * 60_000L)
            }
            "apply_scenario" -> {
                val scenarioId = action.optLong("scenarioId")
                val scenario = automationRepository.getScenarioById(scenarioId) ?: return null
                val scenarioActions = JSONArray(scenario.actionsJson)
                for (i in 0 until scenarioActions.length()) {
                    val scenarioAction = scenarioActions.getJSONObject(i)
                    val roomId = scenarioAction.optString("roomId")
                    val temp = scenarioAction.optDouble("temp", Double.NaN)
                    if (!temp.isNaN() && roomId.isNotEmpty()) {
                        thermostatRepository.setTemperature(scenario.homeId, roomId, temp)
                    }
                }
            }
        }
        return null
    }

    /** Read values at each notification action, including after a delay or a setpoint action. */
    private suspend fun notificationValues(
        requested: Set<String>, name: String, alert: TemperatureAlert?
    ): Map<String, String?> {
        if (requested.isEmpty()) return emptyMap()
        if ((requested - "automatizacion").isEmpty()) return mapOf("automatizacion" to name)
        val rooms = mutableListOf<NotificationTemplate.Room>()
        val homes = (thermostatRepository.getHomesData() as? ApiResult.Success)?.data.orEmpty()
        val needsRooms = requested.any { it != "automatizacion" && it != "tem_ext" }
        if (needsRooms) for (home in homes) {
            val status = (thermostatRepository.getHomeStatus(home.id) as? ApiResult.Success)?.data ?: continue
            val names = home.rooms.orEmpty().associate { it.id to it.name }
            status.rooms.orEmpty().forEach { room ->
                rooms.add(NotificationTemplate.Room(home.id, room.id, names[room.id] ?: "Habitación",
                    room.measuredTemperature, room.setpointTemperature, room.setpointMode))
            }
        }
        // Base ambiguity on the full home list, not on whichever home happened to respond.
        val homeId = alert?.room?.homeId ?: homes.singleOrNull()?.id
        val outdoor = if ("tem_ext" in requested && homeId != null)
            thermostatRepository.getOutdoorTemperature(homeId) else null
        return NotificationTemplate.values(name, rooms, homeId ?: "", alert?.room?.roomId, outdoor)
    }

    private fun requireNotificationsEnabled() {
        check(NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            "Las notificaciones de la app están desactivadas en Android"
        }
    }

    private fun notifyPlain(title: String, message: String): Int {
        requireNotificationsEnabled()
        val notificationId = System.currentTimeMillis().toInt()
        val notification = NotificationCompat.Builder(context, NetatmoApp.CHANNEL_ADVANCED)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        return notificationId
    }

    private fun notifyTemperatureAlert(title: String, message: String, alert: TemperatureAlert): Int {
        requireNotificationsEnabled()
        val notificationId = System.currentTimeMillis().toInt()
        val lines = mutableListOf(
            "${alert.measuredLabel}: ${formatTemp(alert.measured)}°C (umbral: por debajo de ${formatTemp(alert.threshold)}°C)"
        )
        if (alert.room != null && alert.measuredLabel.startsWith("Temperatura exterior")) {
            lines.add("Interior (${alert.room.name}): ${formatTemp(alert.room.temp)}°C")
        }
        if (message.isNotBlank()) lines.add(message)

        val builder = NotificationCompat.Builder(context, NetatmoApp.CHANNEL_ADVANCED)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val room = alert.room
        if (room != null) {
            val targets = BOOST_STEPS.map { step -> boostTarget(room.temp, step) }
            lines.add("Subir termostato a: " + targets.joinToString(" / ") { "${formatTemp(it)}°C" })
            BOOST_STEPS.forEachIndexed { index, step ->
                val intent = Intent(context, ThermostatActionReceiver::class.java).apply {
                    action = ThermostatActionReceiver.ACTION_SET_TEMPERATURE
                    putExtra(ThermostatActionReceiver.EXTRA_HOME_ID, room.homeId)
                    putExtra(ThermostatActionReceiver.EXTRA_ROOM_ID, room.roomId)
                    putExtra(ThermostatActionReceiver.EXTRA_ROOM_NAME, room.name)
                    putExtra(ThermostatActionReceiver.EXTRA_TARGET, targets[index])
                    putExtra(ThermostatActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                }
                val pending = PendingIntent.getBroadcast(
                    context,
                    notificationId * 10 + index,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(0, "+${formatTemp(step)}°", pending)
            }
        }

        val text = lines.joinToString("\n")
        val notification = builder
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        return notificationId
    }

    /**
     * Ground truth about what Android did with the notifications the test just posted, read back
     * from the system right after sending. Posting without an exception only means the call did
     * not crash; this reports whether the system actually holds them.
     */
    private suspend fun describeNotificationDelivery(postedIds: List<Int>): String {
        val sb = StringBuilder()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Posting can be asynchronous on some devices: re-check over ~1.5 s before reporting.
        var activeIds: List<Int>? = null
        var allPosted = false
        for (attempt in 1..4) {
            activeIds = try {
                nm.activeNotifications?.map { it.id }
            } catch (e: Exception) {
                null
            }
            allPosted = activeIds?.let { ids -> postedIds.all { it in ids } } == true
            if (allPosted) break
            if (attempt < 4) delay(500)
        }
        when {
            activeIds == null ->
                sb.appendLine("No se pudo comprobar en el sistema si el aviso quedó registrado.")
            allPosted ->
                sb.appendLine("Android confirma el aviso registrado en el sistema. Si no lo ves, está silenciado u oculto (No molestar/descanso o canal en silencio).")
            else ->
                sb.appendLine("El aviso no aparece entre las notificaciones activas del sistema tras enviarlo (comprobado varias veces durante unos segundos).")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = nm.getNotificationChannel(NetatmoApp.CHANNEL_ADVANCED)
            if (channel == null) {
                sb.appendLine("El canal «Automatizaciones avanzadas» no existe en Android (puede haberse eliminado).")
            } else {
                val importance = when (channel.importance) {
                    NotificationManager.IMPORTANCE_NONE -> "bloqueado"
                    NotificationManager.IMPORTANCE_MIN -> "mínima (minimizado en la bandeja, sin sonido)"
                    NotificationManager.IMPORTANCE_LOW -> "baja (solo bandeja, sin sonido)"
                    NotificationManager.IMPORTANCE_DEFAULT -> "normal (bandeja y sonido)"
                    else -> "alta (aviso emergente)"
                }
                sb.appendLine("Canal «Automatizaciones avanzadas»: importancia $importance.")
            }
            if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                sb.appendLine("No molestar / modo descanso está activo ahora mismo: puede silenciar u ocultar el aviso aunque esté registrado.")
            }
        }
        return sb.toString()
    }

    // Rounded up to the 0.5° step Netatmo accepts, so the result is never below current + step.
    private fun boostTarget(current: Double, step: Double): Double =
        (ceil((current + step) * 2) / 2).coerceIn(MIN_SETPOINT, MAX_SETPOINT)

    private fun formatTemp(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(Locale("es", "ES"), value)
}
