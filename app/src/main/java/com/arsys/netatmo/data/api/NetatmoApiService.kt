package com.arsys.netatmo.data.api

import com.arsys.netatmo.data.api.models.*
import retrofit2.Response
import retrofit2.http.*

interface NetatmoApiService {

    @GET("api/homesdata")
    suspend fun getHomesData(
        @Query("home_id") homeId: String? = null
    ): Response<HomesDataResponse>

    @GET("api/homestatus")
    suspend fun getHomeStatus(
        @Query("home_id") homeId: String,
        @Query("device_types") deviceTypes: String = "NRV,NATherm1,OTM"
    ): Response<HomeStatusResponse>

    @POST("api/setroomthermpoint")
    @FormUrlEncoded
    suspend fun setRoomThermpoint(
        @Field("home_id") homeId: String,
        @Field("room_id") roomId: String,
        @Field("mode") mode: String,
        @Field("temp") temperature: Double? = null,
        @Field("endtime") endTime: Long? = null
    ): Response<BasicResponse>

    @POST("api/switchhomeschedule")
    @FormUrlEncoded
    suspend fun switchHomeSchedule(
        @Field("home_id") homeId: String,
        @Field("schedule_id") scheduleId: String
    ): Response<BasicResponse>

    @POST("api/synchomeschedule")
    suspend fun syncHomeSchedule(
        @Body schedule: Map<String, Any>
    ): Response<BasicResponse>

    @POST("api/createnewhomeschedule")
    suspend fun createHomeSchedule(
        @Body schedule: Map<String, Any>
    ): Response<BasicResponse>

    @POST("api/deletehomeschedule")
    @FormUrlEncoded
    suspend fun deleteHomeSchedule(
        @Field("home_id") homeId: String,
        @Field("schedule_id") scheduleId: String
    ): Response<BasicResponse>

    @POST("api/setthermmode")
    @FormUrlEncoded
    suspend fun setThermMode(
        @Field("home_id") homeId: String,
        @Field("mode") mode: String,
        @Field("endtime") endTime: Long? = null,
        @Field("temp") temperature: Double? = null
    ): Response<BasicResponse>

    @GET("api/getmeasure")
    suspend fun getMeasure(
        @Query("device_id") deviceId: String,
        @Query("module_id") moduleId: String? = null,
        @Query("scale") scale: String = "1hour",
        @Query("type") type: String = "temperature,min_temp,max_temp,sum_energy_elec",
        @Query("date_begin") dateBegin: Long? = null,
        @Query("date_end") dateEnd: Long? = null,
        @Query("limit") limit: Int? = null,
        @Query("optimize") optimize: Boolean = true,
        @Query("real_time") realTime: Boolean = false
    ): Response<MeasureResponse>

    @GET("api/getroommeasure")
    suspend fun getRoomMeasure(
        @Query("home_id") homeId: String,
        @Query("room_id") roomId: String,
        @Query("scale") scale: String = "1hour",
        @Query("type") type: String = "temperature,min_temp,max_temp,sum_energy_elec",
        @Query("date_begin") dateBegin: Long? = null,
        @Query("date_end") dateEnd: Long? = null,
        @Query("limit") limit: Int? = null
    ): Response<MeasureResponse>

    @GET("api/getstationsdata")
    suspend fun getStationsData(): Response<StationsDataResponse>
}
