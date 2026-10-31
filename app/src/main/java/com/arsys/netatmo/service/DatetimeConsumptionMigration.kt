package com.arsys.netatmo.service

import android.content.Context
import android.content.SharedPreferences
import com.arsys.netatmo.data.repository.AdvancedAutomationRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray

private const val STATE_PREFS = "advanced_automation_state"

/**
 * One short critical section shared by the writers of datetime-consumption state and of the
 * trigger list it describes: saving an automation (AdvancedAutomationViewModel.save) and the
 * legacy-mark migration below. Held for milliseconds, never during evaluation or actions, so
 * a running automation (including long "delay" actions) never blocks a save, and a save never
 * interleaves with the snapshot a migration translates.
 *
 * The invariant this enforces: a migration ALWAYS translates the stored trigger list as it is
 * at that moment, and every structural change to that list goes through save, which migrates
 * the stored list BEFORE overwriting it, inside this same section. So the positional legacy
 * marks are always interpreted against the trigger structure they were written for: date
 * edits, additions, deletions and reorders can no longer mis-assign them.
 */
internal val saveAutomationStateMutex = Mutex()

/**
 * Minimal state surface of the migration core, so unit tests can exercise the real locking
 * and translation logic over an in-memory fake instead of Android SharedPreferences.
 */
internal class DatetimeStateStore(
    val allKeys: () -> Set<String>,
    val getBoolean: (String) -> Boolean,
    val commit: (removals: Set<String>, additions: Map<String, Boolean>) -> Unit
)

/** Legacy marks are "tdt_<id>_<index>" (bare digits); "tdt3_" keys never match this scan. */
internal fun legacyDatetimeIndices(automationId: Long, keys: Set<String>): Set<Int> {
    val legacyPrefix = "tdt_${automationId}_"
    return keys.mapNotNull { key ->
        if (!key.startsWith(legacyPrefix)) return@mapNotNull null
        key.removePrefix(legacyPrefix).toIntOrNull()
    }.toSet()
}

internal fun parseDatetimeTypeAt(triggersJson: String): List<Pair<String, Long>> = try {
    val arr = JSONArray(triggersJson)
    (0 until arr.length()).map {
        val trigger = arr.getJSONObject(it)
        trigger.optString("type") to trigger.optLong("at", 0L)
    }
} catch (e: Exception) {
    emptyList()
}

/**
 * Migration core. CALLER MUST HOLD saveAutomationStateMutex. [readTriggersJson] must return
 * the trigger list CURRENTLY STORED (fresh read), never a snapshot taken outside the section:
 * the translation is only exact against the structure the positional marks refer to.
 * The whole translation (legacy scan, new keys, legacy removal, guard) lands in ONE commit:
 * a crash leaves either the legacy state untouched or the migration fully applied.
 * Marks whose legacy index no longer holds a datetime trigger are dropped.
 */
internal suspend fun migrateDatetimeConsumptionLocked(
    automationId: Long,
    readTriggersJson: suspend () -> String?,
    store: DatetimeStateStore,
    log: (String) -> Unit
) {
    val guardKey = "mig3_tdt_$automationId"
    if (store.getBoolean(guardKey)) return
    val triggersJson = readTriggersJson()
    if (triggersJson == null) {
        // Automation deleted: nothing to translate; set the guard so we stop rescanning.
        store.commit(emptySet(), mapOf(guardKey to true))
        return
    }
    val legacyIndices = legacyDatetimeIndices(automationId, store.allKeys())
    val migrated = AutomationTriggerMath.datetimeKeyMigration(
        automationId, legacyIndices, parseDatetimeTypeAt(triggersJson)
    )
    val removals = legacyIndices.map { "tdt_${automationId}_$it" }.toSet()
    val additions = migrated.associateWith { true } + (guardKey to true)
    store.commit(removals, additions)
    if (legacyIndices.isNotEmpty()) {
        log("Automatización $automationId: ${legacyIndices.size} marca(s) de disparador fecha migradas al nuevo formato (conservan su consumo)")
    }
}

private fun prefsStateStore(statePrefs: SharedPreferences): DatetimeStateStore = DatetimeStateStore(
    allKeys = { statePrefs.all.keys.toSet() },
    getBoolean = { key -> statePrefs.getBoolean(key, false) },
    commit = { removals, additions ->
        val editor = statePrefs.edit()
        removals.forEach { editor.remove(it) }
        additions.forEach { (key, value) -> editor.putBoolean(key, value) }
        editor.apply()
    }
)

/**
 * SharedPreferences/Repository binding of the migration core. CALLER MUST HOLD
 * saveAutomationStateMutex. Re-reads the trigger list from the DB inside the section.
 */
internal suspend fun migrateDatetimeConsumptionLocked(
    context: Context,
    repository: AdvancedAutomationRepository,
    automationId: Long
) {
    val statePrefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
    migrateDatetimeConsumptionLocked(
        automationId = automationId,
        readTriggersJson = { repository.getById(automationId)?.triggersJson },
        store = prefsStateStore(statePrefs)
    ) { message -> DiagnosticLog.log(context, "MOTOR", message) }
}

/**
 * One-time translation of an automation's legacy datetime consumption marks
 * ("tdt_<id>_<index>", without the instant) to instant+occurrence keys. Runs before the first
 * consumption read of each engine path (evaluate, test, reschedule); saves migrate through
 * AdvancedAutomationViewModel.save before overwriting the trigger list.
 *
 * Declared residuals (both match legacy behavior, never worse):
 * - Two datetime triggers with the EXACT same instant are indistinguishable: deleting or
 *   inserting one of them shifts occurrences, so the survivor can inherit the other's mark
 *   (suppressed) or lose it (one refire if still past-dated). Multiplicity is otherwise
 *   preserved: each duplicate fires once.
 * - Marks mis-assigned by structural edits made BEFORE the upgrade stay mis-assigned; the
 *   legacy format kept no information to recover them, and legacy behavior was identical.
 */
internal suspend fun migrateDatetimeConsumption(
    context: Context,
    repository: AdvancedAutomationRepository,
    automationId: Long
) = saveAutomationStateMutex.withLock {
    migrateDatetimeConsumptionLocked(context, repository, automationId)
}
