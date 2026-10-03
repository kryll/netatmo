package com.arsys.netatmo.data.api.models

import com.google.gson.annotations.SerializedName

// --- Auth ---
data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("expires_in") val expiresIn: Int,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("scope") val scope: List<String>
)

// --- Home Status ---
data class HomeStatusResponse(
    @SerializedName("status") val status: String,
    @SerializedName("body") val body: HomeStatusBody
)

data class HomeStatusBody(
    @SerializedName("home") val home: HomeStatus
)

data class HomeStatus(
    @SerializedName("id") val id: String,
    @SerializedName("modules") val modules: List<ModuleStatus>?,
    @SerializedName("rooms") val rooms: List<RoomStatus>?
)

data class RoomStatus(
    @SerializedName("id") val id: String,
    @SerializedName("reachable") val reachable: Boolean?,
    @SerializedName("therm_measured_temperature") val measuredTemperature: Double?,
    @SerializedName("therm_setpoint_temperature") val setpointTemperature: Double?,
    @SerializedName("therm_setpoint_mode") val setpointMode: String?,
    @SerializedName("therm_setpoint_end_time") val setpointEndTime: Long?,
    @SerializedName("heating_power_request") val heatingPower: Int?
)

data class ModuleStatus(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String?,
    @SerializedName("reachable") val reachable: Boolean?,
    @SerializedName("firmware_revision") val firmwareRevision: Int?,
    @SerializedName("rf_strength") val rfStrength: Int?,
    @SerializedName("wifi_strength") val wifiStrength: Int?,
    @SerializedName("battery_level") val batteryLevel: Int?,
    @SerializedName("boiler_status") val boilerStatus: Boolean?
)

// --- Homes Data ---
data class HomesDataResponse(
    @SerializedName("status") val status: String,
    @SerializedName("body") val body: HomesDataBody
)

data class HomesDataBody(
    @SerializedName("homes") val homes: List<Home>
)

data class Home(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("country") val country: String?,
    @SerializedName("timezone") val timezone: String?,
    @SerializedName("rooms") val rooms: List<Room>?,
    @SerializedName("modules") val modules: List<Module>?,
    @SerializedName("schedules") val schedules: List<Schedule>?,
    @SerializedName("therm_setpoint_default_duration") val setpointDuration: Int?
)

data class Room(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String?,
    @SerializedName("module_ids") val moduleIds: List<String>?
)

data class Module(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String,
    @SerializedName("name") val name: String?,
    @SerializedName("room_id") val roomId: String?,
    @SerializedName("bridge") val bridge: String?
)

data class Schedule(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("selected") val selected: Boolean?,
    @SerializedName("type") val type: String?,
    @SerializedName("timetable") val timetable: List<TimeSlot>?,
    @SerializedName("zones") val zones: List<Zone>?
)

data class TimeSlot(
    @SerializedName("m_offset") val minuteOffset: Int,
    @SerializedName("zone_id") val zoneId: Int
)

data class Zone(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String?,
    @SerializedName("type") val type: Int?,
    @SerializedName("rooms") val rooms: List<ZoneRoom>?
)

data class ZoneRoom(
    @SerializedName("id") val id: String,
    @SerializedName("therm_setpoint_temperature") val temperature: Double?
)

// --- Measurements ---
data class MeasureResponse(
    @SerializedName("status") val status: String,
    @SerializedName("body") val body: List<MeasureBody>?
)

data class MeasureBody(
    @SerializedName("beg_time") val beginTime: Long,
    @SerializedName("step_time") val stepTime: Int?,
    @SerializedName("value") val value: List<List<Double?>>
)

// --- Set Thermostat ---
data class SetThermpointRequest(
    @SerializedName("home_id") val homeId: String,
    @SerializedName("room_id") val roomId: String,
    @SerializedName("mode") val mode: String,
    @SerializedName("temp") val temperature: Double? = null,
    @SerializedName("endtime") val endTime: Long? = null
)

data class BasicResponse(
    @SerializedName("status") val status: String,
    @SerializedName("time_server") val timeServer: Long?
)
