package com.arsys.netatmo.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, GeofenceTransitionService::class.java)
        serviceIntent.putExtras(intent)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
