package com.arsys.netatmo.service

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
    }

    private val statePrefs by lazy { context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE) }

    suspend fun evaluateAndExecuteAll() {
        val automations = advancedAutomationRepository.getEnabledAdvancedAutomations()
        automations.forEach { automation ->
            evaluateAndExecute(automation)
        }
    }

    /** Runs the actions only when the conditions start being met, not on every periodic check. */
    suspend fun evaluateAndExecute(automation: AdvancedAutomationEntity) {
        val metKey = "met_${automation.id}"
        val firedKey = "fired_${automation.id}"
        try {
            val conditionsMet = evaluateConditions(automation)
            val wasMet = statePrefs.getBoolean(metKey, false)
            statePrefs.edit().putBoolean(metKey, conditionsMet).apply()
            if (!conditionsMet || wasMet) return

            val now = System.currentTimeMillis()
            if (now - statePrefs.getLong(firedKey, 0L) < MIN_REPEAT_MS) return
            statePrefs.edit().putLong(firedKey, now).apply()

            val alert = buildTemperatureAlert(automation)
            executeActions(automation, alert)
            advancedAutomationRepository.updateLastTriggered(automation.id, now, "success")
            logExecution(automation, now, success = true, error = null)
        } catch (e: Exception) {
            val now = System.currentTimeMillis()
            advancedAutomationRepository.updateLastTriggered(automation.id, now, "error")
            logExecution(automation, now, success = false, error = e.message)
        }
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
            "numeric_state" -> {
                // A value that cannot be read counts as "not met", otherwise a failed request
                // would fire the automation.
                when (condition.optString("entity")) {
                    "outdoor_temp" -> {
                        val homeId = resolveHomeId(condition.optString("home_id", ""))
                        if (homeId.isEmpty()) return false
                        val temp = thermostatRepository.getOutdoorTemperature(homeId) ?: return false
                        checkNumericCondition(condition, temp)
                    }
                    "indoor_temp" -> {
                        val rooms = readIndoorRooms()
                        if (rooms.isEmpty()) return false
                        // "below" is met if any room is below; "above" if any room is above.
                        rooms.any { checkNumericCondition(condition, it.temp) }
                    }
                    else -> true
                }
            }
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
    private suspend fun buildTemperatureAlert(automation: AdvancedAutomationEntity): TemperatureAlert? {
        val conditions = JSONArray(automation.conditionsJson)
        for (i in 0 until conditions.length()) {
            val condition = conditions.getJSONObject(i)
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

    private suspend fun executeActions(automation: AdvancedAutomationEntity, alert: TemperatureAlert?) {
        val actions = JSONArray(automation.actionsJson)
        for (i in 0 until actions.length()) {
            executeAction(actions.getJSONObject(i), alert)
        }
    }

    private suspend fun executeAction(action: JSONObject, alert: TemperatureAlert?) {
        when (action.optString("type")) {
            "set_temperature" -> {
                val homeId = action.optString("homeId")
                val roomId = action.optString("roomId")
                val temperature = action.getDouble("temperature")
                thermostatRepository.setTemperature(homeId, roomId, temperature)
            }
            "set_mode" -> {
                val homeId = action.optString("homeId")
                val roomId = action.optString("roomId", "")
                val mode = action.optString("mode")
                thermostatRepository.setMode(homeId, roomId, mode)
            }
            "notify" -> {
                val title = action.optString("title", "Automation").ifBlank { "Automatización" }
                val message = action.optString("message", "")
                if (alert != null) notifyTemperatureAlert(title, message, alert) else notifyPlain(title, message)
            }
            "delay" -> {
                val minutes = action.optLong("minutes", 1)
                delay(minutes * 60_000L)
            }
            "apply_scenario" -> {
                val scenarioId = action.optLong("scenarioId")
                val scenario = automationRepository.getScenarioById(scenarioId) ?: return
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
    }

    private fun notifyPlain(title: String, message: String) {
        val notification = NotificationCompat.Builder(context, NetatmoApp.CHANNEL_ADVANCED)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun notifyTemperatureAlert(title: String, message: String, alert: TemperatureAlert) {
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
    }

    // Rounded up to the 0.5° step Netatmo accepts, so the result is never below current + step.
    private fun boostTarget(current: Double, step: Double): Double =
        (ceil((current + step) * 2) / 2).coerceIn(MIN_SETPOINT, MAX_SETPOINT)

    private fun formatTemp(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(Locale("es", "ES"), value)
}
