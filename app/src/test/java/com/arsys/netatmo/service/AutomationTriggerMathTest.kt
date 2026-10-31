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

    // ── Instant-qualified consumption keys (re-arm on date edit, no replay) ──

    private fun consumed(vararg keys: String) = setOf(*keys)

    @Test
    fun `datetime consumption key is the instant alone so position never matters`() {
        assertEquals("tdt2_7_1000", AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L))
        assertEquals(
            AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L),
            AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L)
        )
        assertTrue(AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L) != AutomationTriggerMath.datetimeConsumedKey(7L, 2_000L))
        assertTrue(AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L) != AutomationTriggerMath.datetimeConsumedKey(8L, 1_000L))
    }

    @Test
    fun `consumed datetime does not replay when another trigger is inserted before it`() {
        // Trigger A fired at 1000 (consumed). User inserts another trigger in front of it:
        // A slides from index 0 to index 1 with its instant intact.
        val consumedKeys = consumed(AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L))
        // The evaluation reads the key from the instant, wherever the trigger sits now.
        val keyAfterInsert = AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L)
        assertTrue(consumedKeys.contains(keyAfterInsert))
        assertFalse(AutomationTriggerMath.datetimeShouldFire(1_000L, consumedKeys.contains(keyAfterInsert), 2_000L))
    }

    @Test
    fun `swapping two consumed datetime triggers replays neither`() {
        val keyA = AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L)
        val keyB = AutomationTriggerMath.datetimeConsumedKey(7L, 2_000L)
        val consumedKeys = consumed(keyA, keyB)
        // After the swap, B sits where A was and vice versa; both keys are still consumed.
        assertFalse(AutomationTriggerMath.datetimeShouldFire(2_000L, consumedKeys.contains(keyB), 3_000L))
        assertFalse(AutomationTriggerMath.datetimeShouldFire(1_000L, consumedKeys.contains(keyA), 3_000L))
    }

    @Test
    fun `editing the date re-arms because the new instant was never consumed`() {
        val consumedKeys = consumed(AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L))
        val editedKey = AutomationTriggerMath.datetimeConsumedKey(7L, 5_000L)
        assertTrue(AutomationTriggerMath.datetimeShouldFire(5_000L, consumedKeys.contains(editedKey), 5_000L))
    }

    @Test
    fun `editing back to an instant that already fired stays consumed and does not refire`() {
        // Date edited 1000 -> 5000 (re-armed, fired), then edited back to 1000:
        // the original key exists, so the trigger does not fire a second time for 1000.
        val consumedKeys = consumed(AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L))
        assertFalse(AutomationTriggerMath.datetimeShouldFire(1_000L, consumedKeys.contains(AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L)), 6_000L))
    }

    @Test
    fun `duplicate datetime triggers share one key and fire once per instant`() {
        // Two legacy marks for the same instant collapse into a single migrated key.
        val migrated = AutomationTriggerMath.datetimeKeyMigration(
            7L, setOf(0, 1), listOf("datetime" to 1_000L, "datetime" to 1_000L)
        )
        assertEquals(setOf("tdt2_7_1000"), migrated)
        // After the first duplicate fires, the second reads the same key as consumed.
        val afterFirstFire = migrated
        val secondKey = AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L)
        assertFalse(AutomationTriggerMath.datetimeShouldFire(1_000L, afterFirstFire.contains(secondKey), 2_000L))
    }

    @Test
    fun `legacy migration keeps fired datetimes consumed so nothing replays on upgrade`() {
        val migrated = AutomationTriggerMath.datetimeKeyMigration(
            7L, setOf(0), listOf("datetime" to 1_000L, "time" to 600L)
        )
        assertEquals(setOf("tdt2_7_1000"), migrated)
        val consumedNow = migrated.contains(AutomationTriggerMath.datetimeConsumedKey(7L, 1_000L))
        assertFalse(AutomationTriggerMath.datetimeShouldFire(1_000L, consumedNow, 2_000L))
    }

    @Test
    fun `migration drops legacy indices that no longer hold a datetime trigger`() {
        // Index out of range (trigger deleted) and index now holding a non-datetime trigger.
        assertTrue(AutomationTriggerMath.datetimeKeyMigration(7L, setOf(3), listOf("datetime" to 1_000L)).isEmpty())
        assertTrue(AutomationTriggerMath.datetimeKeyMigration(7L, setOf(0), listOf("time" to 600L)).isEmpty())
        assertTrue(AutomationTriggerMath.datetimeKeyMigration(7L, emptySet(), listOf("datetime" to 1_000L)).isEmpty())
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
