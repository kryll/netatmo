package com.arsys.netatmo.service

import android.content.Context
import androidx.room.withTransaction
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.arsys.netatmo.data.local.AppDatabase
import com.arsys.netatmo.data.local.entities.AdvancedAutomationEntity
import com.arsys.netatmo.data.local.entities.AutomationEntity
import com.arsys.netatmo.data.local.entities.CalendarAutomationEntity
import com.arsys.netatmo.data.local.entities.GeofenceEntity
import com.arsys.netatmo.data.local.entities.ZoneEntity
import com.arsys.netatmo.data.repository.AdvancedAutomationRepository
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.data.repository.AutomationRepository
import com.arsys.netatmo.data.repository.ZoneRepository
import com.arsys.netatmo.data.repository.dataStore
import com.arsys.netatmo.worker.CalendarWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Room/WorkManager-backed [MigrationStore]. [atomic] is a real Room transaction. */
@Singleton
class RoomMigrationStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val automationRepository: AutomationRepository,
    private val advancedAutomationRepository: AdvancedAutomationRepository,
    private val zoneRepository: ZoneRepository,
    private val geofenceManager: GeofenceManager,
    private val authRepository: AuthRepository,
    private val localScheduleManager: LocalScheduleManager
) : MigrationStore {

    override suspend fun <T> atomic(block: suspend () -> T): T =
        database.withTransaction(block)

    override suspend fun scheduleAutomations(): List<AutomationEntity> =
        automationRepository.getAutomationsByType("SCHEDULE").first()

    override suspend fun geofences(): List<GeofenceEntity> =
        automationRepository.getAllGeofences().first()

    override suspend fun calendarAutomations(): List<CalendarAutomationEntity> =
        automationRepository.getAllCalendarAutomations().first()

    override suspend fun automationById(id: Long): AutomationEntity? =
        automationRepository.getAutomationById(id)

    override suspend fun selectedHomeId(): String? =
        authRepository.selectedHomeId.firstOrNull()

    override suspend fun hasEquivalent(marker: String): Boolean =
        advancedAutomationRepository.getAllAdvancedAutomations().first()
            .any { it.description == marker }

    override suspend fun insertAdvanced(entity: AdvancedAutomationEntity) {
        advancedAutomationRepository.save(entity)
    }

    override suspend fun deleteAdvancedWithPrefix(markerPrefix: String) {
        advancedAutomationRepository.getAllAdvancedAutomations().first()
            .filter { it.description.startsWith(markerPrefix) }
            .forEach { advancedAutomationRepository.delete(it) }
    }

    override suspend fun setClassicEnabled(id: Long, enabled: Boolean) =
        automationRepository.setAutomationEnabled(id, enabled)

    override suspend fun updateCalendarClassic(entity: CalendarAutomationEntity) =
        automationRepository.updateCalendarAutomation(entity)

    override suspend fun upsertZone(zone: ZoneEntity) =
        database.zoneDao().upsert(zone)

    override suspend fun removeClassicGeofence(geofenceId: String) =
        geofenceManager.removeGeofence(geofenceId)

    override suspend fun registerClassicGeofence(geofence: GeofenceEntity) {
        try {
            geofenceManager.registerGeofence(geofence)
        } catch (e: SecurityException) {
            // Registered again on the next boot once permission is back.
        }
    }

    override suspend fun syncZones() = zoneRepository.syncGeofences()

    override fun scheduleLocalScheduleWorker() = localScheduleManager.scheduleAll()

    override fun scheduleCalendarWorker() {
        val request = PeriodicWorkRequestBuilder<CalendarWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "calendar_check", ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    override fun scheduleVacationStart(delayMs: Long) {
        VacationModeWorker.schedule(context, VacationModeWorker.ACTION_START, delayMs)
    }

    override fun cancelVacationStart() {
        WorkManager.getInstance(context).cancelUniqueWork("vacation_start")
    }

    override suspend fun vacationState(): Triple<Boolean, Long, Long> {
        val prefs = context.dataStore.data.first()
        val enabled = prefs[androidx.datastore.preferences.core.booleanPreferencesKey("vacation_enabled")] ?: false
        val startMs = prefs[androidx.datastore.preferences.core.longPreferencesKey("vacation_start_ms")] ?: 0L
        val endMs = prefs[androidx.datastore.preferences.core.longPreferencesKey("vacation_end_ms")] ?: 0L
        return Triple(enabled, startMs, endMs)
    }
}

/** SharedPreferences-backed [MigrationFlags]. */
@Singleton
class PrefsMigrationFlags @Inject constructor(
    @ApplicationContext context: Context
) : MigrationFlags {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun isMarked(key: String): Boolean = prefs.getBoolean(key, false)
    override fun mark(key: String) = prefs.edit().putBoolean(key, true).apply()
    override fun isReverted(key: String): Boolean = prefs.getBoolean("reverted_$key", false)
    override fun markReverted(key: String) = prefs.edit().putBoolean("reverted_$key", true).apply()

    override var completedVersion: Int
        get() = prefs.getInt(KEY_VERSION, 0)
        set(value) = prefs.edit().putInt(KEY_VERSION, value).apply()

    private companion object {
        const val PREFS = "classic_migration"
        const val KEY_VERSION = "version"
    }
}
