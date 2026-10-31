package com.arsys.netatmo.service

/**
 * Pure trigger-evaluation helpers, extracted from [AdvancedAutomationEngine] so they can be
 * unit-tested on the JVM without Android.
 */
object AutomationTriggerMath {

    const val DEFAULT_POLL_MS = 15 * 60_000L

    /** A time trigger counts as "now" for this long after its time (engine + alarm scheduler). */
    const val TIME_TRIGGER_WINDOW_MIN = 20

    /**
     * Whether [nowMinutes] (minutes since midnight) on [isoDay] (1=Mon..7=Sun) is inside the
     * window. Overnight windows (start > end) wrap past midnight. [days] null means every day.
     */
    fun isInsideWindow(isoDay: Int, nowMinutes: Int, days: Set<Int>?, startMin: Int, endMin: Int): Boolean {
        if (days != null && isoDay !in days) return false
        return if (startMin <= endMin) nowMinutes in startMin..endMin
        else nowMinutes >= startMin || nowMinutes <= endMin
    }

    /**
     * Whether a calendar event starting at [eventStartMs] triggers now: its trigger instant
     * ([minutesBefore] before the start) fell inside the last poll window ending at [nowMs].
     */
    fun calendarShouldFire(
        eventStartMs: Long,
        minutesBefore: Int,
        nowMs: Long,
        pollMs: Long = DEFAULT_POLL_MS
    ): Boolean {
        val triggerTime = eventStartMs - minutesBefore * 60_000L
        return triggerTime in (nowMs - pollMs)..nowMs
    }

    /** One-shot instant trigger: fires at or after [atMs], only once (not when [consumed]). */
    fun datetimeShouldFire(atMs: Long, consumed: Boolean, nowMs: Long): Boolean =
        atMs > 0 && !consumed && nowMs >= atMs

    /**
     * Consumption key for a one-shot datetime trigger, identified by its instant and its
     * OCCURRENCE among the datetime triggers sharing that instant (0-based), never by its
     * position in the trigger list: editing the date of a fired trigger re-arms it (the new
     * instant's key was never consumed); inserting, deleting, reordering or swapping other
     * triggers never replays an intact one; duplicates with the exact same instant keep
     * their legacy multiplicity (distinct occurrence keys, each fires once); editing back to
     * an instant+occurrence that already fired stays consumed (no refire).
     * The "tdt3_" prefix is textually distinct from legacy "tdt_<id>_<index>" marks.
     */
    fun datetimeConsumedKey(automationId: Long, atMs: Long, occurrence: Int): String =
        "tdt3_${automationId}_${atMs}_$occurrence"

    /**
     * Occurrence of the datetime trigger at [index] among the triggers sharing its instant,
     * counting only earlier positions. Stable under insertion, deletion and reordering of
     * any trigger that does not share the exact same instant.
     */
    fun datetimeOccurrence(triggers: List<Pair<String, Long>>, index: Int): Int {
        val at = triggers.getOrNull(index)?.takeIf { it.first == "datetime" }?.second ?: return 0
        var occurrence = 0
        for (i in 0 until index) {
            if (triggers[i].first == "datetime" && triggers[i].second == at) occurrence++
        }
        return occurrence
    }

    /**
     * One-time translation of legacy consumption marks ("tdt_<id>_<index>", without the
     * instant) to instant+occurrence keys: each consumed legacy index marks the datetime
     * trigger currently sitting at that index, at its current instant and occurrence.
     * Exact for every history whose trigger list kept its structure since the mark was
     * written (date edits included: they keep the index). Legacy indices that no longer
     * hold a datetime trigger are dropped. See DatetimeConsumptionMigration for the
     * declared residual on structural edits made between the fire and the migration.
     */
    fun datetimeKeyMigration(
        automationId: Long,
        legacyConsumedIndices: Set<Int>,
        triggers: List<Pair<String, Long>>
    ): Set<String> = legacyConsumedIndices.mapNotNull { index ->
        val trigger = triggers.getOrNull(index) ?: return@mapNotNull null
        if (trigger.first != "datetime") return@mapNotNull null
        datetimeConsumedKey(automationId, trigger.second, datetimeOccurrence(triggers, index))
    }.toSet()

    /**
     * Next instant a recurring "time" trigger should be evaluated: the start of its next
     * window, or [nowMs] when its window is open and it has not fired today ([firedToday]).
     * Null when [days] is an empty set. Already-fired and already-past windows are skipped,
     * matching the engine's once-per-day semantics (no catch-up).
     */
    fun nextTimeTriggerInstant(days: Set<Int>?, timeMinutes: Int, firedToday: Boolean, nowMs: Long): Long? {
        if (days != null && days.isEmpty()) return null
        val base = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
        for (offset in 0..7) {
            val c = (base.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.DAY_OF_YEAR, offset)
                set(java.util.Calendar.HOUR_OF_DAY, timeMinutes / 60)
                set(java.util.Calendar.MINUTE, timeMinutes % 60)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val isoDay = (c.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7 + 1
            if (days != null && isoDay !in days) continue
            val start = c.timeInMillis
            if (offset == 0 && firedToday) continue
            if (nowMs < start + TIME_TRIGGER_WINDOW_MIN * 60_000L) return maxOf(start, nowMs)
        }
        return null
    }

    /** One-shot instant trigger: armed at [atMs] unless already consumed. */
    fun nextDatetimeInstant(atMs: Long, consumed: Boolean): Long? =
        if (atMs > 0 && !consumed) atMs else null
}
