package com.arsys.netatmo.service

import com.arsys.netatmo.data.local.entities.AdvancedAutomationEntity
import com.arsys.netatmo.data.local.entities.AutomationEntity
import com.arsys.netatmo.data.local.entities.CalendarAutomationEntity
import com.arsys.netatmo.data.local.entities.GeofenceEntity
import com.arsys.netatmo.data.local.entities.ZoneEntity

/**
 * Persistence + side-effect seam for [ClassicAutomationMigrator]. The production
 * implementation is Room/WorkManager-backed; tests use an in-memory one whose [atomic]
 * discards the whole block on failure, which lets failure-injection tests prove that a
 * crash at any point never leaves both a classic and its equivalent active (or none).
 */
interface MigrationStore {

    /** Runs [block] as one atomic unit: either every write inside lands or none does. */
    suspend fun <T> atomic(block: suspend () -> T): T

    // ── Reads ────────────────────────────────────────────────────────────────
    suspend fun scheduleAutomations(): List<AutomationEntity>
    suspend fun geofences(): List<GeofenceEntity>
    suspend fun calendarAutomations(): List<CalendarAutomationEntity>
    suspend fun automationById(id: Long): AutomationEntity?
    suspend fun selectedHomeId(): String?
    suspend fun hasEquivalent(marker: String): Boolean

    // ── Writes (only inside [atomic]) ────────────────────────────────────────
    suspend fun insertAdvanced(entity: AdvancedAutomationEntity)
    suspend fun deleteAdvancedWithPrefix(markerPrefix: String)
    suspend fun setClassicEnabled(id: Long, enabled: Boolean)
    suspend fun updateCalendarClassic(entity: CalendarAutomationEntity)
    suspend fun upsertZone(zone: ZoneEntity)

    // ── External effects (outside [atomic], idempotent, self-heal on retry) ──
    suspend fun removeClassicGeofence(geofenceId: String)
    suspend fun registerClassicGeofence(geofence: GeofenceEntity)
    suspend fun syncZones()
    fun scheduleLocalScheduleWorker()
    fun scheduleCalendarWorker()
    fun scheduleVacationStart(delayMs: Long)
    fun cancelVacationStart()

    // ── Vacation DataStore (frost-watch keys; never cleared by migration) ────
    suspend fun vacationState(): Triple<Boolean, Long, Long> // enabled, startMs, endMs
}

/** Migration progress flags, so tests can run without Android SharedPreferences. */
interface MigrationFlags {
    fun isMarked(key: String): Boolean
    fun mark(key: String)
    fun isReverted(key: String): Boolean
    fun markReverted(key: String)
    var completedVersion: Int
}
