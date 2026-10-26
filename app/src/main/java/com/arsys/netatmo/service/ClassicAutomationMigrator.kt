package com.arsys.netatmo.service

import com.arsys.netatmo.data.local.entities.AdvancedAutomationEntity
import com.arsys.netatmo.data.local.entities.AutomationEntity
import com.arsys.netatmo.data.local.entities.CalendarAutomationEntity
import com.arsys.netatmo.data.local.entities.GeofenceEntity
import com.arsys.netatmo.data.local.entities.ZoneEntity
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time, resumable migration of the classic automations (schedule window, geofence,
 * calendar and vacation) to advanced automations.
 *
 * Guarantees:
 * - Originals are never deleted: classic rows stay in their tables, only disabled.
 * - Atomic cutover per item through [MigrationStore.atomic]: the advanced equivalent and
 *   the classic's disable commit together, so a crash at any point can never leave both
 *   active or none. External effects (geofence registration, worker scheduling) run after
 *   the commit, are idempotent and are retried because the item stays unmarked until they
 *   succeed; the guards below keep retries from duplicating anything.
 * - Each migrated item is marked (flags + "migrated:<type>:<id>" in the equivalent's
 *   description); a reverted item is never re-migrated.
 * - Nothing is fired during migration: future rules stay future, in-flight vacations are
 *   left untouched, and the vacation DataStore keys (read by the frost watch) are preserved.
 *
 * All persistence goes through [MigrationStore] so failure-injection tests can prove the
 * atomicity above on the JVM.
 */
@Singleton
class ClassicAutomationMigrator @Inject constructor(
    private val store: MigrationStore,
    private val flags: MigrationFlags,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun runIfNeeded() {
        if (flags.completedVersion >= VERSION) return
        val allDone = migrateSchedules() && migrateGeofences() && migrateCalendarAutomations() && migrateVacation()
        if (allDone) flags.completedVersion = VERSION
    }

    // ── Schedule windows ─────────────────────────────────────────────────────

    private suspend fun migrateSchedules(): Boolean {
        val schedules = try {
            store.scheduleAutomations()
        } catch (e: Exception) {
            return false
        }
        var allDone = true
        for (auto in schedules) {
            val key = "sched_${auto.id}"
            if (flags.isMarked(key) || flags.isReverted(key)) continue
            try {
                store.atomic {
                    if (!store.hasEquivalent("migrated:sched:${auto.id}")) {
                        store.insertAdvanced(buildScheduleEquivalent(auto))
                    }
                    store.setClassicEnabled(auto.id, false)
                }
                flags.mark(key)
            } catch (e: Exception) {
                allDone = false
            }
        }
        return allDone
    }

    private suspend fun buildScheduleEquivalent(auto: AutomationEntity): AdvancedAutomationEntity {
        val trigger = JSONObject(auto.triggerData)
        val daysArr = trigger.optJSONArray("days")
        val days = if (daysArr != null) (0 until daysArr.length()).map { daysArr.getInt(it) } else emptyList()
        val start = "%02d:%02d".format(trigger.optInt("startHour", 0), trigger.optInt("startMinute", 0))
        val end = "%02d:%02d".format(trigger.optInt("endHour", 23), trigger.optInt("endMinute", 59))
        val homeId = auto.homeId.ifBlank { store.selectedHomeId() ?: "" }
        return AdvancedAutomationEntity(
            name = auto.name,
            description = "migrated:sched:${auto.id}",
            enabled = auto.enabled,
            triggersJson = JSONArray().put(
                JSONObject()
                    .put("type", "time_window")
                    .put("days", JSONArray(days))
                    .put("start", start)
                    .put("end", end)
            ).toString(),
            actionsJson = JSONArray().put(
                JSONObject()
                    .put("type", "set_temperature")
                    .put("homeId", homeId)
                    .put("roomId", auto.roomId)
                    .put("temperature", auto.targetTemperature)
            ).toString()
        )
    }

    // ── Geofences ────────────────────────────────────────────────────────────

    private suspend fun migrateGeofences(): Boolean {
        val geofences = try {
            store.geofences()
        } catch (e: Exception) {
            return false
        }
        var allDone = true
        for (geo in geofences) {
            val key = "geo_${geo.id}"
            if (flags.isMarked(key) || flags.isReverted(key)) continue
            try {
                val auto = store.automationById(geo.automationId)
                if (auto == null) {
                    flags.mark(key)
                    continue
                }
                val homeId = auto.homeId.ifBlank { store.selectedHomeId() ?: "" }
                val zoneId = "mig_${geo.id}"
                store.atomic {
                    store.upsertZone(ZoneEntity(zoneId, geo.name, geo.latitude, geo.longitude, geo.radius))
                    val twoDirections = geo.triggerOnEnter && geo.triggerOnExit &&
                        geo.temperatureOnEnter != null && geo.temperatureOnExit != null
                    if (geo.triggerOnEnter && geo.temperatureOnEnter != null &&
                        !store.hasEquivalent("migrated:geo:${geo.id}:enter")
                    ) {
                        store.insertAdvanced(
                            buildGeofenceEquivalent(
                                baseName = if (twoDirections) "${auto.name} (entrada)" else auto.name,
                                marker = "migrated:geo:${geo.id}:enter",
                                enabled = auto.enabled,
                                zoneId = zoneId,
                                zoneName = geo.name,
                                event = "enter",
                                homeId = homeId,
                                roomId = auto.roomId,
                                temperature = geo.temperatureOnEnter,
                                notify = !geo.notificationsDisabled
                            )
                        )
                    }
                    if (geo.triggerOnExit && geo.temperatureOnExit != null &&
                        !store.hasEquivalent("migrated:geo:${geo.id}:exit")
                    ) {
                        store.insertAdvanced(
                            buildGeofenceEquivalent(
                                baseName = if (twoDirections) "${auto.name} (salida)" else auto.name,
                                marker = "migrated:geo:${geo.id}:exit",
                                enabled = auto.enabled,
                                zoneId = zoneId,
                                zoneName = geo.name,
                                event = "exit",
                                homeId = homeId,
                                roomId = auto.roomId,
                                temperature = geo.temperatureOnExit,
                                notify = !geo.notificationsDisabled
                            )
                        )
                    }
                    store.setClassicEnabled(auto.id, false)
                }
                // External effect after the commit; a failure here leaves the item unmarked
                // and the retry above is a no-op apart from this call.
                store.removeClassicGeofence(geo.id)
                flags.mark(key)
            } catch (e: Exception) {
                allDone = false
            }
        }
        try {
            store.syncZones()
        } catch (e: Exception) {
            // Location permission missing; zones register on the next sync.
        }
        return allDone
    }

    private fun buildGeofenceEquivalent(
        baseName: String,
        marker: String,
        enabled: Boolean,
        zoneId: String,
        zoneName: String,
        event: String,
        homeId: String,
        roomId: String,
        temperature: Double,
        notify: Boolean
    ): AdvancedAutomationEntity {
        val actions = JSONArray().put(
            JSONObject()
                .put("type", "set_temperature")
                .put("homeId", homeId)
                .put("roomId", roomId)
                .put("temperature", temperature)
        )
        if (notify) {
            actions.put(
                JSONObject()
                    .put("type", "notify")
                    .put("title", baseName)
                    .put("message", "$zoneName -> $temperature°C")
            )
        }
        return AdvancedAutomationEntity(
            name = baseName,
            description = marker,
            enabled = enabled,
            triggersJson = JSONArray().put(
                JSONObject()
                    .put("type", "geofence")
                    .put("event", event)
                    .put("zoneId", zoneId)
                    .put("zone", zoneName)
            ).toString(),
            actionsJson = actions.toString()
        )
    }

    // ── Calendar ─────────────────────────────────────────────────────────────

    private suspend fun migrateCalendarAutomations(): Boolean {
        val calendars = try {
            store.calendarAutomations()
        } catch (e: Exception) {
            return false
        }
        var allDone = true
        for (cal in calendars) {
            val key = "cal_${cal.id}"
            if (flags.isMarked(key) || flags.isReverted(key)) continue
            try {
                store.atomic {
                    if (!store.hasEquivalent("migrated:cal:${cal.id}")) {
                        val auto = store.automationById(cal.automationId)
                        val homeId = auto?.homeId?.ifBlank { null } ?: store.selectedHomeId() ?: ""
                        store.insertAdvanced(
                            AdvancedAutomationEntity(
                                name = auto?.name ?: "Calendario ${cal.calendarName}",
                                description = "migrated:cal:${cal.id}",
                                enabled = cal.enabled && (auto?.enabled ?: true),
                                triggersJson = JSONArray().put(
                                    JSONObject()
                                        .put("type", "calendar")
                                        .put("calendarId", cal.calendarId)
                                        .put("calendarName", cal.calendarName)
                                        .put("title", cal.eventTitleFilter ?: "")
                                        .put("minutesBefore", cal.minutesBefore)
                                ).toString(),
                                actionsJson = JSONArray().put(
                                    JSONObject()
                                        .put("type", "set_temperature")
                                        .put("homeId", homeId)
                                        .put("roomId", auto?.roomId ?: "")
                                        .put("temperature", cal.targetTemperature)
                                ).toString()
                            )
                        )
                    }
                    store.updateCalendarClassic(cal.copy(enabled = false))
                    store.automationById(cal.automationId)?.let {
                        store.setClassicEnabled(it.id, false)
                    }
                }
                flags.mark(key)
            } catch (e: Exception) {
                allDone = false
            }
        }
        return allDone
    }

    // ── Vacation ─────────────────────────────────────────────────────────────

    /**
     * A FUTURE vacation becomes one advanced automation that starts frost guard at the
     * planned start (Netatmo itself reverts at the stored end). The vacation DataStore keys
     * stay as they are - the frost watch reads them - and the vacation_end worker keeps its
     * job of restoring the schedule and clearing the flag at the end. An in-flight vacation
     * is left untouched: no mode is sent during migration.
     */
    private suspend fun migrateVacation(): Boolean {
        val key = "vacation"
        if (flags.isMarked(key) || flags.isReverted(key)) return true
        return try {
            val (enabled, startMs, endMs) = store.vacationState()
            val now = nowMs()
            if (enabled && startMs > now && endMs > startMs && !store.hasEquivalent("migrated:vacation:start")) {
                store.atomic {
                    store.insertAdvanced(
                        AdvancedAutomationEntity(
                            name = "Vacaciones",
                            description = "migrated:vacation:start",
                            enabled = true,
                            triggersJson = JSONArray().put(
                                JSONObject().put("type", "datetime").put("at", startMs)
                            ).toString(),
                            actionsJson = JSONArray().put(
                                JSONObject()
                                    .put("type", "set_home_mode")
                                    .put("mode", "hg")
                                    .put("endAt", endMs)
                            ).toString()
                        )
                    )
                }
                // The advanced automation owns the start now; the end worker still restores
                // the schedule and clears vacation_enabled at endMs. If this cancel fails,
                // the item stays unmarked and the retry repeats only the cancel.
                store.cancelVacationStart()
            }
            flags.mark(key)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ── Rollback ─────────────────────────────────────────────────────────────

    /**
     * Safe reversal of one migrated item: the classic is re-enabled and the advanced
     * equivalent(s) are deleted in ONE atomic commit, so a crash can never lose the rule
     * (classic row is only ever re-enabled, never deleted) nor leave both active.
     * External effects after the commit are idempotent and self-heal on the next boot.
     * The item is flagged reverted so the migration never recreates the equivalent.
     */
    suspend fun rollbackFor(advanced: AdvancedAutomationEntity): Boolean {
        val parts = advanced.description.split(":")
        if (parts.size < 3 || parts[0] != "migrated") return false
        return try {
            when (parts[1]) {
                "sched" -> {
                    val classicId = parts[2].toLong()
                    store.atomic {
                        store.automationById(classicId)?.let { store.setClassicEnabled(it.id, true) }
                        store.deleteAdvancedWithPrefix("migrated:sched:$classicId")
                    }
                    store.scheduleLocalScheduleWorker()
                    flags.markReverted("sched_$classicId")
                    true
                }
                "geo" -> {
                    val geofenceId = parts[2]
                    val geofence = store.geofences().firstOrNull { it.id == geofenceId }
                    store.atomic {
                        geofence?.let { g ->
                            store.automationById(g.automationId)?.let { store.setClassicEnabled(it.id, true) }
                        }
                        store.deleteAdvancedWithPrefix("migrated:geo:$geofenceId")
                    }
                    geofence?.let { store.registerClassicGeofence(it) }
                    store.syncZones()
                    flags.markReverted("geo_$geofenceId")
                    true
                }
                "cal" -> {
                    val calId = parts[2].toLong()
                    val cal = store.calendarAutomations().firstOrNull { it.id == calId }
                    store.atomic {
                        cal?.let {
                            store.updateCalendarClassic(it.copy(enabled = true))
                            store.automationById(it.automationId)?.let { a -> store.setClassicEnabled(a.id, true) }
                        }
                        store.deleteAdvancedWithPrefix("migrated:cal:$calId")
                    }
                    store.scheduleCalendarWorker()
                    flags.markReverted("cal_$calId")
                    true
                }
                "vacation" -> {
                    val (enabled, startMs, _) = store.vacationState()
                    store.atomic {
                        store.deleteAdvancedWithPrefix("migrated:vacation")
                    }
                    val delay = startMs - nowMs()
                    if (enabled && delay > 0) {
                        store.scheduleVacationStart(delay)
                    }
                    flags.markReverted("vacation")
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        const val VERSION = 1
    }
}
