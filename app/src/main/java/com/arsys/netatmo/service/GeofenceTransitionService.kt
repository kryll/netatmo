package com.arsys.netatmo.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.arsys.netatmo.MainActivity
import com.arsys.netatmo.NetatmoApp
import com.arsys.netatmo.R
import com.arsys.netatmo.data.repository.AutomationRepository
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
class GeofenceTransitionService : LifecycleService() {

    @Inject lateinit var automationRepository: AutomationRepository
    @Inject lateinit var thermostatRepository: ThermostatRepository
    @Inject lateinit var authRepository: AuthRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        intent?.let { handleGeofenceEvent(it) }
        return START_NOT_STICKY
    }

    private fun handleGeofenceEvent(intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) {
            Log.e("GeofenceService", "Error: ${geofencingEvent.errorCode}")
            return
        }

        val transition = geofencingEvent.geofenceTransition
        val triggeredGeofences = geofencingEvent.triggeringGeofences ?: return

        serviceScope.launch {
            triggeredGeofences.forEach { geofence ->
                processGeofenceTrigger(geofence.requestId, transition)
            }
        }
    }

    private suspend fun processGeofenceTrigger(geofenceId: String, transition: Int) {
        val homeId = authRepository.selectedHomeId.first() ?: return

        val geofences = automationRepository.getAllGeofences().first()
        val geofenceEntity = geofences.find { it.id == geofenceId } ?: return
        val automation = automationRepository.getAutomationById(geofenceEntity.automationId) ?: return

        if (!automation.enabled) return

        when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                if (geofenceEntity.triggerOnEnter) {
                    val temp = geofenceEntity.temperatureOnEnter ?: return
                    thermostatRepository.setTemperature(homeId, automation.roomId, temp)
                    automationRepository.setAutomationEnabled(automation.id, true)
                    showNotification("Llegando a ${geofenceEntity.name}", "Temperatura ajustada a ${temp}°C")
                }
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                if (geofenceEntity.triggerOnExit) {
                    val temp = geofenceEntity.temperatureOnExit ?: return
                    thermostatRepository.setTemperature(homeId, automation.roomId, temp)
                    showNotification("Saliendo de ${geofenceEntity.name}", "Temperatura ajustada a ${temp}°C")
                }
            }
        }
    }

    private fun showNotification(title: String, message: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NetatmoApp.CHANNEL_GEOFENCE)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
