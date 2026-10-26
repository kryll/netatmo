package com.arsys.netatmo.service

/**
 * Pure trigger-evaluation helpers, extracted from [AdvancedAutomationEngine] so they can be
 * unit-tested on the JVM without Android.
 */
object AutomationTriggerMath {

    const val DEFAULT_POLL_MS = 15 * 60_000L

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
}
