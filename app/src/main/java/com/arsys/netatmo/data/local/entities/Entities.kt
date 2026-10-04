package com.arsys.netatmo.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.arsys.netatmo.data.local.Converters

@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // GEOFENCE, CALENDAR, SCHEDULE, SCENARIO
    val enabled: Boolean = true,
    val homeId: String,
    val roomId: String,
    val targetTemperature: Double,
    val mode: String = "manual", // manual, schedule, away, hg, off
    val triggerData: String, // JSON serialized trigger config
    val createdAt: Long = System.currentTimeMillis(),
    val lastTriggeredAt: Long? = null
)

@Entity(tableName = "geofences")
data class GeofenceEntity(
    @PrimaryKey val id: String,
    val automationId: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Float, // metros
    val triggerOnEnter: Boolean = true,
    val triggerOnExit: Boolean = false,
    val temperatureOnEnter: Double? = null,
    val temperatureOnExit: Double? = null,
    val modeOnEnter: String? = null,
    val modeOnExit: String? = null
)

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val homeId: String,
    val name: String,
    val netatmoScheduleId: String? = null,
    val isActive: Boolean = false,
    val timetableJson: String, // JSON de slots horarios
    val zonesJson: String,     // JSON de zonas y temperaturas
    val type: String = "therm", // therm, cooling
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "scenarios")
data class ScenarioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = "thermostat",
    val homeId: String,
    val actionsJson: String, // JSON lista de acciones [{ roomId, temp, mode }]
    val color: String = "#1976D2",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "temperature_history")
data class TemperatureHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val homeId: String,
    val roomId: String,
    val timestamp: Long,
    val temperature: Double,
    val setpoint: Double?,
    val heatingActive: Boolean = false
)

@Entity(tableName = "calendar_automations")
data class CalendarAutomationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val automationId: Long,
    val calendarId: Long,
    val calendarName: String,
    val eventTitleFilter: String? = null, // null = todos los eventos
    val minutesBefore: Int = 30, // precalentar N minutos antes
    val targetTemperature: Double,
    val mode: String = "manual",
    val enabled: Boolean = true
)

@Entity(tableName = "automation_logs")
data class AutomationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val automationId: Long,
    val automationName: String,
    val triggerType: String, // ENTER, EXIT, CALENDAR, MANUAL
    val timestamp: Long = System.currentTimeMillis(),
    val success: Boolean,
    val errorMessage: String? = null
)

@Entity(tableName = "homes_cache")
data class HomeCacheEntity(
    @PrimaryKey val homeId: String,
    val name: String,
    val country: String?,
    val timezone: String?,
    val roomsJson: String,
    val modulesJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "family_members")
data class FamilyMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isHome: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)
