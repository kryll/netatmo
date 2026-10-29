package com.arsys.netatmo.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/** Dismisses an open-window notification when the user taps "Ignorar". */
class OpenWindowActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != OpenWindowWorker.ACTION_DISMISS) return
        val notificationId = intent.getIntExtra(OpenWindowWorker.EXTRA_NOTIFICATION_ID, -1)
        if (notificationId >= 0) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
    }
}
