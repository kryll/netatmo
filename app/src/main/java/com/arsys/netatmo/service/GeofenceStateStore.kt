package com.arsys.netatmo.service

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.google.android.gms.location.Geofence
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers whether the user is inside each geofence so a transition only runs its action when
 * the state really changes. Play Services re-sends ENTER every time a geofence is registered
 * while the device is already inside (boot, app update, saving, toggling) and can flap near the
 * border, which would otherwise re-apply the arrival temperature over and over.
 */
@Singleton
class GeofenceStateStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private companion object {
        const val MIN_REPEAT_MS = 10L * 60 * 1000
        fun insideKey(id: String) = booleanPreferencesKey("geofence_inside_$id")
        fun lastKey(id: String, inside: Boolean) =
            longPreferencesKey("geofence_last_${if (inside) "enter" else "exit"}_$id")
    }

    /** Records [transition] and returns true only if it changes the zone state and should run. */
    suspend fun registerTransition(geofenceId: String, transition: Int, now: Long): Boolean {
        val inside = when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> true
            Geofence.GEOFENCE_TRANSITION_EXIT -> false
            else -> return false
        }
        var accepted = false
        dataStore.edit { prefs ->
            val wasInside = prefs[insideKey(geofenceId)]
            val last = prefs[lastKey(geofenceId, inside)] ?: 0L
            if (wasInside != inside && now - last >= MIN_REPEAT_MS) {
                prefs[insideKey(geofenceId)] = inside
                prefs[lastKey(geofenceId, inside)] = now
                accepted = true
            }
        }
        return accepted
    }

    /** Forgets the state, e.g. when the geofence location or radius was edited. */
    suspend fun reset(geofenceId: String) {
        dataStore.edit { prefs ->
            prefs.remove(insideKey(geofenceId))
            prefs.remove(lastKey(geofenceId, true))
            prefs.remove(lastKey(geofenceId, false))
        }
    }
}
