package com.arsys.netatmo.data.repository

import com.arsys.netatmo.data.local.dao.*
import com.arsys.netatmo.data.local.entities.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationRepository @Inject constructor(
    private val automationDao: AutomationDao,
    private val geofenceDao: GeofenceDao,
    private val calendarDao: CalendarAutomationDao,
    private val scenarioDao: ScenarioDao,
    private val scheduleDao: ScheduleDao,
    private val automationLogDao: AutomationLogDao
) {
    fun getAllAutomations(): Flow<List<AutomationEntity>> = automationDao.getAllAutomations()

    fun getAutomationsByType(type: String): Flow<List<AutomationEntity>> =
        automationDao.getAutomationsByType(type)

    suspend fun getAutomationById(id: Long): AutomationEntity? = automationDao.getAutomationById(id)

    suspend fun saveAutomation(automation: AutomationEntity): Long =
        automationDao.insertAutomation(automation)

    suspend fun updateAutomation(automation: AutomationEntity) =
        automationDao.updateAutomation(automation)

    suspend fun deleteAutomation(automation: AutomationEntity) {
        geofenceDao.deleteByAutomationId(automation.id)
        automationDao.deleteAutomation(automation)
    }

    suspend fun setAutomationEnabled(id: Long, enabled: Boolean) =
        automationDao.setAutomationEnabled(id, enabled)

    suspend fun updateLastTriggered(id: Long, timestamp: Long) =
        automationDao.updateLastTriggered(id, timestamp)

    // Logs de ejecución
    fun getRecentLogs(): Flow<List<AutomationLogEntity>> = automationLogDao.getRecentLogs()

    fun getLogsForAutomation(automationId: Long): Flow<List<AutomationLogEntity>> =
        automationLogDao.getLogsForAutomation(automationId)

    suspend fun logExecution(log: AutomationLogEntity) = automationLogDao.insert(log)

    // Geofences
    fun getAllGeofences(): Flow<List<GeofenceEntity>> = geofenceDao.getAllGeofences()

    suspend fun getGeofenceByAutomation(automationId: Long): GeofenceEntity? =
        geofenceDao.getGeofenceByAutomation(automationId)

    suspend fun saveGeofence(geofence: GeofenceEntity) = geofenceDao.insertGeofence(geofence)

    suspend fun deleteGeofence(geofence: GeofenceEntity) = geofenceDao.deleteGeofence(geofence)

    // Calendarios
    fun getAllCalendarAutomations(): Flow<List<CalendarAutomationEntity>> =
        calendarDao.getAllCalendarAutomations()

    suspend fun saveCalendarAutomation(automation: CalendarAutomationEntity): Long =
        calendarDao.insert(automation)

    suspend fun updateCalendarAutomation(automation: CalendarAutomationEntity) =
        calendarDao.update(automation)

    suspend fun deleteCalendarAutomation(automation: CalendarAutomationEntity) =
        calendarDao.delete(automation)

    suspend fun getEnabledCalendarAutomations(): List<CalendarAutomationEntity> =
        calendarDao.getEnabledCalendarAutomations()

    // Escenarios
    fun getAllScenarios(): Flow<List<ScenarioEntity>> = scenarioDao.getAllScenarios()

    suspend fun getScenarioById(id: Long): ScenarioEntity? = scenarioDao.getScenarioById(id)

    suspend fun saveScenario(scenario: ScenarioEntity): Long = scenarioDao.insertScenario(scenario)

    suspend fun updateScenario(scenario: ScenarioEntity) = scenarioDao.updateScenario(scenario)

    suspend fun deleteScenario(scenario: ScenarioEntity) = scenarioDao.deleteScenario(scenario)

    // Horarios
    fun getSchedulesByHome(homeId: String): Flow<List<ScheduleEntity>> =
        scheduleDao.getSchedulesByHome(homeId)

    suspend fun saveSchedule(schedule: ScheduleEntity): Long = scheduleDao.insertSchedule(schedule)

    suspend fun updateSchedule(schedule: ScheduleEntity) = scheduleDao.updateSchedule(schedule)

    suspend fun deleteSchedule(schedule: ScheduleEntity) = scheduleDao.deleteSchedule(schedule)

    suspend fun getScheduleById(id: Long): ScheduleEntity? = scheduleDao.getScheduleById(id)
}
