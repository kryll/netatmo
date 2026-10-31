package com.arsys.netatmo.service

import android.content.Context
import com.arsys.netatmo.data.repository.AdvancedAutomationRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray

private const val STATE_PREFS = "advanced_automation_state"

/**
 * One short critical section shared by the two writers of datetime-consumption state:
 * saving an automation (the Room write only, in AdvancedAutomationViewModel.save) and the
 * legacy-mark migration below (its fresh DB read plus its prefs commit). Held for
 * milliseconds, never during evaluation or actions, so a running automation (including
 * long "delay" actions) never blocks a save, and a save never interleaves with the
 * snapshot a migration translates.
 */
internal val saveAutomationStateMutex = Mutex()

/**
 * Translates an automation's legacy datetime consumption marks ("tdt_<id>_<index>", without
 * the instant) to instant+occurrence keys, once per automation (guard "mig3_tdt_<id>").
 * The trigger list is re-read from the DB INSIDE saveAutomationStateMutex, so the
 * translated snapshot never interleaves with a concurrent save. The whole translation
 * (legacy scan, new keys, legacy removal, guard) lands in ONE SharedPreferences commit:
 * a crash leaves either the legacy state untouched or the migration fully applied.
 *
 * Declared residual: the legacy marks are positional, so a STRUCTURAL edit (deleting a
 * trigger placed before a fired datetime) made between the fire and the first post-upgrade
 * run can mis-assign the mark: the fired trigger may lose it (it would fire once more if
 * still past-dated) and the trigger that shifted into its place may inherit it (suppressed
 * until its next date edit). Date edits, additions and reorders are all exact.
 */
internal suspend fun migrateDatetimeConsumption(
    context: Context,
    repository: AdvancedAutomationRepository,
    automationId: Long
) {
    val statePrefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
    val guardKey = "mig3_tdt_$automationId"
    if (statePrefs.getBoolean(guardKey, false)) return
    saveAutomationStateMutex.withLock {
        if (statePrefs.getBoolean(guardKey, false)) return@withLock
        val triggersJson = repository.getById(automationId)?.triggersJson
        if (triggersJson == null) {
            // Automation deleted: nothing to translate; set the guard so we stop rescanning.
            statePrefs.edit().putBoolean(guardKey, true).apply()
            return@withLock
        }
        val legacyPrefix = "tdt_${automationId}_"
        // Legacy keys end at the index (bare digits); "tdt3_" keys never match this prefix.
        val legacyIndices = statePrefs.all.keys.mapNotNull { key ->
            if (!key.startsWith(legacyPrefix)) return@mapNotNull null
            key.removePrefix(legacyPrefix).toIntOrNull()
        }.toSet()
        val triggers = try {
            val arr = JSONArray(triggersJson)
            (0 until arr.length()).map {
                val trigger = arr.getJSONObject(it)
                trigger.optString("type") to trigger.optLong("at", 0L)
            }
        } catch (e: Exception) {
            emptyList()
        }
        val migrated = AutomationTriggerMath.datetimeKeyMigration(automationId, legacyIndices, triggers)
        val editor = statePrefs.edit()
        legacyIndices.forEach { editor.remove("tdt_${automationId}_$it") }
        migrated.forEach { editor.putBoolean(it, true) }
        editor.putBoolean(guardKey, true)
        editor.apply()
        if (legacyIndices.isNotEmpty()) {
            DiagnosticLog.log(context, "MOTOR", "Automatización $automationId: ${legacyIndices.size} marca(s) de disparador fecha migradas al nuevo formato (conservan su consumo)")
        }
    }
}
