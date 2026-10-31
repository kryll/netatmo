package com.arsys.netatmo.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.arsys.netatmo.data.repository.AdvancedAutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arms one alarm for the earliest upcoming "time"/"datetime" trigger across the enabled
 * automations, so those triggers do not depend solely on the periodic (~15 min, deferrable
 * by Doze/battery) WorkManager pass. The alarm only wakes the engine: evaluation, firing
 * marks and actions stay in AdvancedAutomationEngine, whose per-day/per-instant marks
 * prevent double execution.
 */
@Singleton
class AutomationAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val advancedAutomationRepository: AdvancedAutomationRepository
) {
    companion object {
        const val ACTION_FIRE = "com.arsys.netatmo.action.AUTOMATION_ALARM"
        // Same prefs file the engine uses for fired/consumed marks.
        private const val STATE_PREFS = "advanced_automation_state"
        private const val KEY_NEXT_AT = "alarm_next_at"
    }

    private val alarmManager: AlarmManager?
        get() = context.getSystemService(AlarmManager::class.java)
    private val statePrefs
        get() = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, AutomationAlarmReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Instant of the currently armed alarm, 0 when none (diagnostics). */
    fun nextArmedAt(): Long = statePrefs.getLong(KEY_NEXT_AT, 0L)

    /** Whether exact alarms are allowed on this phone (always true before Android 12). */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager?.canScheduleExactAlarms() == true

    /**
     * Recomputes and arms the next alarm. Call after every engine run, after saving or
     * deleting an automation, and on boot/app update (alarms do not survive those).
     */
    suspend fun reschedule() {
        val now = System.currentTimeMillis()
        var next: Long? = null
        val diag = StringBuilder()
        for (automation in advancedAutomationRepository.getEnabledAdvancedAutomations()) {
            migrateDatetimeConsumption(context, advancedAutomationRepository, automation.id)
            val triggers = try {
                JSONArray(automation.triggersJson)
            } catch (e: Exception) {
                continue
            }
            val typeAtPairs = (0 until triggers.length()).map {
                val t = triggers.getJSONObject(it)
                t.optString("type") to t.optLong("at", 0L)
            }
            for (i in 0 until triggers.length()) {
                val trg = triggers.getJSONObject(i)
                val instant = nextInstant(automation.id, i, AutomationTriggerMath.datetimeOccurrence(typeAtPairs, i), trg, now)
                if (trg.optString("type") == "time" || trg.optString("type") == "datetime") {
                    diag.append("\n    «${automation.name}» disparador ${i + 1} (${trg.optString("type")}): próximo ${instant?.let { DiagnosticLog.fmt(it) } ?: "ninguno"}")
                }
                if (instant == null) continue
                if (next == null || instant < next!!) next = instant
            }
        }
        val pi = pendingIntent()
        val armedAt = next
        DiagnosticLog.log(context, "ALARMA", "Rearmado. Ahora ${DiagnosticLog.fmt(now)}. Alarma exacta permitida=${canScheduleExact()}. Elegida=${armedAt?.let { DiagnosticLog.fmt(it) } ?: "ninguna"}.$diag")
        if (armedAt == null) {
            alarmManager?.cancel(pi)
            statePrefs.edit().remove(KEY_NEXT_AT).apply()
            return
        }
        if (canScheduleExact()) {
            alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, armedAt, pi)
        } else {
            // Without the exact-alarm grant this still beats the bare periodic pass and
            // never crashes; the periodic worker remains as a second fallback.
            alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, armedAt, pi)
        }
        statePrefs.edit().putLong(KEY_NEXT_AT, armedAt).apply()
    }

    private fun nextInstant(automationId: Long, index: Int, occurrence: Int, trigger: JSONObject, now: Long): Long? =
        when (trigger.optString("type")) {
            "time" -> {
                val minutes = parseTimeToMinutes(trigger.optString("time", "")) ?: return null
                val days = trigger.optJSONArray("days")
                    ?.let { arr -> (0 until arr.length()).map { arr.getInt(it) }.toSet() }
                val cal = Calendar.getInstance().apply { timeInMillis = now }
                val dayKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
                val firedToday = statePrefs.getString("tday_${automationId}_$index", null) == dayKey
                AutomationTriggerMath.nextTimeTriggerInstant(days, minutes, firedToday, now)
            }
            "datetime" -> {
                val at = trigger.optLong("at", 0L)
                AutomationTriggerMath.nextDatetimeInstant(
                    at,
                    statePrefs.getBoolean(AutomationTriggerMath.datetimeConsumedKey(automationId, at, occurrence), false)
                )
            }
            else -> null
        }

    private fun parseTimeToMinutes(hhmm: String): Int? = try {
        val parts = hhmm.split(":")
        require(parts.size >= 2)
        parts[0].toInt() * 60 + parts[1].toInt()
    } catch (e: Exception) {
        null
    }
}
