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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Handles the actions of the open-window notification: "Ignorar" and "Apagar 30 min". */
@AndroidEntryPoint
class OpenWindowActionReceiver : BroadcastReceiver() {

    @Inject lateinit var thermostatRepository: ThermostatRepository

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            OpenWindowWorker.ACTION_DISMISS -> {
                val notificationId = intent.getIntExtra(OpenWindowWorker.EXTRA_NOTIFICATION_ID, -1)
                if (notificationId >= 0) {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                }
            }
            OpenWindowWorker.ACTION_TURN_OFF_30_MIN -> turnHeatingOff30Min(context, intent)
        }
    }

    /**
     * Turns the heating off for 30 minutes: sets the home to frost guard ("hg") with an
     * endtime, so Netatmo itself returns the home to schedule mode when it ends. There is
     * no permanent manual mode left behind and nothing local to undo. The official Energy
     * API has no "off" mode with endtime: setthermmode only accepts schedule/away/hg and
     * endtime is only valid for away/hg.
     */
    private fun turnHeatingOff30Min(context: Context, intent: Intent) {
        val homeId = intent.getStringExtra(OpenWindowWorker.EXTRA_HOME_ID) ?: return
        val notificationId = intent.getIntExtra(OpenWindowWorker.EXTRA_NOTIFICATION_ID, 0)
        val endTime = System.currentTimeMillis() / 1000 + OpenWindowWorker.OFF_DURATION_SECONDS

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val result = thermostatRepository.setHomeMode(homeId, "hg", endTime)
                val (title, text) = if (result is ApiResult.Success) {
                    val resume = SimpleDateFormat("HH:mm", Locale("es", "ES"))
                        .format(Date(endTime * 1000))
                    "Calefacción apagada 30 min" to "Vuelve a la programación a las $resume"
                } else {
                    val reason = (result as? ApiResult.Error)?.message ?: "error desconocido"
                    "No se pudo apagar la calefacción" to reason
                }
                // Same id: replaces the open-window notification, which also removes its buttons.
                val notification = NotificationCompat.Builder(context, NetatmoApp.CHANNEL_ANOMALY)
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
