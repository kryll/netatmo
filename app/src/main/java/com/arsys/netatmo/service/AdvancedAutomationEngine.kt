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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val familyPresenceManager: FamilyPresenceManager,
    private val alarmScheduler: AutomationAlarmScheduler
) {
    private companion object {
        const val STATE_PREFS = "advanced_automation_state"
        const val KEY_LAST_EVAL_MS = "last_eval_ms"
        // Avoids re-running when a value hovers around its threshold.
        const val MIN_REPEAT_MS = 60L * 60 * 1000
        val BOOST_STEPS = listOf(1.0, 1.5, 2.0)
        const val MIN_SETPOINT = 7.0
        const val MAX_SETPOINT = 30.0
    }

    private val statePrefs by lazy { context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE) }

    // Serializes engine evaluation: the periodic worker and an alarm-driven run can overlap,
    // and the read-check-mark sequence must not execute the same trigger twice.
    private val evaluationMutex = Mutex()

    suspend fun evaluateAndExecuteAll() {
        evaluationMutex.withLock {
            val now = System.currentTimeMillis()
            val windowStartMs = calendarWindowStart(now)
            val automations = advancedAutomationRepository.getEnabledAdvancedAutomations()
            DiagnosticLog.log(context, "MOTOR", "Evaluando ${automations.size} automatización(es) activas")
            automations.forEach { automation ->
                evaluateAndExecute(automation, windowStartMs)
            }
            statePrefs.edit().putLong(KEY_LAST_EVAL_MS, now).apply()
        }
    }

    /**
     * Start of the fire window for calendar triggers: the previous evaluation (periodic,
     * immediate or alarm-driven), or one default poll period back when there is none (first
     * run after install or app-data reset). Each automation narrows it further: never before
     * its own creation nor before its last save or re-enable (see the overload below).
     */
    private fun calendarWindowStart(nowMs: Long): Long {
        val last = statePrefs.getLong(KEY_LAST_EVAL_MS, 0L)
        return if (last > 0) last else nowMs - AutomationTriggerMath.DEFAULT_POLL_MS
    }

    /**
     * Per-automation fire-window start for calendar triggers: never before the global previous
     * evaluation, never before the automation was created, and never before its last save or
     * re-enable ("tcal_since_<id>", written by the editor and the enable toggle). Enabling an
     * old automation or changing its calendar or title filter cannot re-fire matches from
     * before that moment; only matches since then fire, late rather than never.
     */
    private fun calendarWindowStart(automation: AdvancedAutomationEntity, globalWindowStartMs: Long): Long =
        maxOf(globalWindowStartMs, automation.createdAt, statePrefs.getLong("tcal_since_${automation.id}", 0L))

    /**
     * An automation runs when one of its triggers fires and its conditions hold. Without triggers
     * it runs when its conditions start being met. Never on every periodic check.
     */
    suspend fun evaluateAndExecute(
        automation: AdvancedAutomationEntity,
        windowStartMs: Long = calendarWindowStart(System.currentTimeMillis())
    ) {
        try {
            migrateDatetimeConsumption(context, advancedAutomationRepository, automation.id)
            migrateCalendarConsumption(context, automation.id)
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
                firedTrigger = findFiringTrigger(automation.id, triggers, calendarWindowStart(automation, windowStartMs))
                shouldRun = firedTrigger != null && evaluateConditions(automation)
            }
            if (firedTrigger != null || triggers.length() == 0) {
                DiagnosticLog.log(context, "MOTOR", "«${automation.name}»: disparador=${firedTrigger?.optString("type") ?: "sin disparadores"} dispara=${firedTrigger != null} condiciones/ejecutar=$shouldRun")
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
        if (minRepeatMs > 0 && now - statePrefs.getLong(firedKey, 0L) < minRepeatMs) {
            DiagnosticLog.log(context, "MOTOR", "«${automation.name}»: NO ejecutada, ya se ejecutó hace menos de 1 h (${DiagnosticLog.fmt(statePrefs.getLong(firedKey, 0L))})")
            return
        }
        DiagnosticLog.log(context, "MOTOR", "«${automation.name}»: ejecutando acciones")
        statePrefs.edit().putLong(firedKey, now).apply()
        try {
            val alert = buildTemperatureAlert(automation, firedTrigger)
            apiErrorsInRun = 0
            executeActions(automation, alert)
            advancedAutomationRepository.updateLastTriggered(automation.id, now, "success")
            logExecution(automation, now, success = true, error = null)
            if (apiErrorsInRun > 0) {
                DiagnosticLog.log(context, "MOTOR", "«${automation.name}»: acciones terminadas con $apiErrorsInRun ERROR(ES) de API de Netatmo. El Historial lo marca como éxito (comportamiento previo) y no se reintenta")
            } else {
                DiagnosticLog.log(context, "MOTOR", "«${automation.name}»: acciones terminadas sin errores de API")
            }
        } catch (e: Exception) {
            recordFailure(automation, e)
        }
    }

    private suspend fun recordFailure(automation: AdvancedAutomationEntity, e: Exception) {
        val firedKey = "fired_${automation.id}"
        val now = System.currentTimeMillis()
        DiagnosticLog.log(context, "MOTOR", "«${automation.name}»: FALLO ${e.javaClass.simpleName}: ${e.message}. Se reintentará en la próxima evaluación.")
        advancedAutomationRepository.updateLastTriggered(automation.id, now, "error")
        logExecution(automation, now, success = false, error = e.message ?: e.javaClass.simpleName)
        // Forget the consumed trigger so the next check tries again instead of waiting for a new crossing.
        val editor = statePrefs.edit().remove(firedKey)
        statePrefs.all.keys
            .filter {
                it.startsWith("tact_${automation.id}_") || it.startsWith("tday_${automation.id}_") ||
                    it.startsWith("tdt_${automation.id}_") || it.startsWith("tdt3_${automation.id}_") ||
                    it.startsWith("tcal_${automation.id}_")
            }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    /**
     * Runs [automation] right now, ignoring the "already fired" state, and returns a plain-text
     * report of what it read and did. Used by the "Probar ahora" button.
     */
    suspend fun testRun(automation: AdvancedAutomationEntity): String {
        migrateDatetimeConsumption(context, advancedAutomationRepository, automation.id)
        migrateCalendarConsumption(context, automation.id)
        val report = StringBuilder()
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            report.appendLine("AVISO: las notificaciones de la app están desactivadas en Android (Ajustes del móvil > Aplicaciones > Netatmo Smart > Notificaciones). Sin eso no verás ningún aviso.")
        } else if (manager.getNotificationChannel(NetatmoApp.CHANNEL_ADVANCED)?.importance == android.app.NotificationManager.IMPORTANCE_NONE) {
            report.appendLine("AVISO: el canal \"Automatizaciones avanzadas\" está desactivado en los ajustes de notificaciones de Android.")
        }
        evaluationMutex.withLock {
        try {
            val triggers = JSONArray(automation.triggersJson)
            val typeAtPairs = (0 until triggers.length()).map {
                val t = triggers.getJSONObject(it)
                t.optString("type") to t.optLong("at", 0L)
            }
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
                        val next = CalendarEventQuery.queryEvents(
                            context,
                            trigger.optLong("calendarId", -1L),
                            trigger.optString("title").ifBlank { null },
                            System.currentTimeMillis(), System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
                        ).firstOrNull()
                        report.appendLine("Disparador ${i + 1}: calendario «${trigger.optString("calendarName")}»" +
                            (trigger.optString("title").ifBlank { "" }.let { if (it.isEmpty()) "" else " con «$it»" }) +
                            ", ${trigger.optInt("minutesBefore", 30)} min antes: " +
                            (next?.let { "próximo evento «${it.title}» el ${formatDateTime(it.startMs)}" } ?: "sin eventos próximos en 7 días") +
                            (if (CalendarEventQuery.hasCalendarPermission(context)) "" else " AVISO: sin permiso de lectura de calendario; no se puede comprobar.") +
                            " (se evalúa cuando toca, no se puede forzar aquí)")
                    }
                    "datetime" -> {
                        val at = trigger.optLong("at", 0L)
                        val occurrence = AutomationTriggerMath.datetimeOccurrence(typeAtPairs, i)
                        val consumed = statePrefs.getBoolean(AutomationTriggerMath.datetimeConsumedKey(automation.id, at, occurrence), false)
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
        }
        if (!alarmScheduler.canScheduleExact()) {
            report.appendLine("AVISO: este móvil no permite alarmas exactas; los disparadores de hora y fecha dependen de la comprobación periódica (~15 min) y pueden retrasarse.")
        } else {
            val nextAlarm = alarmScheduler.nextArmedAt()
            report.appendLine(
                if (nextAlarm > 0) "Programación puntual: próxima alarma ${formatDateTime(nextAlarm)}."
                else "Programación puntual: activada; sin alarma armada ahora mismo (se reprograma al guardar y tras cada ejecución)."
            )
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
    private suspend fun findFiringTrigger(automationId: Long, triggers: JSONArray, windowStartMs: Long): JSONObject? {
        val typeAtPairs = (0 until triggers.length()).map {
            val t = triggers.getJSONObject(it)
            t.optString("type") to t.optLong("at", 0L)
        }
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
                "calendar" -> calendarTriggerFires(automationId, trigger, windowStartMs)
                "datetime" -> datetimeTriggerFires(automationId, AutomationTriggerMath.datetimeOccurrence(typeAtPairs, i), trigger)
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
        if (elapsed !in 0 until AutomationTriggerMath.TIME_TRIGGER_WINDOW_MIN) return false
        val dayKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
        val key = "tday_${automationId}_$index"
        if (statePrefs.getString(key, null) == dayKey) return false
        statePrefs.edit().putString(key, dayKey).apply()
        DiagnosticLog.log(context, "MOTOR", "Disparador hora ${trigger.optString("time")}: dentro de la ventana (${elapsed} min tras la hora), se ejecuta")
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
     * Fires once per matching calendar event, [minutesBefore] before it starts. The trigger
     * instant must fall after the previous evaluation ([windowStartMs], never before the
     * automation existed) and not after now: a "0 minutes before" trigger fires on the first
     * check after the event starts, and a battery-delayed check fires late instead of never.
     * Consumed events are remembered per event instant (never by trigger position), so they
     * cannot fire twice even if the trigger list is edited or reordered. Every evaluation
     * logs what the trigger saw, so the diagnostic is never blind for calendar triggers.
     */
    private fun calendarTriggerFires(automationId: Long, trigger: JSONObject, windowStartMs: Long): Boolean {
        val calendarId = trigger.optLong("calendarId", -1L)
        if (calendarId < 0) return false
        val minutesBefore = trigger.optInt("minutesBefore", 30).coerceAtLeast(0)
        val label = "Disparador calendario «${trigger.optString("calendarName")}»" +
            trigger.optString("title").ifBlank { "" }.let { if (it.isEmpty()) "" else " con «$it»" } +
            ", $minutesBefore min antes"
        if (!CalendarEventQuery.hasCalendarPermission(context)) {
            DiagnosticLog.log(context, "MOTOR", "$label: SIN PERMISO de lectura de calendario; no se puede evaluar. Concédelo en Ajustes del móvil > Aplicaciones > Netatmo Smart > Permisos.")
            return false
        }
        val now = System.currentTimeMillis()
        val events = CalendarEventQuery.queryEvents(
            context,
            calendarId,
            trigger.optString("title").ifBlank { null },
            windowStartMs, now + 7L * 24 * 60 * 60 * 1000
        )
        var fires = false
        for (event in events) {
            if (AutomationTriggerMath.calendarShouldFire(event.startMs, minutesBefore, now, windowStartMs)) {
                val key = AutomationTriggerMath.calendarConsumedKey(automationId, event.startMs)
                if (!statePrefs.getBoolean(key, false)) {
                    statePrefs.edit().putBoolean(key, true).apply()
                    fires = true
                    DiagnosticLog.log(context, "MOTOR", "$label: evento «${event.title}» del ${formatDateTime(event.startMs)}: TOCA, se ejecuta (retraso ${(now - (event.startMs - minutesBefore * 60_000L)) / 1000}s)")
                }
            }
        }
        if (!fires) {
            val next = events.firstOrNull { it.startMs - minutesBefore * 60_000L > now }
            DiagnosticLog.log(context, "MOTOR", "$label: " +
                (next?.let { "próximo evento «${it.title}» el ${formatDateTime(it.startMs)}, aún no toca" }
                    ?: "sin eventos que coincidan entre la última comprobación y 7 días"))
        }
        return fires
    }

    /** One-shot trigger for a fixed instant; fires on the first check at or after [at]. */
    private fun datetimeTriggerFires(automationId: Long, occurrence: Int, trigger: JSONObject): Boolean {
        val at = trigger.optLong("at", 0L)
        val key = AutomationTriggerMath.datetimeConsumedKey(automationId, at, occurrence)
        val consumed = statePrefs.getBoolean(key, false)
        val nowMs = System.currentTimeMillis()
        if (!AutomationTriggerMath.datetimeShouldFire(at, consumed, nowMs)) {
            if (at > 0 && !consumed) {
                DiagnosticLog.log(context, "MOTOR", "Disparador fecha ${DiagnosticLog.fmt(at)}: aún no toca (ahora ${DiagnosticLog.fmt(nowMs)})")
            }
            return false
        }
        DiagnosticLog.log(context, "MOTOR", "Disparador fecha ${DiagnosticLog.fmt(at)}: VENCIDO, se ejecuta (retraso ${(nowMs - at) / 1000}s)")
        statePrefs.edit().putBoolean(key, true).apply()
        return true
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

    /** Diagnostic only: counts Netatmo API errors during the current run. Never changes execution. */
    @Volatile private var apiErrorsInRun = 0

    /**
     * Logs the API result of an action. Behaviour is the same as v195: an API error does NOT
     * fail the automation and is NOT retried (the Historial still marks it as success). Only the
     * diagnostic log says so explicitly.
     */
    private fun logApiResult(what: String, result: ApiResult<Unit>) {
        if (ApiResultLog.isError(result)) apiErrorsInRun++
        DiagnosticLog.log(context, "ACCION", ApiResultLog.describe(what, result))
    }

    private suspend fun executeActions(automation: AdvancedAutomationEntity, alert: TemperatureAlert?): List<Int> {
        val actions = JSONArray(automation.actionsJson)
        val postedNotificationIds = mutableListOf<Int>()
        // Diagnostic: record what the stored automation actually asks to run. Without this
        // line a missing/unsaved action is indistinguishable from one that ran silently
        // (v212 field log: notification arrived, no ACCION lines, mode not applied).
        val types = (0 until actions.length()).map {
            actions.getJSONObject(it).optString("type").ifBlank { "?" }
        }
        DiagnosticLog.log(context, "ACCION", "Ejecutando ${actions.length()} acción(es): ${types.joinToString(", ")}")
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
                logApiResult("Cambiar temperatura", thermostatRepository.setTemperature(homeId, roomId, temperature))
            }
            "set_mode" -> {
                // The editor saves set_mode without home/room: it is a home-level mode change
                // (same target as set_home_mode). Without resolving the home this always
                // failed with an API error on an empty home_id.
                val mode = action.optString("mode")
                // Same duration semantics as set_home_mode: compute the end when the
                // trigger fires, so Netatmo returns to schedule when it ends.
                val hours = action.optDouble("durationHours", 0.0).takeIf { it > 0.0 }
                val endAt = hours?.let { System.currentTimeMillis() + (it * 3_600_000).toLong() }
                    ?: action.optLong("endAt", 0L).takeIf { it > 0 }
                val homeId = resolveHomeId(action.optString("homeId", ""))
                check(homeId.isNotEmpty()) { "No hay instalación disponible" }
                DiagnosticLog.log(context, "ACCION", "Modo=$mode hasta=${endAt?.let { DiagnosticLog.fmt(it) } ?: "sin fin"} (tipo ${action.optString("type")})")
                logApiResult("Cambiar modo", thermostatRepository.setHomeMode(homeId, mode, endAt?.let { it / 1000 }))
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
                val hours = action.optDouble("durationHours", 0.0).takeIf { it > 0.0 }
                val endAt = hours?.let { System.currentTimeMillis() + (it * 3_600_000).toLong() }
                    ?: action.optLong("endAt", 0L).takeIf { it > 0 }
                val homeId = resolveHomeId(action.optString("homeId", ""))
                check(homeId.isNotEmpty()) { "No hay instalación disponible" }
                DiagnosticLog.log(context, "ACCION", "Modo=$mode hasta=${endAt?.let { DiagnosticLog.fmt(it) } ?: "sin fin"} (tipo ${action.optString("type")})")
                logApiResult("Cambiar modo", thermostatRepository.setHomeMode(homeId, mode, endAt?.let { it / 1000 }))
            }
            "delay" -> {
                val minutes = action.optLong("minutes", 1)
                DiagnosticLog.log(context, "ACCION", "Espera de $minutes min")
                delay(minutes * 60_000L)
            }
            "apply_scenario" -> {
                val scenarioId = action.optLong("scenarioId")
                val scenario = automationRepository.getScenarioById(scenarioId)
                if (scenario == null) {
                    DiagnosticLog.log(context, "ACCION", "Escenario $scenarioId no encontrado: se ignora")
                    return null
                }
                DiagnosticLog.log(context, "ACCION", "Aplicando escenario '${scenario.name}' (${JSONArray(scenario.actionsJson).length()} acción(es))")
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
            else -> DiagnosticLog.log(context, "ACCION", "Tipo de acción desconocido '${action.optString("type")}': se ignora")
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

