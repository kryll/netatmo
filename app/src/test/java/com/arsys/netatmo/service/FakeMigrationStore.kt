package com.arsys.netatmo.service

import com.arsys.netatmo.data.local.entities.AdvancedAutomationEntity
import com.arsys.netatmo.data.local.entities.AutomationEntity
import com.arsys.netatmo.data.local.entities.CalendarAutomationEntity
import com.arsys.netatmo.data.local.entities.GeofenceEntity
import com.arsys.netatmo.data.local.entities.ZoneEntity

/**
 * In-memory [MigrationStore] whose [atomic] applies the block against a copy of the state
 * and only commits it on success: a throw anywhere inside discards every write, exactly
 * like a rolled-back Room transaction.
 */
open class FakeMigrationStore : MigrationStore {

    val automations = mutableListOf<AutomationEntity>()
    val geofenceRows = mutableListOf<GeofenceEntity>()
    val calendarRows = mutableListOf<CalendarAutomationEntity>()
    val advanced = mutableListOf<AdvancedAutomationEntity>()
    val zones = mutableListOf<ZoneEntity>()
    var homeId: String? = "home-1"
    var vacation = Triple(false, 0L, 0L)

    // External-effect call log, in order.
    val effects = mutableListOf<String>()

    private fun snapshot() = State(
        automations.toList(), geofenceRows.toList(), calendarRows.toList(),
        advanced.toList(), zones.toList()
    )

    private data class State(
        val automations: List<AutomationEntity>,
        val geofences: List<GeofenceEntity>,
        val calendars: List<CalendarAutomationEntity>,
        val advanced: List<AdvancedAutomationEntity>,
        val zones: List<ZoneEntity>
    )

    override suspend fun <T> atomic(block: suspend () -> T): T {
        val backup = snapshot()
        return try {
            block()
        } catch (e: Throwable) {
            automations.clear(); automations.addAll(backup.automations)
            geofenceRows.clear(); geofenceRows.addAll(backup.geofences)
            calendarRows.clear(); calendarRows.addAll(backup.calendars)
            advanced.clear(); advanced.addAll(backup.advanced)
            zones.clear(); zones.addAll(backup.zones)
            throw e
        }
    }

    override suspend fun scheduleAutomations(): List<AutomationEntity> =
        automations.filter { it.type == "SCHEDULE" }

    override suspend fun geofences(): List<GeofenceEntity> = geofenceRows.toList()

    override suspend fun calendarAutomations(): List<CalendarAutomationEntity> = calendarRows.toList()

    override suspend fun automationById(id: Long): AutomationEntity? = automations.firstOrNull { it.id == id }

    override suspend fun selectedHomeId(): String? = homeId

    override suspend fun hasEquivalent(marker: String): Boolean = advanced.any { it.description == marker }

    override suspend fun insertAdvanced(entity: AdvancedAutomationEntity) {
        onWrite()
        advanced.add(entity)
    }

    override suspend fun deleteAdvancedWithPrefix(markerPrefix: String) {
        onWrite()
        advanced.removeAll { it.description.startsWith(markerPrefix) }
    }

    override suspend fun setClassicEnabled(id: Long, enabled: Boolean) {
        onWrite()
        automations.replaceAll { if (it.id == id) it.copy(enabled = enabled) else it }
    }

    override suspend fun updateCalendarClassic(entity: CalendarAutomationEntity) {
        onWrite()
        calendarRows.replaceAll { if (it.id == entity.id) entity else it }
    }

    override suspend fun upsertZone(zone: ZoneEntity) {
        onWrite()
        zones.removeAll { it.id == zone.id }
        zones.add(zone)
    }

    /** Test hook: override or set to throw when a write happens inside a transaction. */
    protected open fun onWrite() {}

    override suspend fun removeClassicGeofence(geofenceId: String) { effects.add("removeGeofence:$geofenceId") }
    override suspend fun registerClassicGeofence(geofence: GeofenceEntity) { effects.add("registerGeofence:${geofence.id}") }
    override suspend fun syncZones() { effects.add("syncZones") }
    override fun scheduleLocalScheduleWorker() { effects.add("scheduleLocal") }
    override fun scheduleCalendarWorker() { effects.add("scheduleCalendar") }
    override fun scheduleVacationStart(delayMs: Long) { effects.add("vacationStart:$delayMs") }
    override fun cancelVacationStart() { effects.add("cancelVacationStart") }

    override suspend fun vacationState(): Triple<Boolean, Long, Long> = vacation
}

/** Fails on the [failOnWrite]-th transactional write (1-based), simulating a crash mid-cutover. */
class FailingWriteStore(private val failOnWrite: Int) : FakeMigrationStore() {
    private var writes = 0
    val writesBeforeFailure: Int get() = writes

    override fun onWrite() {
        writes++
        if (writes == failOnWrite) throw RuntimeException("injected failure on write $failOnWrite")
    }
}

class FakeMigrationFlags : MigrationFlags {
    private val marked = mutableSetOf<String>()
    private val reverted = mutableSetOf<String>()
    override var completedVersion: Int = 0

    override fun isMarked(key: String): Boolean = key in marked
    override fun mark(key: String) { marked.add(key) }
    override fun isReverted(key: String): Boolean = key in reverted
    override fun markReverted(key: String) { reverted.add(key) }
}
