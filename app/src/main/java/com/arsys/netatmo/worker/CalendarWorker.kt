package com.arsys.netatmo.worker

import android.content.Context
import android.provider.CalendarContract
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.arsys.netatmo.data.repository.ApiResult
import com.arsys.netatmo.data.repository.AutomationRepository
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.data.repository.ThermostatRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class CalendarWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val automationRepository: AutomationRepository,
    private val thermostatRepository: ThermostatRepository,
    private val authRepository: AuthRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val homeId = authRepository.selectedHomeId.first() ?: return Result.success()
        val calendarAutomations = automationRepository.getEnabledCalendarAutomations()

        val now = System.currentTimeMillis()

        calendarAutomations.forEach { calAuto ->
            val upcomingEvents = getUpcomingEvents(
                calendarId = calAuto.calendarId,
                titleFilter = calAuto.eventTitleFilter,
                withinMs = (calAuto.minutesBefore + 15) * 60 * 1000L,
                now = now
            )

            upcomingEvents.forEach { eventStart ->
                val triggerTime = eventStart - (calAuto.minutesBefore * 60 * 1000L)
                if (triggerTime in (now - 15 * 60 * 1000L)..now) {
                    thermostatRepository.setTemperature(homeId, "", calAuto.targetTemperature)
                }
            }
        }

        return Result.success()
    }

    private fun getUpcomingEvents(
        calendarId: Long,
        titleFilter: String?,
        withinMs: Long,
        now: Long
    ): List<Long> {
        val events = mutableListOf<Long>()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART
        )
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? " +
                "AND ${CalendarContract.Events.DTSTART} BETWEEN ? AND ?"
        val selectionArgs = arrayOf(
            calendarId.toString(),
            now.toString(),
            (now + withinMs).toString()
        )

        try {
            applicationContext.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection, selection, selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val title = cursor.getString(1) ?: ""
                    val start = cursor.getLong(2)
                    if (titleFilter == null || title.contains(titleFilter, ignoreCase = true)) {
                        events.add(start)
                    }
                }
            }
        } catch (e: SecurityException) {
            // Sin permisos de calendario
        }

        return events
    }
}
