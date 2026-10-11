package com.arsys.netatmo.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.arsys.netatmo.data.repository.AutomationRepository
import com.arsys.netatmo.worker.CalendarWorker
import com.arsys.netatmo.worker.DataSyncWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var geofenceManager: GeofenceManager

    @Inject
    lateinit var localScheduleManager: LocalScheduleManager

    @Inject
    lateinit var automationRepository: AutomationRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val workManager = WorkManager.getInstance(context)

        // Periodic data sync every 15 minutes
        val syncRequest = PeriodicWorkRequestBuilder<DataSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            "data_sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )

        // Calendar check every 15 minutes
        val calendarRequest = PeriodicWorkRequestBuilder<CalendarWorker>(15, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "calendar_check",
            ExistingPeriodicWorkPolicy.UPDATE,
            calendarRequest
        )

        localScheduleManager.scheduleAll()
        TemperatureAnomalyWorker.schedule(context)

        // Play Services geofences are lost on reboot/app update: register them again
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                automationRepository.getEnabledGeofences().forEach { geofence ->
                    try {
                        geofenceManager.registerGeofence(geofence)
                    } catch (e: SecurityException) {
                        // Background location permission not granted
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
