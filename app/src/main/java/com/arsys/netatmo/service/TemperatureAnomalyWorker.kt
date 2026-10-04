package com.arsys.netatmo.service

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.arsys.netatmo.data.local.dao.HomeCacheDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import org.json.JSONArray

@HiltWorker
class TemperatureAnomalyWorker @AssistedInject constructor(
    @Assisted private val ctx: Context,
    @Assisted params: WorkerParameters,
    private val homeCacheDao: HomeCacheDao,
    private val dataStore: DataStore<Preferences>
) : CoroutineWorker(ctx, params) {

    companion object {
        private val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
        private val ANOMALY_THRESHOLD_KEY = floatPreferencesKey("anomaly_threshold")
        private val SELECTED_HOME_ID_KEY = stringPreferencesKey("selected_home_id")

        const val CHANNEL_AUTOMATIONS = "channel_automations"
    }

    override suspend fun doWork(): Result {
        val prefs = dataStore.data.first()

        val notificationsEnabled = prefs[NOTIFICATIONS_ENABLED_KEY] ?: true
        if (!notificationsEnabled) return Result.success()

        val threshold = (prefs[ANOMALY_THRESHOLD_KEY] ?: 3.0f).toDouble()
        val homeId = prefs[SELECTED_HOME_ID_KEY] ?: return Result.success()

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

            if (currentTemp == null || targetTemp == null) continue
            if (Math.abs(currentTemp - targetTemp) <= threshold) continue

            val notifId = (name.hashCode() and 0x7FFFFFFF) % 1000 + 2000

            val notification = NotificationCompat.Builder(ctx, CHANNEL_AUTOMATIONS)
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
