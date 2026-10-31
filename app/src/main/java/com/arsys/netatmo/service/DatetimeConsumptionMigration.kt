package com.arsys.netatmo.service

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray

private const val STATE_PREFS = "advanced_automation_state"

/**
 * Serializes only the migration (a few SharedPreferences reads and one atomic commit, in the
 * millisecond range) between the engine, the alarm scheduler and the test report, so a first
 * migration cannot interleave with a second one. It is never held during evaluation or
 * actions, so a running automation (including long "delay" actions) never blocks behind it.
 */
private val migrationMutex = Mutex()

/**
 * Translates an automation's legacy datetime consumption marks ("tdt_<id>_<index>", without
 * the instant) to instant-qualified keys, once per automation (guard key "mig2_tdt_<id>").
 * The whole translation (legacy scan, key writes, legacy removal and the guard) lands in ONE
 * SharedPreferences commit, so a crash either leaves the legacy state untouched or the
 * migration fully applied; a partial migration is never marked complete. A save that lands
 * mid-migration can only make the translation conservative (a stale consumed key on an
 * instant that no longer exists is garbage but harmless; an instant edited during the
 * migration simply stays unconsumed and fires at its new time, which matches the new
 * edit-to-rearm semantics).
 */
internal suspend fun migrateDatetimeConsumption(context: Context, automationId: Long, triggersJson: String) {
    val statePrefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
    val guardKey = "mig2_tdt_$automationId"
    if (statePrefs.getBoolean(guardKey, false)) return
    migrationMutex.withLock {
        if (statePrefs.getBoolean(guardKey, false)) return@withLock
        val legacyPrefix = "tdt_${automationId}_"
        // Legacy keys end at the index (bare digits); "tdt2_" keys never match this prefix.
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
