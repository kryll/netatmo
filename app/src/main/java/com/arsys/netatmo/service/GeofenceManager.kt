package com.arsys.netatmo.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.arsys.netatmo.data.local.entities.GeofenceEntity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val geofencingClient: GeofencingClient =
        LocationServices.getGeofencingClient(context)

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceTransitionService::class.java)
        PendingIntent.getService(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    fun registerGeofence(entity: GeofenceEntity) {
        val transitions = mutableListOf<Int>().apply {
            if (entity.triggerOnEnter) add(Geofence.GEOFENCE_TRANSITION_ENTER)
            if (entity.triggerOnExit) add(Geofence.GEOFENCE_TRANSITION_EXIT)
        }
        if (transitions.isEmpty()) return

        val geofence = Geofence.Builder()
            .setRequestId(entity.id)
            .setCircularRegion(entity.latitude, entity.longitude, entity.radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(transitions.reduce { acc, i -> acc or i })
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        geofencingClient.addGeofences(request, geofencePendingIntent)
    }

    fun removeGeofence(geofenceId: String) {
        geofencingClient.removeGeofences(listOf(geofenceId))
    }
}
