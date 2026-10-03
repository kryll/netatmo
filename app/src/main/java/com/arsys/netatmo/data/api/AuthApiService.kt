package com.arsys.netatmo.data.api

import com.arsys.netatmo.data.api.models.TokenResponse
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface AuthApiService {

    @FormUrlEncoded
    @POST("oauth2/token")
    suspend fun getToken(
        @Field("grant_type") grantType: String,
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String? = null,
        @Field("redirect_uri") redirectUri: String? = null,
        @Field("refresh_token") refreshToken: String? = null,
        @Field("username") username: String? = null,
        @Field("password") password: String? = null,
        @Field("scope") scope: String = "read_thermostat write_thermostat read_presence"
    ): Response<TokenResponse>
}
