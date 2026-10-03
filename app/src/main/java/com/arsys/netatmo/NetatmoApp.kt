package com.arsys.netatmo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NetatmoApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
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
                    CHANNEL_CALENDAR,
                    "Calendario",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Automatizaciones por eventos de calendario" }
            )
        }
    }

    companion object {
        const val CHANNEL_AUTOMATIONS = "channel_automations"
        const val CHANNEL_GEOFENCE = "channel_geofence"
        const val CHANNEL_CALENDAR = "channel_calendar"
    }
}
