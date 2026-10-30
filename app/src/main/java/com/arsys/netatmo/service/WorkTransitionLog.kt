package com.arsys.netatmo.service

/**
 * Formats WorkManager state transitions for the diagnostic log and suppresses repeats:
 * the observer emits on every database change, but only real transitions are worth a line.
 * Pure logic (no Android types) so it is unit-testable.
 */
class WorkTransitionLog {
    private val lastByWork = mutableMapOf<String, String>()

    /** Returns the line to log, or null when nothing changed since the previous emission. */
    fun next(work: String, state: String, runAttemptCount: Int, stopReason: String): String? {
        val key = "$state|$runAttemptCount|$stopReason"
        if (lastByWork[work] == key) return null
        lastByWork[work] = key
        return "Evaluación $work: $state (intento ${runAttemptCount + 1}, $stopReason)"
    }
}
