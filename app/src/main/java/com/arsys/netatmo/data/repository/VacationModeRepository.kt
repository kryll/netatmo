package com.arsys.netatmo.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

data class VacationState(
    val enabled: Boolean = false,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val temperature: Double = 15.0
)

@Singleton
class VacationModeRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val VACATION_ENABLED = booleanPreferencesKey("vacation_enabled")
        val VACATION_START_MS = longPreferencesKey("vacation_start_ms")
        val VACATION_END_MS = longPreferencesKey("vacation_end_ms")
        val VACATION_TEMP = floatPreferencesKey("vacation_temp")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val state: StateFlow<VacationState> = context.dataStore.data
        .map { prefs ->
            VacationState(
                enabled = prefs[VACATION_ENABLED] ?: false,
                startMs = prefs[VACATION_START_MS] ?: 0L,
                endMs = prefs[VACATION_END_MS] ?: 0L,
                temperature = (prefs[VACATION_TEMP] ?: 15f).toDouble()
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, VacationState())

    suspend fun activate(startMs: Long, endMs: Long, temp: Double) {
        context.dataStore.edit { prefs ->
            prefs[VACATION_ENABLED] = true
            prefs[VACATION_START_MS] = startMs
            prefs[VACATION_END_MS] = endMs
            prefs[VACATION_TEMP] = temp.toFloat()
        }
    }

    suspend fun deactivate() {
        context.dataStore.edit { prefs ->
            prefs[VACATION_ENABLED] = false
        }
    }

    fun isCurrentlyActive(): Boolean {
        val s = state.value
        return s.enabled && System.currentTimeMillis() in s.startMs..s.endMs
    }
}
