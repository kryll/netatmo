package com.arsys.netatmo.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.arsys.netatmo.BuildConfig
import com.arsys.netatmo.data.api.AuthApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authApiService: AuthApiService
) {
    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val EXPIRES_AT = longPreferencesKey("expires_at")
        val HOME_ID = stringPreferencesKey("selected_home_id")
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ACCESS_TOKEN] != null
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.ACCESS_TOKEN]
    }

    val selectedHomeId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.HOME_ID]
    }

    suspend fun getValidAccessToken(): String? {
        val prefs = context.dataStore.data.first()
        val token = prefs[Keys.ACCESS_TOKEN] ?: return null
        val expiresAt = prefs[Keys.EXPIRES_AT] ?: 0L
        val refreshToken = prefs[Keys.REFRESH_TOKEN] ?: return null

        return if (System.currentTimeMillis() < expiresAt - 60_000) {
            token
        } else {
            refreshTokens(refreshToken)
        }
    }

    suspend fun loginWithCode(code: String): Result<Unit> {
        return try {
            val response = authApiService.getToken(
                grantType = "authorization_code",
                clientId = BuildConfig.NETATMO_CLIENT_ID,
                clientSecret = BuildConfig.NETATMO_CLIENT_SECRET,
                code = code,
                redirectUri = BuildConfig.NETATMO_REDIRECT_URI
            )
            if (response.isSuccessful) {
                response.body()?.let { token ->
                    saveTokens(token.accessToken, token.refreshToken, token.expiresIn)
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("Auth error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun refreshTokens(refreshToken: String): String? {
        return try {
            val response = authApiService.getToken(
                grantType = "refresh_token",
                clientId = BuildConfig.NETATMO_CLIENT_ID,
                clientSecret = BuildConfig.NETATMO_CLIENT_SECRET,
                refreshToken = refreshToken
            )
            if (response.isSuccessful) {
                response.body()?.let { token ->
                    saveTokens(token.accessToken, token.refreshToken, token.expiresIn)
                    token.accessToken
                }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.REFRESH_TOKEN] = refreshToken
            prefs[Keys.EXPIRES_AT] = System.currentTimeMillis() + (expiresIn * 1000L)
        }
    }

    suspend fun setSelectedHome(homeId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HOME_ID] = homeId
        }
    }

    suspend fun logout() {
        context.dataStore.edit { it.clear() }
    }

    fun getAuthUrl(): String {
        return "https://api.netatmo.com/oauth2/authorize" +
                "?client_id=${BuildConfig.NETATMO_CLIENT_ID}" +
                "&redirect_uri=${BuildConfig.NETATMO_REDIRECT_URI}" +
                "&scope=read_thermostat+write_thermostat" +
                "&response_type=code"
    }
}
