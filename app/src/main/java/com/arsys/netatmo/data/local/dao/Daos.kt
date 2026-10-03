package com.arsys.netatmo.data.local.dao

import androidx.room.*
import com.arsys.netatmo.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automations ORDER BY createdAt DESC")
    fun getAllAutomations(): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automations WHERE type = :type ORDER BY createdAt DESC")
    fun getAutomationsByType(type: String): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automations WHERE enabled = 1")
    suspend fun getEnabledAutomations(): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun getAutomationById(id: Long): AutomationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAutomation(automation: AutomationEntity): Long

    @Update
    suspend fun updateAutomation(automation: AutomationEntity)

    @Delete
    suspend fun deleteAutomation(automation: AutomationEntity)

    @Query("UPDATE automations SET enabled = :enabled WHERE id = :id")
    suspend fun setAutomationEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE automations SET lastTriggeredAt = :timestamp WHERE id = :id")
    suspend fun updateLastTriggered(id: Long, timestamp: Long)
}

@Dao
interface GeofenceDao {
    @Query("SELECT * FROM geofences ORDER BY name ASC")
    fun getAllGeofences(): Flow<List<GeofenceEntity>>

    @Query("SELECT * FROM geofences WHERE automationId = :automationId")
    suspend fun getGeofenceByAutomation(automationId: Long): GeofenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeofence(geofence: GeofenceEntity)

    @Delete
    suspend fun deleteGeofence(geofence: GeofenceEntity)

    @Query("DELETE FROM geofences WHERE automationId = :automationId")
    suspend fun deleteByAutomationId(automationId: Long)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules WHERE homeId = :homeId ORDER BY name ASC")
    fun getSchedulesByHome(homeId: String): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getScheduleById(id: Long): ScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ScheduleEntity): Long

    @Update
    suspend fun updateSchedule(schedule: ScheduleEntity)

    @Delete
    suspend fun deleteSchedule(schedule: ScheduleEntity)
}

@Dao
interface ScenarioDao {
    @Query("SELECT * FROM scenarios ORDER BY name ASC")
    fun getAllScenarios(): Flow<List<ScenarioEntity>>

    @Query("SELECT * FROM scenarios WHERE id = :id")
    suspend fun getScenarioById(id: Long): ScenarioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScenario(scenario: ScenarioEntity): Long

    @Update
    suspend fun updateScenario(scenario: ScenarioEntity)

    @Delete
    suspend fun deleteScenario(scenario: ScenarioEntity)
}

@Dao
interface TemperatureHistoryDao {
    @Query("SELECT * FROM temperature_history WHERE homeId = :homeId AND roomId = :roomId AND timestamp >= :from ORDER BY timestamp ASC")
    fun getHistory(homeId: String, roomId: String, from: Long): Flow<List<TemperatureHistoryEntity>>

    @Query("SELECT * FROM temperature_history WHERE homeId = :homeId AND roomId = :roomId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentHistory(homeId: String, roomId: String, limit: Int = 100): List<TemperatureHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: TemperatureHistoryEntity)

    @Query("DELETE FROM temperature_history WHERE timestamp < :before")
    suspend fun deleteOldHistory(before: Long)
}

@Dao
interface CalendarAutomationDao {
    @Query("SELECT * FROM calendar_automations ORDER BY id DESC")
    fun getAllCalendarAutomations(): Flow<List<CalendarAutomationEntity>>

    @Query("""
        SELECT ca.* FROM calendar_automations ca
        INNER JOIN automations a ON ca.automationId = a.id
        WHERE ca.enabled = 1 AND a.enabled = 1
    """)
    suspend fun getEnabledCalendarAutomations(): List<CalendarAutomationEntity>

    @Query("SELECT * FROM calendar_automations WHERE automationId = :automationId LIMIT 1")
    suspend fun getByAutomationId(automationId: Long): CalendarAutomationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(automation: CalendarAutomationEntity): Long

    @Update
    suspend fun update(automation: CalendarAutomationEntity)

    @Delete
    suspend fun delete(automation: CalendarAutomationEntity)
}

@Dao
interface HomeCacheDao {
    @Query("SELECT * FROM homes_cache")
    fun getAllHomes(): Flow<List<HomeCacheEntity>>

    @Query("SELECT * FROM homes_cache WHERE homeId = :homeId")
    suspend fun getHome(homeId: String): HomeCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHome(home: HomeCacheEntity)

    @Delete
    suspend fun deleteHome(home: HomeCacheEntity)
}
