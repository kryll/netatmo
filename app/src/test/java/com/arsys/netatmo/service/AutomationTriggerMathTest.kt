package com.arsys.netatmo.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.Test
import java.util.Calendar

class AutomationTriggerMathTest {

    // ── Time windows ─────────────────────────────────────────────────────────

    @Test
    fun `same-day window contains its bounds`() {
        // 08:00-12:30; 08:00 = 480, 12:30 = 750
        assertTrue(AutomationTriggerMath.isInsideWindow(3, 480, null, 480, 750))
        assertTrue(AutomationTriggerMath.isInsideWindow(3, 750, null, 480, 750))
        assertTrue(AutomationTriggerMath.isInsideWindow(3, 600, null, 480, 750))
    }

    @Test
    fun `same-day window excludes outside times`() {
        assertFalse(AutomationTriggerMath.isInsideWindow(3, 479, null, 480, 750))
        assertFalse(AutomationTriggerMath.isInsideWindow(3, 751, null, 480, 750))
    }

    @Test
    fun `overnight window wraps past midnight`() {
        // 22:00-07:00
        assertTrue(AutomationTriggerMath.isInsideWindow(3, 22 * 60, null, 22 * 60, 7 * 60))
        assertTrue(AutomationTriggerMath.isInsideWindow(3, 23 * 60 + 59, null, 22 * 60, 7 * 60))
        assertTrue(AutomationTriggerMath.isInsideWindow(3, 0, null, 22 * 60, 7 * 60))
        assertTrue(AutomationTriggerMath.isInsideWindow(3, 7 * 60, null, 22 * 60, 7 * 60))
        assertFalse(AutomationTriggerMath.isInsideWindow(3, 7 * 60 + 1, null, 22 * 60, 7 * 60))
        assertFalse(AutomationTriggerMath.isInsideWindow(3, 12 * 60, null, 22 * 60, 7 * 60))
        assertFalse(AutomationTriggerMath.isInsideWindow(3, 21 * 60 + 59, null, 22 * 60, 7 * 60))
    }

    @Test
    fun `day filter rejects other weekdays`() {
        val weekdays = setOf(1, 2, 3, 4, 5)
        assertTrue(AutomationTriggerMath.isInsideWindow(5, 600, weekdays, 480, 750))
        assertFalse(AutomationTriggerMath.isInsideWindow(6, 600, weekdays, 480, 750))
        assertFalse(AutomationTriggerMath.isInsideWindow(7, 600, weekdays, 480, 750))
    }

    @Test
    fun `migrated schedule window behaves like the classic worker`() {
        // Classic: days 1-5, 07:30-09:00, applied every 15 min while inside.
        val days = setOf(1, 2, 3, 4, 5)
        assertTrue(AutomationTriggerMath.isInsideWindow(1, 7 * 60 + 30, days, 450, 540))
        assertTrue(AutomationTriggerMath.isInsideWindow(1, 540, days, 450, 540))
        assertFalse(AutomationTriggerMath.isInsideWindow(1, 541, days, 450, 540))
        assertFalse(AutomationTriggerMath.isInsideWindow(6, 500, days, 450, 540))
    }

    // ── Calendar dedup window ────────────────────────────────────────────────

    @Test
    fun `calendar fires exactly minutesBefore before the event`() {
        val now = 1_000_000_000_000L
        val event = now + 30 * 60_000L // event in 30 min, trigger is right now
        assertTrue(AutomationTriggerMath.calendarShouldFire(event, 30, now))
    }

    @Test
    fun `calendar does not fire before the trigger instant`() {
        val now = 1_000_000_000_000L
        val event = now + 31 * 60_000L // trigger in 1 minute from now
        assertFalse(AutomationTriggerMath.calendarShouldFire(event, 30, now))
    }

    @Test
    fun `calendar still fires when the poll lands within the lookback`() {
        val now = 1_000_000_000_000L
        // Trigger instant was 10 min ago; the 15-min lookback still catches it.
        val event = now + 30 * 60_000L - 10 * 60_000L
        assertTrue(AutomationTriggerMath.calendarShouldFire(event, 30, now))
    }

    @Test
    fun `calendar does not fire when the trigger instant is older than the lookback`() {
        val now = 1_000_000_000_000L
        // Trigger instant was 16 min ago; a crashed or delayed worker must not re-fire late.
        val event = now + 30 * 60_000L - 16 * 60_000L
        assertFalse(AutomationTriggerMath.calendarShouldFire(event, 30, now))
    }

    // ── One-shot datetime ────────────────────────────────────────────────────

    @Test
    fun `datetime fires only at or after its instant and only once`() {
        val now = 1_000_000_000_000L
        assertFalse(AutomationTriggerMath.datetimeShouldFire(now + 60_000L, false, now))
        assertTrue(AutomationTriggerMath.datetimeShouldFire(now, false, now))
        assertTrue(AutomationTriggerMath.datetimeShouldFire(now - 60_000L, false, now))
        assertFalse(AutomationTriggerMath.datetimeShouldFire(now - 60_000L, true, now))
        assertFalse(AutomationTriggerMath.datetimeShouldFire(0L, false, now))
    }

    // ── Instant+occurrence consumption keys (re-arm on date edit, no replay) ──

    private fun dt(atMs: Long): Pair<String, Long> = "datetime" to atMs

    @Test
    fun `datetime key is instant plus occurrence among identical instants, never position`() {
        assertEquals("tdt3_7_1000_0", AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L, 0))
        assertTrue(AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L, 0) != AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L, 1))
        assertTrue(AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L, 0) != AutomationTriggerMath.datetimeConsumedKey(7L, 2_000L, 0))
        assertTrue(AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L, 0) != AutomationTriggerMath.datetimeConsumedKey(8L, 1_000L, 0))
        assertEquals(
            AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L, 1),
            AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L, 1)
        )
    }

    @Test
    fun `occurrence is stable when other triggers are inserted deleted or reordered`() {
        val before = listOf(dt(1_000L), "time" to 600L)
        val after = listOf("time" to 600L, "geofence" to 0L, dt(1_000L))
        // A slid from index 0 to index 2; no identical instant in front in either layout.
        assertEquals(AutomationTriggerMath.datetimeOccurrence(before, 0), AutomationTriggerMath.datetimeOccurrence(after, 2))
        assertEquals(0, AutomationTriggerMath.datetimeOccurrence(after, 2))
    }

    @Test
    fun `consumed datetime does not replay when another trigger is inserted before it`() {
        // A fired at 1000 (consumed). The user inserts other triggers in front of it.
        val key = AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L, 0)
        val consumedKeys = setOf(key)
        val after = listOf("time" to 600L, dt(1_000L))
        val keyAfter = AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L, AutomationTriggerMath.datetimeOccurrence(after, 1))
        assertTrue(consumedKeys.contains(keyAfter))
        assertFalse(AutomationTriggerMath.datetimeShouldFire(1_000L, true, 2_000L))
    }

    @Test
    fun `swapping two consumed datetime triggers replays neither`() {
        val keyA = AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L, 0)
        val keyB = AutomationTriggerMath.datetimeConsumedKey(7L, 2_000L, 0)
        val consumedKeys = setOf(keyA, keyB)
        assertFalse(AutomationTriggerMath.datetimeShouldFire(1_000L, consumedKeys.contains(keyA), 3_000L))
        assertFalse(AutomationTriggerMath.datetimeShouldFire(2_000L, consumedKeys.contains(keyB), 3_000L))
    }

    @Test
    fun `a pending intact datetime is not suppressed by edits around it`() {
        // A fired at 1000 (consumed); B is pending at 9000. Reorder everything: B's key is
        // unchanged, still unconsumed, still fires when its time comes.
        val consumedKeys = setOf(AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L, 0))
        val after = listOf(dt(9_000L), "time" to 600L, dt(1_000L))
        val keyB = AutomationTriggerMath.datetimeConsumedKey(7L, 9_000L, AutomationTriggerMath.datetimeOccurrence(after, 0))
        assertFalse(consumedKeys.contains(keyB))
        assertTrue(AutomationTriggerMath.datetimeShouldFire(9_000L, consumedKeys.contains(keyB), 9_000L))
    }

    @Test
    fun `editing the date re-arms and editing back stays consumed`() {
        val consumedKeys = setOf(AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L, 0))
        // Edit 1000 -> 5000: new instant key was never consumed, fires when due.
        val editedKey = AutomationTriggerMath.datetimeConsumedKey(7L, 5_000L, 0)
        assertTrue(AutomationTriggerMath.datetimeShouldFire(5_000L, consumedKeys.contains(editedKey), 5_000L))
        // Edit back to 1000: the original key exists, no refire.
        assertFalse(AutomationTriggerMath.datetimeShouldFire(1_000L, consumedKeys.contains(AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L, 0)), 6_000L))
    }

    @Test
    fun `duplicate datetime triggers keep legacy multiplicity with distinct occurrence keys`() {
        val triggers = listOf(dt(1_000L), dt(1_000L))
        val first = AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L, AutomationTriggerMath.datetimeOccurrence(triggers, 0))
        val second = AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L, AutomationTriggerMath.datetimeOccurrence(triggers, 1))
        assertTrue(first != second)
        // Each fires once: after the first fires, the second still fires (legacy semantics).
        val afterFirst = setOf(first)
        assertTrue(AutomationTriggerMath.datetimeShouldFire(1_000L, afterFirst.contains(second), 1_000L))
        assertFalse(AutomationTriggerMath.datetimeShouldFire(1_000L, afterFirst.contains(first), 1_000L))
    }

    @Test
    fun `legacy migration maps each consumed index to its current instant and occurrence`() {
        val migrated = AutomationTriggerMath.datetimeKeyMigration(
            7L, setOf(0), listOf(dt(1_000L), "time" to 600L)
        )
        assertEquals(setOf("tdt3_7_1000_0"), migrated)
        val consumedNow = migrated.contains(AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L, 0))
        assertFalse(AutomationTriggerMath.datetimeShouldFire(1_000L, consumedNow, 2_000L))
        // Duplicates: consumed second occurrence maps to occurrence 1.
        val dup = AutomationTriggerMath.datetimeKeyMigration(7L, setOf(1), listOf(dt(1_000L), dt(1_000L)))
        assertEquals(setOf("tdt3_7_1000_1"), dup)
    }

    @Test
    fun `migration drops legacy indices that no longer hold a datetime trigger`() {
        assertTrue(AutomationTriggerMath.datetimeKeyMigration(7L, setOf(3), listOf(dt(1_000L))).isEmpty())
        assertTrue(AutomationTriggerMath.datetimeKeyMigration(7L, setOf(0), listOf("time" to 600L)).isEmpty())
        assertTrue(AutomationTriggerMath.datetimeKeyMigration(7L, emptySet(), listOf(dt(1_000L))).isEmpty())
    }

    // ── Next scheduled instant (alarm scheduler) ─────────────────────────────

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun `time trigger next instant is today's window start when still ahead`() {
        // Friday 2026-10-30 08:00, trigger 09:00 -> today 09:00
        val now = at(2026, 9, 30, 8, 0)
        assertEquals(at(2026, 9, 30, 9, 0), AutomationTriggerMath.nextTimeTriggerInstant(null, 9 * 60, false, now))
    }

    @Test
    fun `time trigger inside its open window fires immediately`() {
        // 09:10 with window 09:00-09:20 and not fired -> evaluate now
        val now = at(2026, 9, 30, 9, 10)
        assertEquals(now, AutomationTriggerMath.nextTimeTriggerInstant(null, 9 * 60, false, now))
    }

    @Test
    fun `time trigger already fired today waits for tomorrow`() {
        val now = at(2026, 9, 30, 9, 10)
        assertEquals(at(2026, 9, 31, 9, 0), AutomationTriggerMath.nextTimeTriggerInstant(null, 9 * 60, true, now))
    }

    @Test
    fun `time trigger past today's window waits for tomorrow`() {
        val now = at(2026, 9, 30, 9, 30)
        assertEquals(at(2026, 9, 31, 9, 0), AutomationTriggerMath.nextTimeTriggerInstant(null, 9 * 60, false, now))
    }

    @Test
    fun `time trigger skips weekdays not in the set`() {
        // Friday 2026-10-30 08:00, days = weekend -> Saturday 2026-10-31 09:00
        val now = at(2026, 9, 30, 8, 0)
        assertEquals(at(2026, 9, 31, 9, 0), AutomationTriggerMath.nextTimeTriggerInstant(setOf(6, 7), 9 * 60, false, now))
    }

    @Test
    fun `time trigger with empty day set is never armed`() {
        val now = at(2026, 9, 30, 8, 0)
        assertNull(AutomationTriggerMath.nextTimeTriggerInstant(emptySet(), 9 * 60, false, now))
    }

    @Test
    fun `datetime trigger is armed unless consumed`() {
        assertEquals(123_000L, AutomationTriggerMath.nextDatetimeInstant(123_000L, false))
        assertNull(AutomationTriggerMath.nextDatetimeInstant(123_000L, true))
        assertNull(AutomationTriggerMath.nextDatetimeInstant(0L, false))
    }

    // ── Datetime migration: interleaving (real shared mutex, fake state store) ────

    private class FakeDatetimeStore(initial: Map<String, Boolean>) {
        val data = java.util.concurrent.ConcurrentHashMap(initial)
        val logs = java.util.concurrent.CopyOnWriteArrayList<String>()
        val store = DatetimeStateStore(
            allKeys = { data.keys.toSet() },
            getBoolean = { data[it] ?: false },
            commit = { removals, additions ->
                removals.forEach { data.remove(it) }
                data.putAll(additions)
            }
        )
    }

    private fun dtJsonOf(vararg entries: Pair<String, Long>): String {
        val arr = org.json.JSONArray()
        for ((type, at) in entries) {
            val obj = org.json.JSONObject()
            obj.put("type", type)
            if (type == "datetime") obj.put("at", at)
            if (type == "time") obj.put("time", "08:00")
            arr.put(obj)
        }
        return arr.toString()
    }

    @Test
    fun `save migrates against the stored list before a structural overwrite`() = runBlocking {
        val atMs = 1_800_000_000_000L
        val stored = dtJsonOf("time" to 0L, "datetime" to atMs)
        val edited = dtJsonOf("datetime" to atMs) // user deleted the leading time trigger
        val fake = FakeDatetimeStore(mapOf("tdt_7_1" to true))
        val storedHolder = java.util.concurrent.atomic.AtomicReference(stored)
        // AdvancedAutomationViewModel.save order: migrate the STORED list, then overwrite.
        saveAutomationStateMutex.withLock {
            migrateDatetimeConsumptionLocked(7, { storedHolder.get() }, fake.store) { fake.logs.add(it) }
            storedHolder.set(edited)
        }
        assertFalse(fake.data.containsKey("tdt_7_1"))
        assertTrue(fake.data["tdt3_7_${atMs}_0"] == true)
        assertTrue(fake.data["mig3_tdt_7"] == true)
        assertEquals(1, fake.logs.size)
        // The datetime shifted to index 0, but its occurrence-0 key is the migrated mark:
        // it stays consumed instead of refiring, and no shifted mark suppresses it wrongly.
        val editedPairs = parseDatetimeTypeAt(edited)
        assertEquals(0, AutomationTriggerMath.datetimeOccurrence(editedPairs, 0))
        assertFalse(AutomationTriggerMath.datetimeShouldFire(atMs, fake.data["tdt3_7_${atMs}_0"] == true, atMs + 1))
    }

    @Test
    fun `concurrent save and engine migrations never interleave or double-apply`() = runBlocking {
        repeat(20) {
            val atMs = 1_800_000_000_000L
            val stored = dtJsonOf("datetime" to atMs)
            val fake = FakeDatetimeStore(mapOf("tdt_5_0" to true))
            val jobSave = launch(Dispatchers.Default) {
                saveAutomationStateMutex.withLock {
                    migrateDatetimeConsumptionLocked(5, { stored }, fake.store) { fake.logs.add(it) }
                }
            }
            val jobEngine = launch(Dispatchers.Default) {
                saveAutomationStateMutex.withLock {
                    migrateDatetimeConsumptionLocked(5, { stored }, fake.store) { fake.logs.add(it) }
                }
            }
            jobSave.join(); jobEngine.join()
            assertFalse(fake.data.containsKey("tdt_5_0"))
            assertTrue(fake.data["tdt3_5_${atMs}_0"] == true)
            assertTrue(fake.data["mig3_tdt_5"] == true)
            assertEquals("migration applied exactly once", 1, fake.logs.size)
        }
    }

    @Test
    fun `in-flight evaluation on an old snapshot keeps marks coherent across a save`() = runBlocking {
        val atMs = 1_800_000_000_000L
        val editedAtMs = 1_800_000_600_000L
        val stored = dtJsonOf("datetime" to atMs)
        val fake = FakeDatetimeStore(mapOf("tdt_9_0" to true))
        val storedHolder = java.util.concurrent.atomic.AtomicReference(stored)
        // Engine path: migrate first, then read consumption for the snapshot it holds.
        saveAutomationStateMutex.withLock {
            migrateDatetimeConsumptionLocked(9, { storedHolder.get() }, fake.store) { fake.logs.add(it) }
        }
        val snapshotPairs = parseDatetimeTypeAt(stored)
        val key = AutomationTriggerMath.datetimeConsumedKey(9, atMs, AutomationTriggerMath.datetimeOccurrence(snapshotPairs, 0))
        assertTrue("migrated mark suppresses the already-fired trigger", fake.data[key] == true)
        // A save edits the date: migration is a no-op (guard set), the write then lands.
        saveAutomationStateMutex.withLock {
            migrateDatetimeConsumptionLocked(9, { storedHolder.get() }, fake.store) { fake.logs.add(it) }
            storedHolder.set(dtJsonOf("datetime" to editedAtMs))
        }
        // The edited trigger re-arms under its new instant; the old mark is orphaned but harmless.
        val editedKey = AutomationTriggerMath.datetimeConsumedKey(9, editedAtMs, 0)
        assertFalse(fake.data[editedKey] == true)
        assertTrue(AutomationTriggerMath.datetimeShouldFire(editedAtMs, fake.data[editedKey] == true, editedAtMs + 1))
        assertEquals(1, fake.logs.size)
    }

    @Test
    fun `deleting one of two identical-instant datetimes shifts the survivor occurrence`() = runBlocking {
        // Declared residual, documented as a test: identical instants are indistinguishable,
        // so the survivor inherits the consumed mark of the deleted duplicate (suppressed).
        // This matches legacy behavior and never causes an extra fire.
        val atMs = 1_800_000_000_000L
        val stored = dtJsonOf("datetime" to atMs, "datetime" to atMs)
        val fake = FakeDatetimeStore(mapOf("tdt_4_0" to true)) // first duplicate fired, second pending
        val storedHolder = java.util.concurrent.atomic.AtomicReference(stored)
        saveAutomationStateMutex.withLock {
            migrateDatetimeConsumptionLocked(4, { storedHolder.get() }, fake.store) { fake.logs.add(it) }
            storedHolder.set(dtJsonOf("datetime" to atMs)) // user deletes the FIRST duplicate
        }
        val survivorPairs = parseDatetimeTypeAt(dtJsonOf("datetime" to atMs))
        val survivorKey = AutomationTriggerMath.datetimeConsumedKey(4, atMs, AutomationTriggerMath.datetimeOccurrence(survivorPairs, 0))
        assertTrue(fake.data[survivorKey] == true)
        assertFalse(AutomationTriggerMath.datetimeShouldFire(atMs, fake.data[survivorKey] == true, atMs + 1))
    }

    @Test
    fun `upgrade never reactivates a legacy-consumed trigger, only genuine pendings fire`() = runBlocking {
        val past = 1_000_000_000_000L
        val pastEdited = 1_100_000_000_000L
        // A: fired and untouched. B: fired then date EDITED pre-upgrade (the rearm bug victim:
        // its legacy mark outlived the edit). C: never fired, instant already past (genuinely
        // pending: v212 would fire it at its next evaluation too). D: fired duplicate of A.
        val stored = dtJsonOf(
            "datetime" to past,        // idx0 A: mark
            "datetime" to pastEdited,  // idx1 B: mark (edited after firing, mark kept)
            "datetime" to past,        // idx2 C: NO mark
            "datetime" to past         // idx3 D: mark (fired second duplicate of the past instant)
        )
        val fake = FakeDatetimeStore(mapOf("tdt_3_0" to true, "tdt_3_1" to true, "tdt_3_3" to true))
        saveAutomationStateMutex.withLock {
            migrateDatetimeConsumptionLocked(3, { stored }, fake.store) { fake.logs.add(it) }
        }
        fun consumed(index: Int, at: Long): Boolean {
            val pairs = parseDatetimeTypeAt(stored)
            val key = AutomationTriggerMath.datetimeConsumedKey(3, at, AutomationTriggerMath.datetimeOccurrence(pairs, index))
            return fake.data[key] == true
        }
        val now = pastEdited + 1
        assertFalse("A fired+untouched stays consumed", AutomationTriggerMath.datetimeShouldFire(past, consumed(0, past), now))
        assertFalse("B fired then edited pre-upgrade stays consumed at its current instant", AutomationTriggerMath.datetimeShouldFire(pastEdited, consumed(1, pastEdited), now))
        assertTrue("C genuinely pending still fires (same as v212)", AutomationTriggerMath.datetimeShouldFire(past, consumed(2, past), now))
        assertFalse("D fired duplicate stays consumed", AutomationTriggerMath.datetimeShouldFire(past, consumed(3, past), now))
        assertTrue("legacy marks removed", fake.data.keys.none { it.startsWith("tdt_3_") })
        assertTrue(fake.data["mig3_tdt_3"] == true)
    }

    // ── Calendar: window anchored at the previous evaluation ────────────────

    @Test
    fun `calendar with zero minutes before fires on the first check after the event starts`() {
        val now = 1_000_000_000_000L
        val event = now - 5 * 60_000L // started 5 min ago; trigger instant = event start
        assertTrue(AutomationTriggerMath.calendarShouldFire(event, 0, now))
    }

    @Test
    fun `calendar with zero minutes before does not fire before the event starts`() {
        val now = 1_000_000_000_000L
        val event = now + 5 * 60_000L
        assertFalse(AutomationTriggerMath.calendarShouldFire(event, 0, now))
    }

    @Test
    fun `calendar fires late when the previous check was long ago instead of never`() {
        val now = 1_000_000_000_000L
        val lastCheck = now - 45 * 60_000L // battery delayed the worker 45 min
        val event = now // trigger instant (30 min ago) is inside (lastCheck, now]
        assertTrue(AutomationTriggerMath.calendarShouldFire(event, 30, now, lastCheck))
    }

    @Test
    fun `calendar does not replay an instant already covered by the previous check`() {
        val now = 1_000_000_000_000L
        val lastCheck = now - 10 * 60_000L
        val event = now - 40 * 60_000L // trigger instant (70 min ago) precedes the last check: already covered
        assertFalse(AutomationTriggerMath.calendarShouldFire(event, 30, now, lastCheck))
    }

    @Test
    fun `calendar consumed key is per event instant and independent of trigger position`() {
        assertEquals("tcal2_7_1730000000000", AutomationTriggerMath.calendarConsumedKey(7L, 1_730_000_000_000L))
    }

    @Test
    fun `legacy positional calendar marks migrate to per-event keys keeping their instants`() {
        val legacy = setOf("tcal_7_0_1730000000000", "tcal_7_2_1730003600000", "tcal2_7_1", "tcal_8_0_1730000000000")
        assertEquals(
            setOf("tcal2_7_1730000000000", "tcal2_7_1730003600000"),
            AutomationTriggerMath.calendarKeyMigration(7L, legacy)
        )
    }

    // ── Calendar: all-day events and per-occurrence keys ────────────────────

    @Test
    fun `all-day event triggers at local midnight, not UTC midnight shifted`() {
        val utcMidnight = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            set(2026, 10, 2, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis // all-day "2 Nov 2026" as the provider returns it
        val madrid = java.util.TimeZone.getTimeZone("Europe/Madrid") // CET in November: UTC+1
        val expected = utcMidnight - 1 * 60 * 60_000L // local midnight 2 Nov = 23:00 UTC 1 Nov
        assertEquals(expected, AutomationTriggerMath.allDayLocalStart(utcMidnight, madrid))
    }

    @Test
    fun `each occurrence of a recurring event gets its own consumption key`() {
        val first = AutomationTriggerMath.calendarConsumedKey(7L, 1_730_000_000_000L)
        val nextOccurrence = AutomationTriggerMath.calendarConsumedKey(7L, 1_730_604_800_000L)
        assertTrue(first != nextOccurrence)
    }
}
