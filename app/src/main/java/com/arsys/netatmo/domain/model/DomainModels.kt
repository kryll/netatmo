package com.arsys.netatmo.domain.model

data class ThermostatState(
    val homeId: String,
    val homeName: String,
    val rooms: List<RoomState>,
    val modules: List<ModuleState>,
    val activeScheduleId: String? = null,
    val schedules: List<ScheduleInfo> = emptyList()
)

data class ScheduleInfo(
    val id: String,
    val name: String,
    val isActive: Boolean
)

data class RoomState(
    val id: String,
    val name: String,
    val currentTemp: Double?,
    val targetTemp: Double?,
    val mode: ThermostatMode,
    val heatingActive: Boolean,
    val reachable: Boolean,
    val setpointEndTime: Long?
)

data class ModuleState(
    val id: String,
    val type: String,
    val reachable: Boolean,
    val batteryLevel: Int?,
    val rfStrength: Int?,
    val wifiStrength: Int?,
    val boilerStatus: Boolean?,
    val co2Level: Int? = null,        // NAMain: nivel CO2 en ppm
    val noise: Int? = null,           // NAMain: ruido en dB
    val humidity: Int? = null,        // NAMain: humedad relativa %
    val pressure: Double? = null,     // NAMain: presión atmosférica en mbar
    val modulationLevel: Int? = null, // NAPlug: modulación caldera 0-100%
)

enum class ThermostatMode(val apiValue: String, val displayName: String) {
    MANUAL("manual", "Manual"),
    SCHEDULE("schedule", "Programado"),
    AWAY("away", "Ausente"),
    FROST_GUARD("hg", "Anticongelación"),
    OFF("off", "Apagado");

    companion object {
        fun fromApi(value: String?) = entries.find { it.apiValue == value } ?: SCHEDULE
    }
}

data class AutomationModel(
    val id: Long,
    val name: String,
    val type: AutomationType,
    val enabled: Boolean,
    val homeId: String,
    val roomId: String,
    val targetTemperature: Double,
    val mode: ThermostatMode,
    val lastTriggeredAt: Long?
)

enum class AutomationType(val displayName: String, val icon: String) {
    GEOFENCE("Geovalla", "location_on"),
    CALENDAR("Calendario", "calendar_today"),
    SCHEDULE("Horario", "schedule"),
    SCENARIO("Escenario", "auto_awesome")
}

data class GeofenceModel(
    val id: String,
    val automationId: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Float,
    val triggerOnEnter: Boolean,
    val triggerOnExit: Boolean,
    val temperatureOnEnter: Double?,
    val temperatureOnExit: Double?,
    val modeOnEnter: ThermostatMode?,
    val modeOnExit: ThermostatMode?
)

data class ScenarioModel(
    val id: Long,
    val name: String,
    val icon: String,
    val color: String,
    val homeId: String,
    val actions: List<ScenarioAction>
)

data class ScenarioAction(
    val roomId: String,
    val roomName: String,
    val temperature: Double,
    val mode: ThermostatMode
)

data class ScheduleModel(
    val id: Long,
    val homeId: String,
    val name: String,
    val netatmoScheduleId: String?,
    val isActive: Boolean,
    val timetable: List<TimeSlotModel>,
    val zones: List<ZoneModel>
)

data class TimeSlotModel(
    val minuteOffset: Int,
    val zoneId: Int,
    val dayOfWeek: Int = minuteOffset / (24 * 60),
    val timeOfDay: Int = minuteOffset % (24 * 60)
)

data class ZoneModel(
    val id: Int,
    val name: String,
    val temperature: Double
)

data class TemperatureDataPoint(
    val timestamp: Long,
    val temperature: Double,
    val setpoint: Double?,
    val heatingActive: Boolean
)

data class CalendarEvent(
    val id: Long,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val calendarName: String,
    val calendarId: Long,
    val location: String?
)

data class CalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String,
    val color: Int
)

data class MaintenanceRecord(
    val id: Long = 0,
    val date: Long,
    val type: MaintenanceType,
    val description: String,
    val technicianName: String = "",
    val certificateRef: String = "",
    val warrantyExtendedUntil: Long? = null
)

enum class MaintenanceType(val displayName: String) {
    ANNUAL_REVISION("Revisión anual"),
    PURGE("Purga sistema"),
    PRESSURE_CHECK("Control de presión"),
    VALVE_CALIBRATION("Calibración válvula"),
    OTHER("Otro")
}

data class BoilerHealth(
    val pressureBar: Double?,
    val modulationPct: Int?,
    val impulsionTempC: Double?,
    val isModulating: Boolean,
    val healthScore: Int,
    val healthLabel: String
)

data class WeatherInfo(
    val outdoorTemp: Double?,
    val humidity: Int?,
    val windKmh: Double?,
    val condition: String
)
