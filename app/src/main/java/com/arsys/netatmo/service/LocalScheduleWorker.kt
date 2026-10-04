package com.arsys.netatmo.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.arsys.netatmo.data.repository.AutomationRepository
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.data.repository.ThermostatRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.util.Calendar

@HiltWorker
class LocalScheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val automationRepository: AutomationRepository,
    private val thermostatRepository: ThermostatRepository,
    private val authRepository: AuthRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val homeId = authRepository.selectedHomeId.first() ?: return Result.success()

        val scheduleAutomations = automationRepository
            .getAutomationsByType("SCHEDULE")
            .first()
            .filter { it.enabled }

        val now = Calendar.getInstance()
        // Calendar.DAY_OF_WEEK: 1=Sun,2=Mon,...,7=Sat -> map to 1=Mon..7=Sun
        val calendarDay = now.get(Calendar.DAY_OF_WEEK)
        val isoDay = when (calendarDay) {
            Calendar.MONDAY    -> 1
            Calendar.TUESDAY   -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY  -> 4
            Calendar.FRIDAY    -> 5
            Calendar.SATURDAY  -> 6
            Calendar.SUNDAY    -> 7
            else               -> 1
        }
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentTotalMinutes = currentHour * 60 + currentMinute

        for (automation in scheduleAutomations) {
            try {
                val trigger = JSONObject(automation.triggerData)
                val daysArray = trigger.optJSONArray("days") ?: continue
                val days = (0 until daysArray.length()).map { daysArray.getInt(it) }.toSet()

                if (isoDay !in days) continue

                val startHour = trigger.optInt("startHour", 0)
                val startMinute = trigger.optInt("startMinute", 0)
                val endHour = trigger.optInt("endHour", 23)
                val endMinute = trigger.optInt("endMinute", 59)

                val startTotal = startHour * 60 + startMinute
                val endTotal = endHour * 60 + endMinute

                if (currentTotalMinutes in startTotal..endTotal) {
                    val roomId = automation.roomId.ifBlank { "" }
                    thermostatRepository.setTemperature(
                        homeId = homeId,
                        roomId = roomId,
                        temperature = automation.targetTemperature
                    )
                }
            } catch (e: Exception) {
                // Skip malformed trigger data and continue with next automation
            }
        }

        return Result.success()
    }
}
