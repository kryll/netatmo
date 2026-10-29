package com.arsys.netatmo.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.arsys.netatmo.NetatmoApp
import com.arsys.netatmo.data.local.dao.HomeCacheDao
import com.arsys.netatmo.data.repository.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * Detects a possible open window: a sudden temperature drop in a room while the
 * heating is on. Notification only; it never changes the heating by itself.
 * Runs on the same 15-minute cadence as the data sync and reads the enriched
 * home cache written by getHomeStatus, storing its own previous sample per room.
 */
@HiltWorker
class OpenWindowWorker @AssistedInject constructor(
    @Assisted private val ctx: Context,
    @Assisted params: WorkerParameters,
    private val homeCacheDao: HomeCacheDao,
    private val dataStore: DataStore<Preferences>,
    private val authRepository: AuthRepository
) : CoroutineWorker(ctx, params) {

    companion object {
        private val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
        private val OPEN_WINDOW_ENABLED_KEY = booleanPreferencesKey("open_window_enabled")
        private val OPEN_WINDOW_OFF_ACTION_KEY = booleanPreferencesKey("open_window_off_action")
        private val STATE_KEY = stringPreferencesKey("open_window_state")

        private const val PREFS_NAME = "open_window_prefs"

        // Conservative thresholds: with the boiler on, a drop of 1 °C between two
        // consecutive samples (15-45 min apart) is a strong open-window signal and
        // very unlikely to come from normal temperature drift.
        private const val DROP_THRESHOLD_C = 1.0
        private const val MIN_SAMPLE_GAP_MS = 3 * 60_000L
        private const val MAX_SAMPLE_GAP_MS = 45 * 60_000L
        private const val ALERT_COOLDOWN_MS = 120 * 60_000L

        const val ACTION_DISMISS = "com.arsys.netatmo.DISMISS_OPEN_WINDOW"
        const val ACTION_TURN_OFF_30_MIN = "com.arsys.netatmo.OPEN_WINDOW_TURN_OFF_30_MIN"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_HOME_ID = "home_id"
        const val OFF_DURATION_SECONDS = 30 * 60L

        fun schedule(context: Context) {
            val request = androidx.work.PeriodicWorkRequestBuilder<OpenWindowWorker>(
                15, java.util.concurrent.TimeUnit.MINUTES
            ).setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
            ).build()
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "open_window_check",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val prefs = dataStore.data.first()
        if (!(prefs[NOTIFICATIONS_ENABLED_KEY] ?: true)) return Result.success()
        if (!(prefs[OPEN_WINDOW_ENABLED_KEY] ?: true)) return Result.success()
        val offActionEnabled = prefs[OPEN_WINDOW_OFF_ACTION_KEY] ?: true

        val homeId = authRepository.selectedHomeId.first() ?: return Result.success()
        val homeCache = homeCacheDao.getHome(homeId) ?: return Result.success()

        val now = System.currentTimeMillis()
        val sampleTs = homeCache.updatedAt
        // Without a recent reading there is no reliable detection.
        if (now - sampleTs > MAX_SAMPLE_GAP_MS) return Result.success()

        val roomsArray = try {
            org.json.JSONArray(homeCache.roomsJson)
        } catch (e: Exception) {
            return Result.success()
        }

        val state = try {
            JSONObject(prefs[STATE_KEY] ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }

        val cooldownPrefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val notificationManager =
            ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        var stateChanged = false

        for (i in 0 until roomsArray.length()) {
            val roomObj = roomsArray.optJSONObject(i) ?: continue

            val name = roomObj.optString("name", "")
            if (name.isEmpty()) continue
            val roomKey = roomObj.optString("id", "").ifEmpty { name }

            val currentTemp: Double = if (roomObj.isNull("currentTemp")) continue
            else roomObj.optDouble("currentTemp", Double.NaN).takeUnless { it.isNaN() } ?: continue

            val targetTemp: Double? = if (roomObj.isNull("targetTemp")) null
            else roomObj.optDouble("targetTemp", Double.NaN).takeUnless { it.isNaN() }

            val heating = roomObj.optBoolean("heatingActive", false) ||
                (targetTemp != null && targetTemp > currentTemp + 0.2)

            val prevObj = state.optJSONObject(roomKey)
            val prevTemp = prevObj?.optDouble("temp", Double.NaN)?.takeUnless { it.isNaN() }
            val prevTs = prevObj?.optLong("ts", 0L) ?: 0L

            // Same data as last run (cache not refreshed yet): nothing to do.
            if (prevObj != null && sampleTs <= prevTs) continue

            val gap = sampleTs - prevTs
            if (prevTemp != null && gap in MIN_SAMPLE_GAP_MS..MAX_SAMPLE_GAP_MS) {
                val drop = prevTemp - currentTemp
                val cooldownUntil = cooldownPrefs.getLong("cooldown_$roomKey", 0L)
                if (heating && drop >= DROP_THRESHOLD_C && now >= cooldownUntil) {
                    val notifId = 4000 + (roomKey.hashCode() and 0x7FFFFFFF) % 1000
                    val minutes = (gap / 60_000L).coerceAtLeast(1L)

                    val launchIntent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                    val contentIntent = launchIntent?.let {
                        PendingIntent.getActivity(
                            ctx, notifId, it,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    }

                    val dismissIntent = PendingIntent.getBroadcast(
                        ctx,
                        notifId,
                        Intent(ACTION_DISMISS)
                            .setPackage(ctx.packageName)
                            .putExtra(EXTRA_NOTIFICATION_ID, notifId),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val turnOffIntent = PendingIntent.getBroadcast(
                        ctx,
                        notifId + 1,
                        Intent(ACTION_TURN_OFF_30_MIN)
                            .setPackage(ctx.packageName)
                            .putExtra(EXTRA_NOTIFICATION_ID, notifId)
                            .putExtra(EXTRA_HOME_ID, homeId),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val text = "$name: de ${"%.1f".format(prevTemp)}° a " +
                        "${"%.1f".format(currentTemp)}° en $minutes min con la calefacción encendida"

                    val builder = NotificationCompat.Builder(ctx, NetatmoApp.CHANNEL_ANOMALY)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("¿Ventana abierta?")
                        .setContentText(text)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                        .setAutoCancel(true)
                    if (offActionEnabled) builder.addAction(0, "Apagar 30 min", turnOffIntent)
                    builder.addAction(0, "Ignorar", dismissIntent)
                    if (contentIntent != null) builder.setContentIntent(contentIntent)

                    notificationManager.notify(notifId, builder.build())
                    cooldownPrefs.edit()
                        .putLong("cooldown_$roomKey", now + ALERT_COOLDOWN_MS)
                        .apply()
                }
            }

            state.put(roomKey, JSONObject().put("temp", currentTemp).put("ts", sampleTs))
            stateChanged = true
        }

        if (stateChanged) {
            val serialized = state.toString()
            dataStore.edit { it[STATE_KEY] = serialized }
        }

        return Result.success()
    }
}
