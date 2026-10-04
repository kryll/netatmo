package com.arsys.netatmo.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.arsys.netatmo.data.local.dao.HomeCacheDao
import com.arsys.netatmo.data.local.entities.AutomationLogEntity
import com.arsys.netatmo.data.repository.ApiResult
import com.arsys.netatmo.data.repository.AutomationRepository
import com.arsys.netatmo.data.repository.ThermostatRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@HiltWorker
class OutdoorTempWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val thermostatRepository: ThermostatRepository,
    private val automationRepository: AutomationRepository,
    private val homeCacheDao: HomeCacheDao
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "OutdoorTempWorker"
        private const val CHANNEL_OUTDOOR = "outdoor_temp"
        private const val WORK_NAME = "OutdoorTempWorker"
        private const val NOTIFICATION_BASE_ID = 8100

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<OutdoorTempWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        // 1. Get all enabled OUTDOOR_TEMP automations
        val automations = automationRepository
            .getAutomationsByType("OUTDOOR_TEMP")
            .first()
            .filter { it.enabled }

        if (automations.isEmpty()) return Result.success()

        // 2. Fetch fresh homes data (also updates homeCacheDao)
        val homesResult = thermostatRepository.getHomesData()
        if (homesResult !is ApiResult.Success) {
            Log.w(TAG, "getHomesData failed; skipping outdoor temp check")
            return Result.success()
        }

        // 3. Extract outdoor module temperature from the cached module JSON
        val outdoorTemp = extractOutdoorTemperature(homesResult.data.map { it.id })
        if (outdoorTemp == null) {
            Log.d(TAG, "No outdoor temperature available; skipping")
            return Result.success()
        }

        Log.d(TAG, "Outdoor temperature: $outdoorTemp°C")

        // 4. Evaluate each automation and act if condition is met
        val now = System.currentTimeMillis()
        for (automation in automations) {
            try {
                val trigger = JSONObject(automation.triggerData)
                val condition = trigger.optString("condition", "below")
                val thresholdTemp = trigger.optDouble("thresholdTemp", Double.NaN)
                val targetHomeId = trigger.optString("targetHomeId", "")
                val targetRoomId = trigger.optString("targetRoomId", "")
                val targetTemp = trigger.optDouble("targetTemp", Double.NaN)

                if (thresholdTemp.isNaN() || targetTemp.isNaN() ||
                    targetHomeId.isBlank() || targetRoomId.isBlank()
                ) {
                    Log.w(TAG, "Automation ${automation.id} has incomplete triggerData; skipping")
                    continue
                }

                val conditionMet = when (condition) {
                    "below" -> outdoorTemp < thresholdTemp
                    "above" -> outdoorTemp > thresholdTemp
                    else -> {
                        Log.w(TAG, "Unknown condition '$condition' in automation ${automation.id}")
                        false
                    }
                }

                if (!conditionMet) continue

                Log.d(TAG, "Condition met for automation '${automation.name}': outdoor $outdoorTemp $condition $thresholdTemp")

                val setResult = thermostatRepository.setTemperature(
                    homeId = targetHomeId,
                    roomId = targetRoomId,
                    temperature = targetTemp
                )

                val success = setResult is ApiResult.Success
                val errorMsg = if (setResult is ApiResult.Error) setResult.message else null

                automationRepository.updateLastTriggered(automation.id, now)
                automationRepository.logExecution(
                    AutomationLogEntity(
                        automationId = automation.id,
                        automationName = automation.name,
                        triggerType = "OUTDOOR_TEMP",
                        timestamp = now,
                        success = success,
                        errorMessage = errorMsg
                    )
                )

                if (success) {
                    sendNotification(
                        automationId = automation.id,
                        automationName = automation.name,
                        outdoorTemp = outdoorTemp,
                        condition = condition,
                        thresholdTemp = thresholdTemp,
                        targetTemp = targetTemp
                    )
                } else {
                    Log.e(TAG, "setTemperature failed for '${automation.name}': $errorMsg")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing automation ${automation.id}", e)
                automationRepository.logExecution(
                    AutomationLogEntity(
                        automationId = automation.id,
                        automationName = automation.name,
                        triggerType = "OUTDOOR_TEMP",
                        timestamp = now,
                        success = false,
                        errorMessage = e.message
                    )
                )
            }
        }

        return Result.success()
    }

    /**
     * Scans the cached module JSON for each home and returns the first outdoor
     * temperature reading found in a NAModule1 (outdoor) module. The Netatmo API
     * may include a "temperature" field or a nested "dashboard_data" object in the
     * raw module JSON that is not captured by the typed Module data class.
     */
    private suspend fun extractOutdoorTemperature(homeIds: List<String>): Double? {
        for (homeId in homeIds) {
            val cacheEntry = homeCacheDao.getHome(homeId) ?: continue
            val temp = parseOutdoorTempFromModulesJson(cacheEntry.modulesJson)
            if (temp != null) return temp
        }
        return null
    }

    private fun parseOutdoorTempFromModulesJson(modulesJson: String): Double? {
        return try {
            val array = JSONArray(modulesJson)
            for (i in 0 until array.length()) {
                val module = array.optJSONObject(i) ?: continue
                val type = module.optString("type", "")
                val isOutdoor = type == "NAModule1"
                val hasTempMeasure = module.optString("measure_type", "")
                    .contains("temperature", ignoreCase = true)

                if (!isOutdoor && !hasTempMeasure) continue

                // Direct temperature field
                if (module.has("temperature")) {
                    return module.getDouble("temperature")
                }

                // Nested dashboard_data (Netatmo weather station format)
                val dashboard = module.optJSONObject("dashboard_data")
                if (dashboard != null && dashboard.has("Temperature")) {
                    return dashboard.getDouble("Temperature")
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse modulesJson for outdoor temperature", e)
            null
        }
    }

    private fun sendNotification(
        automationId: Long,
        automationName: String,
        outdoorTemp: Double,
        condition: String,
        thresholdTemp: Double,
        targetTemp: Double
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannelExists()

        val conditionLabel = if (condition == "below") "por debajo de" else "por encima de"
        val title = "Temperatura exterior: ${outdoorTemp}°C"
        val text = "$automationName activada ($conditionLabel ${thresholdTemp}°C) → objetivo ${targetTemp}°C"

        val notification = NotificationCompat.Builder(appContext, CHANNEL_OUTDOOR)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notifId = NOTIFICATION_BASE_ID + (automationId % 100).toInt()
        NotificationManagerCompat.from(appContext).notify(notifId, notification)
    }

    private fun ensureChannelExists() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = appContext.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_OUTDOOR) == null) {
                val channel = NotificationChannel(
                    CHANNEL_OUTDOOR,
                    "Temperatura exterior",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Automatizaciones basadas en temperatura exterior"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
