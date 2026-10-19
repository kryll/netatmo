package com.arsys.netatmo.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.arsys.netatmo.NetatmoApp
import com.arsys.netatmo.data.repository.ApiResult
import com.arsys.netatmo.data.repository.ThermostatRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/** Handles the quick "+1° / +1,5° / +2°" buttons of the temperature notifications. */
@AndroidEntryPoint
class ThermostatActionReceiver : BroadcastReceiver() {

    @Inject lateinit var thermostatRepository: ThermostatRepository

    companion object {
        const val ACTION_SET_TEMPERATURE = "com.arsys.netatmo.SET_ROOM_TEMPERATURE"
        const val EXTRA_HOME_ID = "home_id"
        const val EXTRA_ROOM_ID = "room_id"
        const val EXTRA_ROOM_NAME = "room_name"
        const val EXTRA_TARGET = "target"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_TEMPERATURE) return
        val homeId = intent.getStringExtra(EXTRA_HOME_ID) ?: return
        val roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: return
        val roomName = intent.getStringExtra(EXTRA_ROOM_NAME) ?: "Termostato"
        val target = intent.getDoubleExtra(EXTRA_TARGET, Double.NaN)
        if (target.isNaN()) return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val result = thermostatRepository.setTemperature(homeId, roomId, target)
                val temp = if (target % 1.0 == 0.0) target.toInt().toString()
                else "%.1f".format(Locale("es", "ES"), target)
                val (title, text) = if (result is ApiResult.Success) {
                    "Termostato actualizado" to "$roomName: ${temp}°C"
                } else {
                    val reason = (result as? ApiResult.Error)?.message ?: "error desconocido"
                    "No se pudo cambiar la temperatura" to "$roomName: $reason"
                }
                // Same id: replaces the original notification, which also removes its buttons.
                val notification = NotificationCompat.Builder(context, NetatmoApp.CHANNEL_ADVANCED)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(context).notify(notificationId, notification)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
