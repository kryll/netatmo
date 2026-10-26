package com.arsys.netatmo.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.arsys.netatmo.domain.model.ThermostatMode
import com.arsys.netatmo.service.VacationModeWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

data class VacationState(
    val enabled: Boolean = false,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val frozenAlerts: Boolean = true
)

@Singleton
class VacationModeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val thermostatRepository: ThermostatRepository,
    private val authRepository: AuthRepository
) {
    private companion object {
        val VACATION_ENABLED = booleanPreferencesKey("vacation_enabled")
        val VACATION_START_MS = longPreferencesKey("vacation_start_ms")
        val VACATION_END_MS = longPreferencesKey("vacation_end_ms")
        val VACATION_FROZEN_ALERTS = booleanPreferencesKey("vacation_frozen_alerts")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun Preferences.toVacationState() = VacationState(
        enabled = this[VACATION_ENABLED] ?: false,
        startMs = this[VACATION_START_MS] ?: 0L,
        endMs = this[VACATION_END_MS] ?: 0L,
        frozenAlerts = this[VACATION_FROZEN_ALERTS] ?: true
    )

    val state: StateFlow<VacationState> = context.dataStore.data
        .map { prefs -> prefs.toVacationState() }
        .stateIn(scope, SharingStarted.Eagerly, VacationState())

    private suspend fun currentState(): VacationState =
        context.dataStore.data.first().toVacationState()

    /**
     * Sends frost guard (hg) to Netatmo until [endMs] when the period has already started and
     * only then stores the vacation as enabled; a future start is applied later by [VacationModeWorker].
     * setthermmode cannot set a custom setpoint for hg, so Netatmo uses the frost-guard
     * temperature configured on the installation.
     */
    suspend fun activate(startMs: Long, endMs: Long): ApiResult<Unit> {
        val startsNow = startMs <= System.currentTimeMillis()
        if (startsNow) {
            val result = sendFrostGuard(endMs)
            if (result is ApiResult.Error) return result
        }
        context.dataStore.edit { prefs ->
            prefs[VACATION_ENABLED] = true
            prefs[VACATION_START_MS] = startMs
            prefs[VACATION_END_MS] = endMs
        }
        val now = System.currentTimeMillis()
        VacationModeWorker.cancel(context)
        if (!startsNow) {
            VacationModeWorker.schedule(context, VacationModeWorker.ACTION_START, startMs - now)
        }
        VacationModeWorker.schedule(context, VacationModeWorker.ACTION_END, endMs - now)
        return ApiResult.Success(Unit)
    }

    /**
     * Stores a future vacation without sending any mode: the advanced automation created by the
     * caller owns the frost-guard start, and the vacation_end worker restores the schedule and
     * clears the flag at [endMs].
     */
    suspend fun scheduleFuture(startMs: Long, endMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[VACATION_ENABLED] = true
            prefs[VACATION_START_MS] = startMs
            prefs[VACATION_END_MS] = endMs
        }
        VacationModeWorker.cancel(context)
        VacationModeWorker.schedule(context, VacationModeWorker.ACTION_END, endMs - System.currentTimeMillis())
    }

    suspend fun deactivate(): ApiResult<Unit> {
        if (currentState().enabled) {
            val result = restoreSchedule()
            if (result is ApiResult.Error) return result
        }
        VacationModeWorker.cancel(context)
        markInactive()
        return ApiResult.Success(Unit)
    }

    suspend fun applyScheduledStart(): ApiResult<Unit> {
        val vacation = currentState()
        if (!vacation.enabled) return ApiResult.Success(Unit)
        if (vacation.endMs <= System.currentTimeMillis()) {
            markInactive()
            return ApiResult.Success(Unit)
        }
        return sendFrostGuard(vacation.endMs)
    }

    suspend fun finishVacation(): ApiResult<Unit> {
        if (!currentState().enabled) return ApiResult.Success(Unit)
        val result = restoreSchedule()
        if (result is ApiResult.Success) markInactive()
        return result
    }

    suspend fun markInactive() {
        context.dataStore.edit { prefs ->
            prefs[VACATION_ENABLED] = false
        }
    }

    private suspend fun sendFrostGuard(endMs: Long): ApiResult<Unit> {
        val homeId = authRepository.selectedHomeId.first()
            ?: return ApiResult.Error("No hay ninguna instalacion seleccionada")
        return thermostatRepository.setHomeMode(homeId, ThermostatMode.FROST_GUARD.apiValue, endMs / 1000)
    }

    private suspend fun restoreSchedule(): ApiResult<Unit> {
        val homeId = authRepository.selectedHomeId.first()
            ?: return ApiResult.Error("No hay ninguna instalacion seleccionada")
        return thermostatRepository.setHomeMode(homeId, ThermostatMode.SCHEDULE.apiValue)
    }

    suspend fun setFrozenAlerts(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[VACATION_FROZEN_ALERTS] = enabled
        }
    }

    fun isCurrentlyActive(): Boolean {
        val s = state.value
        return s.enabled && System.currentTimeMillis() in s.startMs..s.endMs
    }
}
