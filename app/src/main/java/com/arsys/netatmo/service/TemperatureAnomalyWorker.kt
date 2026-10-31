package com.arsys.netatmo.service

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.hilt.work.HiltWorker
import com.arsys.netatmo.data.repository.AuthRepository
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.arsys.netatmo.NetatmoApp
import com.arsys.netatmo.data.local.dao.HomeCacheDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import org.json.JSONArray


/**
 * The temperature-anomaly decision, extracted pure for tests. Alerts only when the room is
 * BELOW its setpoint by at least [threshold] (inclusive: setpoint 20, reading 18, threshold 2
 * alerts - Jose, 31 oct 2026): a cold room despite a heating demand can be a real fault
 * (valve, boiler, open window). Never alerts over the setpoint.
 */
internal fun shouldAlertTemperatureAnomaly(current: Double, target: Double, threshold: Double): Boolean =
    (target - current) >= threshold

@HiltWorker
class TemperatureAnomalyWorker @AssistedInject constructor(
    @Assisted private val ctx: Context,
    @Assisted params: WorkerParameters,
    private val homeCacheDao: HomeCacheDao,
    private val dataStore: DataStore<Preferences>,
    private val authRepository: AuthRepository
) : CoroutineWorker(ctx, params) {

    companion object {
        private val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
        private val ANOMALY_THRESHOLD_KEY = floatPreferencesKey("anomaly_threshold")
        private val VACATION_ENABLED_KEY = booleanPreferencesKey("vacation_enabled")
        private val VACATION_START_MS_KEY = longPreferencesKey("vacation_start_ms")
        private val VACATION_END_MS_KEY = longPreferencesKey("vacation_end_ms")
        private val VACATION_FROZEN_ALERTS_KEY = booleanPreferencesKey("vacation_frozen_alerts")
        private const val FROST_ALERT_THRESHOLD = 2.0

        fun schedule(context: Context) {
            val request = androidx.work.PeriodicWorkRequestBuilder<TemperatureAnomalyWorker>(
                15, java.util.concurrent.TimeUnit.MINUTES
            ).setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
            ).build()
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "anomaly_check",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val prefs = dataStore.data.first()

        val notificationsEnabled = prefs[NOTIFICATIONS_ENABLED_KEY] ?: true
        if (!notificationsEnabled) return Result.success()

        val now = System.currentTimeMillis()
        val frostWatch = (prefs[VACATION_ENABLED_KEY] ?: false) &&
            (prefs[VACATION_FROZEN_ALERTS_KEY] ?: true) &&
            now in (prefs[VACATION_START_MS_KEY] ?: 0L)..(prefs[VACATION_END_MS_KEY] ?: 0L)

        val threshold = (prefs[ANOMALY_THRESHOLD_KEY] ?: 2.0f).toDouble()
        val homeId = authRepository.selectedHomeId.first() ?: return Result.success()

        val homeCache = homeCacheDao.getHome(homeId) ?: return Result.success()

        val notificationManager =
            ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val roomsArray = try {
            JSONArray(homeCache.roomsJson)
        } catch (e: Exception) {
            return Result.success()
        }

        for (i in 0 until roomsArray.length()) {
            val roomObj = roomsArray.optJSONObject(i) ?: continue

            val name = roomObj.optString("name", "")
            val currentTemp: Double? = if (roomObj.isNull("currentTemp")) null
                                      else roomObj.optDouble("currentTemp", Double.NaN)
                                          .takeUnless { it.isNaN() }
            val targetTemp: Double? = if (roomObj.isNull("targetTemp")) null
                                     else roomObj.optDouble("targetTemp", Double.NaN)
                                         .takeUnless { it.isNaN() }

            if (frostWatch && currentTemp != null && currentTemp < FROST_ALERT_THRESHOLD) {
                val frostNotifId = (name.hashCode() and 0x7FFFFFFF) % 1000 + 3000
                val frostNotification = NotificationCompat.Builder(ctx, NetatmoApp.CHANNEL_ANOMALY)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("Riesgo de helada")
                    .setContentText("$name: ${"%.1f".format(currentTemp)}°C")
                    .setAutoCancel(true)
                    .build()
                notificationManager.notify(frostNotifId, frostNotification)
            }

            if (currentTemp == null || targetTemp == null) continue
            // Only UNDER-target alerts: heating cannot cool, so a reading above the
            // setpoint (e.g. an away/eco setpoint on a mild day) is not an anomaly
            // (José, 31 oct 2026). Over-target readings never notify.
            if (!shouldAlertTemperatureAnomaly(currentTemp, targetTemp, threshold)) continue

            val notifId = (name.hashCode() and 0x7FFFFFFF) % 1000 + 2000

            val notification = NotificationCompat.Builder(ctx, NetatmoApp.CHANNEL_ANOMALY)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Temperatura anomala")
                .setContentText("$name: ${"%.1f".format(currentTemp)}°C (objetivo ${"%.1f".format(targetTemp)}°C)")
                .setAutoCancel(true)
                .build()

            notificationManager.notify(notifId, notification)
        }

        return Result.success()
    }
}
