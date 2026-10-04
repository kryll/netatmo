package com.arsys.netatmo.service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.arsys.netatmo.MainActivity
import com.arsys.netatmo.NetatmoApp
import com.arsys.netatmo.R
import com.arsys.netatmo.data.local.entities.AutomationLogEntity
import com.arsys.netatmo.data.repository.AutomationRepository
import com.arsys.netatmo.data.repository.ApiResult
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.data.repository.ThermostatRepository
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceTransitionService : Service() {

    @Inject lateinit var automationRepository: AutomationRepository
    @Inject lateinit var thermostatRepository: ThermostatRepository
    @Inject lateinit var authRepository: AuthRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (intent != null) {
            serviceScope.launch {
                handleGeofenceEvent(intent)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val notification = NotificationCompat.Builder(this, NetatmoApp.CHANNEL_GEOFENCE)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Verificando ubicación")
            .setContentText("Comprobando automatización de geovalla...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 9001
    }

    private suspend fun handleGeofenceEvent(intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) {
            Log.e("GeofenceService", "Error: ${geofencingEvent.errorCode}")
            return
        }

        val transition = geofencingEvent.geofenceTransition
        val triggeredGeofences = geofencingEvent.triggeringGeofences ?: return

        triggeredGeofences.forEach { geofence ->
            processGeofenceTrigger(geofence.requestId, transition)
        }
    }

    private suspend fun processGeofenceTrigger(geofenceId: String, transition: Int) {
        val homeId = authRepository.selectedHomeId.first() ?: return

        val geofences = automationRepository.getAllGeofences().first()
        val geofenceEntity = geofences.find { it.id == geofenceId } ?: return
        val automation = automationRepository.getAutomationById(geofenceEntity.automationId) ?: return

        if (!automation.enabled) return

        val now = System.currentTimeMillis()
        when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                if (geofenceEntity.triggerOnEnter) {
                    val temp = geofenceEntity.temperatureOnEnter ?: return
                    val result = thermostatRepository.setTemperature(homeId, automation.roomId, temp)
                    val success = result is ApiResult.Success
                    val errorMsg = if (result is ApiResult.Error) result.message else null
                    automationRepository.updateLastTriggered(automation.id, now)
                    automationRepository.logExecution(AutomationLogEntity(
                        automationId = automation.id,
                        automationName = automation.name,
                        triggerType = "ENTER",
                        timestamp = now,
                        success = success,
                        errorMessage = errorMsg
                    ))
                    if (success) {
                        showNotification("Llegando a ${geofenceEntity.name}", "Temperatura ajustada a ${temp}°C")
                    } else {
                        showNotification("Llegando a ${geofenceEntity.name}", "Error al ajustar temperatura: $errorMsg")
                    }
                }
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                if (geofenceEntity.triggerOnExit) {
                    val temp = geofenceEntity.temperatureOnExit ?: return
                    val result = thermostatRepository.setTemperature(homeId, automation.roomId, temp)
                    val success = result is ApiResult.Success
                    val errorMsg = if (result is ApiResult.Error) result.message else null
                    automationRepository.updateLastTriggered(automation.id, now)
                    automationRepository.logExecution(AutomationLogEntity(
                        automationId = automation.id,
                        automationName = automation.name,
                        triggerType = "EXIT",
                        timestamp = now,
                        success = success,
                        errorMessage = errorMsg
                    ))
                    if (success) {
                        showNotification("Saliendo de ${geofenceEntity.name}", "Temperatura ajustada a ${temp}°C")
                    } else {
                        showNotification("Saliendo de ${geofenceEntity.name}", "Error al ajustar temperatura: $errorMsg")
                    }
                }
            }
        }
    }

    private fun showNotification(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NetatmoApp.CHANNEL_GEOFENCE)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(title.hashCode(), notification)
    }
}
