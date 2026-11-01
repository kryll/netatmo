package com.arsys.netatmo.ui.screens.automations.advanced

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPrefillTest {

    /** Mirrors AdvancedAutomationViewModel.jsonArrayToMaps: Kotlin values become JSON types. */
    private fun jsonRoundTrip(map: Map<String, Any>): Map<String, Any> {
        val obj = JSONObject()
        for ((k, v) in map) obj.put(k, JSONObject.wrap(v))
        val out = mutableMapOf<String, Any>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = obj.get(key)
        }
        return out
    }

    // ── Triggers ─────────────────────────────────────────────────────────────

    @Test
    fun `time trigger prefill from creation shape`() {
        val map = mapOf<String, Any>(
            "type" to "time", "platform" to "time", "time" to "09:30", "days" to listOf(6, 7)
        )
        val p = prefillTrigger(map)
        assertEquals("time", p.type)
        assertEquals("09:30", p.time)
        assertEquals(listOf(6, 7), p.days)
    }

    @Test
    fun `time trigger prefill after JSON round trip keeps days`() {
        val map = jsonRoundTrip(mapOf<String, Any>(
            "type" to "time", "platform" to "time", "time" to "21:15", "days" to listOf(1, 3, 5)
        ))
        assertTrue(map["days"] is JSONArray) // proves we test the JSON shape
        val p = prefillTrigger(map)
        assertEquals("21:15", p.time)
        assertEquals(listOf(1, 3, 5), p.days)
    }

    @Test
    fun `legacy hour-minute trigger prefill builds HH MM`() {
        val p = prefillTrigger(mapOf<String, Any>("type" to "time", "hour" to 8, "minute" to 5))
        assertEquals("08:05", p.time)
    }

    @Test
    fun `numeric trigger below keeps direction and threshold`() {
        val p = prefillTrigger(jsonRoundTrip(mapOf<String, Any>(
            "type" to "numeric_state", "entity" to "outdoor_temp", "below" to 3.0
        )))
        assertEquals("below", p.numericCondition)
        assertEquals(3.0f, p.threshold, 0.001f)
    }

    @Test
    fun `numeric trigger above keeps direction and threshold`() {
        val p = prefillTrigger(mapOf<String, Any>(
            "type" to "numeric_state", "entity" to "indoor_temp", "above" to 24.5
        ))
        assertEquals("above", p.numericCondition)
        assertEquals(24.5f, p.threshold, 0.001f)
    }

    @Test
    fun `datetime trigger prefill round trips date and time`() {
        val at = parseDateTimeMs("2026-11-15", "18:45")!!
        val p = prefillTrigger(mapOf<String, Any>("type" to "datetime", "at" to at))
        assertEquals("datetime", p.type)
        assertEquals("2026-11-15", p.date)
        assertEquals("18:45", p.dateTime)
        // And the parsed values feed parseDateTimeMs back to the same instant.
        assertEquals(at, parseDateTimeMs(p.date, p.dateTime))
    }

    @Test
    fun `datetime trigger prefill after JSON round trip`() {
        val at = parseDateTimeMs("2026-12-01", "07:00")!!
        val p = prefillTrigger(jsonRoundTrip(mapOf<String, Any>("type" to "datetime", "at" to at)))
        assertEquals("2026-12-01", p.date)
        assertEquals("07:00", p.dateTime)
    }

    @Test
    fun `geofence trigger prefill reads event then legacy action`() {
        val modern = prefillTrigger(mapOf<String, Any>(
            "type" to "geofence", "event" to "exit", "zoneId" to "z1", "zone" to "Casa"
        ))
        assertEquals("exit", modern.geofenceEvent)
        assertEquals("z1", modern.geofenceZoneId)

        val legacy = prefillTrigger(mapOf<String, Any>(
            "type" to "geofence", "action" to "enter", "zoneId" to "z2"
        ))
        assertEquals("enter", legacy.geofenceEvent)
    }

    @Test
    fun `time window prefill keeps range and days`() {
        val p = prefillTrigger(jsonRoundTrip(mapOf<String, Any>(
            "type" to "time_window", "start" to "22:30", "end" to "06:30", "days" to listOf(1, 2, 3, 4, 5, 6, 7)
        )))
        assertEquals("22:30", p.windowStart)
        assertEquals("06:30", p.windowEnd)
        assertEquals(7, p.days.size)
    }

    @Test
    fun `calendar trigger prefill keeps id name title and minutes`() {
        val p = prefillTrigger(jsonRoundTrip(mapOf<String, Any>(
            "type" to "calendar", "calendarId" to 42L, "calendarName" to "Familia",
            "title" to "convivencia", "minutesBefore" to 45
        )))
        assertEquals(42L, p.calendarId)
        assertEquals("Familia", p.calendarName)
        assertEquals("convivencia", p.calendarTitle)
        assertEquals("45", p.calendarMinutesBefore)
    }

    // ── Conditions ───────────────────────────────────────────────────────────

    @Test
    fun `time range condition prefill`() {
        val p = prefillCondition(mapOf<String, Any>(
            "type" to "time_range", "after" to "08:00", "before" to "13:30"
        ))
        assertEquals("time_range", p.type)
        assertEquals("08:00", p.fromTime)
        assertEquals("13:30", p.toTime)
    }

    @Test
    fun `numeric condition above prefill`() {
        val p = prefillCondition(jsonRoundTrip(mapOf<String, Any>(
            "type" to "numeric_state", "entity" to "indoor_temp", "above" to 26.0
        )))
        assertEquals("above", p.condition)
        assertEquals("indoor_temp", p.entity)
        assertEquals(26.0f, p.threshold, 0.001f)
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    @Test
    fun `set temperature action prefill`() {
        val p = prefillAction(jsonRoundTrip(mapOf<String, Any>(
            "type" to "set_temperature", "temperature" to 21.5, "homeId" to "h1", "roomId" to "r1"
        )))
        assertEquals("set_temperature", p.type)
        assertEquals(21.5f, p.temperature, 0.001f)
        assertEquals("h1", p.homeId)
        assertEquals("r1", p.roomId)
    }

    @Test
    fun `mode action with duration prefill formats hours`() {
        val p = prefillAction(jsonRoundTrip(mapOf<String, Any>(
            "type" to "set_home_mode", "mode" to "away", "durationHours" to 3.5
        )))
        assertEquals("away", p.mode)
        assertEquals("3,5", p.durationText)
        assertEquals(0L, p.legacyEndAt)
    }

    @Test
    fun `whole hours format without decimals`() {
        assertEquals("3", formatHoursInput(3.0))
        assertEquals("0,75", formatHoursInput(0.75))
    }

    @Test
    fun `legacy fixed-end action keeps endAt for preservation`() {
        val end = parseDateTimeMs("2026-11-02", "20:00")!!
        val p = prefillAction(jsonRoundTrip(mapOf<String, Any>(
            "type" to "set_home_mode", "mode" to "hg", "endAt" to end
        )))
        assertEquals("", p.durationText)
        assertEquals(end, p.legacyEndAt)
    }

    @Test
    fun `duration wins over legacy endAt`() {
        val end = parseDateTimeMs("2026-11-02", "20:00")!!
        val p = prefillAction(mapOf<String, Any>(
            "type" to "set_mode", "mode" to "away", "durationHours" to 2.0, "endAt" to end
        ))
        assertEquals("2", p.durationText)
        assertEquals(0L, p.legacyEndAt)
    }

    @Test
    fun `notify and delay prefill`() {
        val n = prefillAction(jsonRoundTrip(mapOf<String, Any>(
            "type" to "notify", "title" to "Aviso", "message" to "Exterior %tem_ext%"
        )))
        assertEquals("Aviso", n.notifyTitle)
        assertEquals("Exterior %tem_ext%", n.notifyMessage)

        val d = prefillAction(jsonRoundTrip(mapOf<String, Any>("type" to "delay", "minutes" to 15)))
        assertEquals("15", d.delayMinutes)
    }

    @Test
    fun `unknown types are reported so the sheet can refuse editing`() {
        assertEquals("apply_scenario", prefillAction(mapOf<String, Any>(
            "type" to "apply_scenario", "scenarioId" to "7"
        )).type)
        assertFalse(listOf("set_temperature", "set_mode", "set_home_mode", "notify", "delay")
            .contains(prefillAction(mapOf<String, Any>("type" to "apply_scenario")).type))
    }
}
