package com.arsys.netatmo.data.api

import com.arsys.netatmo.data.api.models.MeteosourcePointResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface MeteosourceApiService {

    @GET("api/v1/free/point")
    suspend fun getCurrentWeather(
        @Query("place_id") placeId: String,
        @Query("sections") sections: String = "current",
        @Query("units") units: String = "metric",
        @Query("key") apiKey: String
    ): Response<MeteosourcePointResponse>
}
