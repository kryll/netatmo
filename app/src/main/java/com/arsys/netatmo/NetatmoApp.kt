package com.arsys.netatmo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.arsys.netatmo.service.AdvancedAutomationWorker
import com.arsys.netatmo.service.LocalScheduleManager
import com.arsys.netatmo.service.TemperatureAnomalyWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NetatmoApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var localScheduleManager: LocalScheduleManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        AdvancedAutomationWorker.schedule(this)
        localScheduleManager.scheduleAll()
        TemperatureAnomalyWorker.schedule(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_AUTOMATIONS,
                    "Automatizaciones",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Notificaciones de automatizaciones del termostato" }
            )

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_GEOFENCE,
                    "Geovalla",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Notificaciones de entrada/salida de zona" }
            )

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_BATTERY,
                    "Batería baja",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Alerta cuando la batería de un dispositivo está baja" }
            )

            manager.createNotificationChannel(NotificationChannel(CHANNEL_ANOMALY, "Alertas de temperatura", NotificationManager.IMPORTANCE_HIGH).apply { description = "Alerta cuando la temperatura se desvía del objetivo" })
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_OUTDOOR,
                    "Temperatura exterior",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Automatizaciones por temperatura exterior" }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ADVANCED,
                    "Automatizaciones avanzadas",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Notificaciones de automatizaciones avanzadas" }
            )
        }
    }

    companion object {
        const val CHANNEL_AUTOMATIONS = "channel_automations"
        const val CHANNEL_GEOFENCE = "channel_geofence"
        const val CHANNEL_BATTERY = "channel_battery"
        const val CHANNEL_ANOMALY = "channel_anomaly"
        const val CHANNEL_OUTDOOR = "outdoor_temp"
        const val CHANNEL_ADVANCED = "advanced_automations"
    }
}
