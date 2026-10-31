package com.arsys.netatmo.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
}
