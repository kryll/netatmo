package com.arsys.netatmo.service

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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

@Singleton
class AdvancedAutomationEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val advancedAutomationRepository: AdvancedAutomationRepository,
    private val thermostatRepository: ThermostatRepository,
    private val automationRepository: AutomationRepository
) {
    suspend fun evaluateAndExecuteAll() {
        val automations = advancedAutomationRepository.getEnabledAdvancedAutomations()
        automations.forEach { automation ->
            evaluateAndExecute(automation)
        }
    }

    suspend fun evaluateAndExecute(automation: AdvancedAutomationEntity) {
        try {
            val conditionsMet = evaluateConditions(automation)
            if (!conditionsMet) {
                advancedAutomationRepository.updateLastTriggered(automation.id, System.currentTimeMillis(), "skipped")
                return
            }
            executeActions(automation)
            advancedAutomationRepository.updateLastTriggered(automation.id, System.currentTimeMillis(), "success")
        } catch (e: Exception) {
            advancedAutomationRepository.updateLastTriggered(automation.id, System.currentTimeMillis(), "error")
        }
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
                val afterParts = after.split(":")
                val beforeParts = before.split(":")
                val afterMinutes = afterParts[0].toInt() * 60 + afterParts[1].toInt()
                val beforeMinutes = beforeParts[0].toInt() * 60 + beforeParts[1].toInt()
                if (afterMinutes <= beforeMinutes) {
                    currentMinutes in afterMinutes..beforeMinutes
                } else {
                    currentMinutes >= afterMinutes || currentMinutes <= beforeMinutes
                }
            }
            "numeric_state" -> {
                when (condition.optString("entity")) {
                    "outdoor_temp" -> {
                        val homeId = resolveHomeId(condition.optString("home_id", ""))
                        if (homeId.isEmpty()) return true
                        val temp = thermostatRepository.getOutdoorTemperature(homeId) ?: return true
                        checkNumericCondition(condition, temp)
                    }
                    "indoor_temp" -> {
                        val temp = getAnyIndoorTemperature() ?: return true
                        checkNumericCondition(condition, temp)
                    }
                    else -> true
                }
            }
            "presence" -> true
            else -> true
        }
    }

    private suspend fun resolveHomeId(homeIdFromCondition: String): String {
        if (homeIdFromCondition.isNotEmpty()) return homeIdFromCondition
        val result = thermostatRepository.getHomesData()
        return if (result is ApiResult.Success) result.data.firstOrNull()?.id ?: "" else ""
    }

    private suspend fun getAnyIndoorTemperature(): Double? {
        val homesResult = thermostatRepository.getHomesData()
        if (homesResult !is ApiResult.Success) return null
        val homes = homesResult.data
        for (home in homes) {
            val statusResult = thermostatRepository.getHomeStatus(home.id)
            if (statusResult is ApiResult.Success) {
                val temp = statusResult.data.rooms
                    ?.mapNotNull { it.measuredTemperature }
                    ?.firstOrNull()
                if (temp != null) return temp
            }
        }
        return null
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

    private suspend fun executeActions(automation: AdvancedAutomationEntity) {
        val actions = JSONArray(automation.actionsJson)
        for (i in 0 until actions.length()) {
            executeAction(actions.getJSONObject(i))
        }
    }

    private suspend fun executeAction(action: JSONObject) {
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
                val title = action.optString("title", "Automation")
                val message = action.optString("message", "")
                val notification = NotificationCompat.Builder(context, "advanced_automations")
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
                NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
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
}
