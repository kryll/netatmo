package com.arsys.netatmo.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.arsys.netatmo.NetatmoApp
import com.arsys.netatmo.data.api.models.ModuleStatus
import com.arsys.netatmo.data.repository.ApiResult
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.data.repository.ThermostatRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class DataSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val thermostatRepository: ThermostatRepository,
    private val authRepository: AuthRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val homeId = authRepository.selectedHomeId.first() ?: return Result.success()
        val statusResult = thermostatRepository.getHomeStatus(homeId)
        return when (statusResult) {
            is ApiResult.Success -> {
                statusResult.data.modules?.forEach { module ->
                    checkBatteryAndNotify(module)
                }
                Result.success()
            }
            is ApiResult.Error -> Result.retry()
            ApiResult.Loading -> Result.retry()
        }
    }

    private fun checkBatteryAndNotify(module: ModuleStatus) {
        if (module.type == "NAPlug") return
        val raw = module.batteryLevel ?: return

        val pct = if (raw <= 100) raw else (raw - 3500).coerceIn(0, 2500) * 100 / 2500

        val prefs = applicationContext.getSharedPreferences("battery_notifications", Context.MODE_PRIVATE)
        val prefKey = "battery_notified_${module.id}"

        if (pct < 20) {
            val alreadyNotified = prefs.getBoolean(prefKey, false)
            if (!alreadyNotified) {
                sendBatteryNotification(module, pct)
                prefs.edit().putBoolean(prefKey, true).apply()
            }
        } else {
            prefs.edit().remove(prefKey).apply()
        }
    }

    private fun sendBatteryNotification(module: ModuleStatus, pct: Int) {
        val typeName = when (module.type) {
            "NATherm1", "NTH01" -> "Termostato"
            "NRV" -> "Válvula radiador"
            "OTM" -> "Módulo OpenTherm"
            else -> module.type ?: "Módulo"
        }

        val notification = NotificationCompat.Builder(applicationContext, NetatmoApp.CHANNEL_BATTERY)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Batería baja")
            .setContentText("El módulo $typeName tiene la batería al ${pct}%")
            .setAutoCancel(true)
            .build()

        val notificationManager = NotificationManagerCompat.from(applicationContext)
        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(module.id.hashCode(), notification)
        }
    }
}
