package com.arsys.netatmo.service

import com.arsys.netatmo.data.local.entities.AutomationEntity
import com.arsys.netatmo.data.local.entities.CalendarAutomationEntity
import com.arsys.netatmo.data.local.entities.GeofenceEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicAutomationMigratorTest {

    private fun scheduleClassic(
        id: Long = 1L,
        enabled: Boolean = true,
        days: List<Int> = listOf(1, 2, 3, 4, 5),
        startHour: Int = 7, startMinute: Int = 30,
        endHour: Int = 9, endMinute: Int = 0
    ) = AutomationEntity(
        id = id,
        name = "Horario mañana",
        type = "SCHEDULE",
        enabled = enabled,
        homeId = "home-1",
        roomId = "room-1",
        targetTemperature = 21.5,
        triggerData = JSONObject()
            .put("days", JSONArray(days))
            .put("startHour", startHour).put("startMinute", startMinute)
            .put("endHour", endHour).put("endMinute", endMinute)
            .toString()
    )

    // ── Schedule migration ───────────────────────────────────────────────────

    @Test
    fun `schedule migrates to an equivalent time_window automation`() = runBlocking {
        val store = FakeMigrationStore()
        store.automations.add(scheduleClassic())
        val flags = FakeMigrationFlags()

        ClassicAutomationMigrator(store, flags).runIfNeeded()

        assertFalse(store.automations[0].enabled)
        assertEquals(1, store.advanced.size)
        val migrated = store.advanced[0]
        assertEquals("migrated:sched:1", migrated.description)
        assertTrue(migrated.enabled)
        val trigger = JSONArray(migrated.triggersJson).getJSONObject(0)
        assertEquals("time_window", trigger.getString("type"))
        assertEquals("07:30", trigger.getString("start"))
        assertEquals("09:00", trigger.getString("end"))
        assertEquals(listOf(1, 2, 3, 4, 5), (0 until trigger.getJSONArray("days").length()).map { trigger.getJSONArray("days").getInt(it) })
        val action = JSONArray(migrated.actionsJson).getJSONObject(0)
        assertEquals("set_temperature", action.getString("type"))
        assertEquals(21.5, action.getDouble("temperature"), 0.001)
        assertEquals("room-1", action.getString("roomId"))
        assertEquals(ClassicAutomationMigrator.VERSION, flags.completedVersion)
    }

    @Test
    fun `failure creating the equivalent keeps the classic active and retries cleanly`() = runBlocking {
        val store = FailingWriteStore(failOnWrite = 1) // insertAdvanced fails
        store.automations.add(scheduleClassic())
        val flags = FakeMigrationFlags()
        val migrator = ClassicAutomationMigrator(store, flags)

        migrator.runIfNeeded()
        assertTrue(store.automations[0].enabled) // classic untouched
        assertTrue(store.advanced.isEmpty())
        assertEquals(0, flags.completedVersion)

        migrator.runIfNeeded() // failure was one-shot: resume completes
        assertFalse(store.automations[0].enabled)
        assertEquals(1, store.advanced.size)
        assertEquals(ClassicAutomationMigrator.VERSION, flags.completedVersion)
    }

    @Test
    fun `failure disabling the classic rolls back the equivalent - never both active`() = runBlocking {
        val store = FailingWriteStore(failOnWrite = 2) // setClassicEnabled fails
        store.automations.add(scheduleClassic())
        val flags = FakeMigrationFlags()
        val migrator = ClassicAutomationMigrator(store, flags)

        migrator.runIfNeeded()
        // Transaction discarded: no half-created equivalent, classic still active.
        assertTrue(store.automations[0].enabled)
        assertTrue(store.advanced.isEmpty())

        migrator.runIfNeeded()
        assertFalse(store.automations[0].enabled)
        assertEquals(1, store.advanced.size) // no duplicate after retry
    }

    @Test
    fun `already migrated schedules are never duplicated nor re-enabled`() = runBlocking {
        val store = FakeMigrationStore()
        store.automations.add(scheduleClassic())
        val flags = FakeMigrationFlags()
        val migrator = ClassicAutomationMigrator(store, flags)

        migrator.runIfNeeded()
        migrator.runIfNeeded()
        migrator.runIfNeeded()

        assertEquals(1, store.advanced.size)
        assertFalse(store.automations[0].enabled)
    }

    // ── Geofence migration ───────────────────────────────────────────────────

    private fun geofenceClassic(
        geofenceId: String = "geo1",
        automationId: Long = 10L,
        bothDirections: Boolean = true,
        notificationsDisabled: Boolean = false
    ): Pair<AutomationEntity, GeofenceEntity> {
        val auto = AutomationEntity(
            id = automationId,
            name = "Casa",
            type = "GEOFENCE",
            enabled = true,
            homeId = "home-1",
            roomId = "room-1",
            targetTemperature = 0.0,
            triggerData = "{}"
        )
        val geo = GeofenceEntity(
            id = geofenceId,
            automationId = automationId,
            name = "Casa",
            latitude = 42.0,
            longitude = -2.0,
            radius = 150f,
            triggerOnEnter = true,
            triggerOnExit = bothDirections,
            temperatureOnEnter = 21.0,
            temperatureOnExit = if (bothDirections) 18.0 else null,
            notificationsDisabled = notificationsDisabled
        )
        return auto to geo
    }

    @Test
    fun `geofence migrates to zone triggers for both directions atomically`() = runBlocking {
        val store = FakeMigrationStore()
        val (auto, geo) = geofenceClassic()
        store.automations.add(auto)
        store.geofenceRows.add(geo)
        val flags = FakeMigrationFlags()

        ClassicAutomationMigrator(store, flags).runIfNeeded()

        assertFalse(store.automations[0].enabled)
        assertEquals(1, store.zones.size)
        assertEquals("mig_geo1", store.zones[0].id)
        assertEquals(2, store.advanced.size)
        val enter = store.advanced.first { it.description == "migrated:geo:geo1:enter" }
        val exit = store.advanced.first { it.description == "migrated:geo:geo1:exit" }
        assertEquals("Casa (entrada)", enter.name)
        assertEquals("Casa (salida)", exit.name)
        assertTrue(store.effects.contains("removeGeofence:geo1"))
        // notificationsEnabled -> both carry a notify action preserving classic behavior
        assertEquals(2, JSONArray(enter.actionsJson).length())
        assertEquals(2, JSONArray(exit.actionsJson).length())
    }

    @Test
    fun `geofence failure mid-transaction leaves no zone and no partial equivalent`() = runBlocking {
        val store = FailingWriteStore(failOnWrite = 3) // zone + enter + exit inserted, disable fails
        val (auto, geo) = geofenceClassic()
        store.automations.add(auto)
        store.geofenceRows.add(geo)
        val flags = FakeMigrationFlags()
        val migrator = ClassicAutomationMigrator(store, flags)

        migrator.runIfNeeded()
        assertTrue(store.automations[0].enabled)
        assertTrue(store.advanced.isEmpty())
        assertTrue(store.zones.isEmpty())

        migrator.runIfNeeded()
        assertFalse(store.automations[0].enabled)
        assertEquals(2, store.advanced.size)
        assertEquals(1, store.zones.size)
    }

    @Test
    fun `geofence with notificationsDisabled migrates without notify action`() = runBlocking {
        val store = FakeMigrationStore()
        val (auto, geo) = geofenceClassic(bothDirections = false, notificationsDisabled = true)
        store.automations.add(auto)
        store.geofenceRows.add(geo)

        ClassicAutomationMigrator(store, FakeMigrationFlags()).runIfNeeded()

        assertEquals(1, store.advanced.size)
        assertEquals(1, JSONArray(store.advanced[0].actionsJson).length())
    }

    // ── Calendar migration ───────────────────────────────────────────────────

    @Test
    fun `calendar automation maps to a calendar trigger and disables both classic rows`() = runBlocking {
        val store = FakeMigrationStore()
        store.automations.add(
            AutomationEntity(
                id = 20L, name = "Precalentar", type = "CALENDAR", enabled = true,
                homeId = "home-1", roomId = "room-2", targetTemperature = 0.0, triggerData = "{}"
            )
        )
        store.calendarRows.add(
            CalendarAutomationEntity(
                id = 5L, automationId = 20L, calendarId = 42L, calendarName = "Personal",
                eventTitleFilter = "gym", minutesBefore = 45, targetTemperature = 22.0, enabled = true
            )
        )
        val flags = FakeMigrationFlags()

        ClassicAutomationMigrator(store, flags).runIfNeeded()

        assertFalse(store.automations[0].enabled)
        assertFalse(store.calendarRows[0].enabled)
        assertEquals(1, store.advanced.size)
        val trigger = JSONArray(store.advanced[0].triggersJson).getJSONObject(0)
        assertEquals("calendar", trigger.getString("type"))
        assertEquals(42L, trigger.getLong("calendarId"))
        assertEquals("gym", trigger.getString("title"))
        assertEquals(45, trigger.getInt("minutesBefore"))
    }

    @Test
    fun `calendar failure rolls back both disables and the equivalent`() = runBlocking {
        val store = FailingWriteStore(failOnWrite = 2)
        store.automations.add(
            AutomationEntity(
                id = 20L, name = "Precalentar", type = "CALENDAR", enabled = true,
                homeId = "home-1", roomId = "room-2", targetTemperature = 0.0, triggerData = "{}"
            )
        )
        store.calendarRows.add(
            CalendarAutomationEntity(
                id = 5L, automationId = 20L, calendarId = 42L, calendarName = "Personal",
                eventTitleFilter = null, minutesBefore = 30, targetTemperature = 22.0, enabled = true
            )
        )
        val migrator = ClassicAutomationMigrator(store, FakeMigrationFlags())

        migrator.runIfNeeded()
        assertTrue(store.automations[0].enabled)
        assertTrue(store.calendarRows[0].enabled)
        assertTrue(store.advanced.isEmpty())

        migrator.runIfNeeded()
        assertFalse(store.automations[0].enabled)
        assertFalse(store.calendarRows[0].enabled)
        assertEquals(1, store.advanced.size)
    }

    // ── Vacation migration ───────────────────────────────────────────────────

    @Test
    fun `future vacation becomes a datetime automation without firing anything`() = runBlocking {
        val now = 1_000_000L
        val store = FakeMigrationStore()
        store.vacation = Triple(true, now + 3_600_000L, now + 7_200_000L)
        val flags = FakeMigrationFlags()

        ClassicAutomationMigrator(store, flags) { now }.runIfNeeded()

        assertEquals(1, store.advanced.size)
        val migrated = store.advanced[0]
        assertEquals("migrated:vacation:start", migrated.description)
        val trigger = JSONArray(migrated.triggersJson).getJSONObject(0)
        assertEquals("datetime", trigger.getString("type"))
        assertEquals(now + 3_600_000L, trigger.getLong("at"))
        val action = JSONArray(migrated.actionsJson).getJSONObject(0)
        assertEquals("set_home_mode", action.getString("type"))
        assertEquals("hg", action.getString("mode"))
        assertEquals(now + 7_200_000L, action.getLong("endAt"))
        assertTrue(store.effects.contains("cancelVacationStart"))
        // DataStore keys (frost watch) untouched.
        assertEquals(Triple(true, now + 3_600_000L, now + 7_200_000L), store.vacation)
    }

    @Test
    fun `in-flight vacation is left completely untouched`() = runBlocking {
        val now = 1_000_000L
        val store = FakeMigrationStore()
        store.vacation = Triple(true, now - 3_600_000L, now + 7_200_000L)

        ClassicAutomationMigrator(store, FakeMigrationFlags()) { now }.runIfNeeded()

        assertTrue(store.advanced.isEmpty())
        assertFalse(store.effects.contains("cancelVacationStart"))
    }

    // ── Rollback ─────────────────────────────────────────────────────────────

    @Test
    fun `rollback re-enables the classic, deletes the equivalent and is never re-migrated`() = runBlocking {
        val store = FakeMigrationStore()
        store.automations.add(scheduleClassic())
        val flags = FakeMigrationFlags()
        val migrator = ClassicAutomationMigrator(store, flags)

        migrator.runIfNeeded()
        assertEquals(1, store.advanced.size)

        assertTrue(migrator.rollbackFor(store.advanced[0]))
        assertTrue(store.automations[0].enabled)
        assertTrue(store.advanced.isEmpty())
        assertTrue(store.effects.contains("scheduleLocal"))

        migrator.runIfNeeded() // reverted items are never re-migrated
        assertTrue(store.automations[0].enabled)
        assertTrue(store.advanced.isEmpty())
    }

    @Test
    fun `rollback failure keeps the advanced equivalent - the rule is never lost`() = runBlocking {
        val base = FakeMigrationStore()
        base.automations.add(scheduleClassic())
        val flags = FakeMigrationFlags()
        ClassicAutomationMigrator(base, flags).runIfNeeded()
        assertEquals(1, base.advanced.size)

        // Rollback against a store that fails on the delete inside the transaction.
        val store = FailingWriteStore(failOnWrite = 2)
        store.automations.addAll(base.automations)
        store.advanced.addAll(base.advanced)
        val migrator2 = ClassicAutomationMigrator(store, flags)

        assertFalse(migrator2.rollbackFor(store.advanced[0]))
        // Rolled back: classic still disabled, equivalent still present and active.
        assertFalse(store.automations[0].enabled)
        assertEquals(1, store.advanced.size)
    }
}
