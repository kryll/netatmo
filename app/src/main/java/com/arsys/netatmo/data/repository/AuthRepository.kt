package com.arsys.netatmo.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.arsys.netatmo.BuildConfig
import com.arsys.netatmo.data.api.AuthApiService
import com.arsys.netatmo.util.AuthDebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authApiService: AuthApiService
) {
    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_HOME_ID = "selected_home_id"
        const val KEY_OAUTH_STATE = "oauth_state"
        const val KEY_PKCE_VERIFIER = "pkce_code_verifier"
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            "auth_secure_prefs",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    val isLoggedIn: Flow<Boolean> = flow {
        emit(encryptedPrefs.getString(KEY_ACCESS_TOKEN, null) != null)
    }

    val accessToken: Flow<String?> = flow {
        emit(encryptedPrefs.getString(KEY_ACCESS_TOKEN, null))
    }

    val selectedHomeId: Flow<String?> = flow {
        emit(encryptedPrefs.getString(KEY_HOME_ID, null))
    }

    suspend fun getValidAccessToken(): String? {
        val token = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val expiresAt = encryptedPrefs.getLong(KEY_EXPIRES_AT, 0L)
        val refreshToken = encryptedPrefs.getString(KEY_REFRESH_TOKEN, null) ?: return null

        return if (System.currentTimeMillis() < expiresAt - 60_000) {
            token
        } else {
            refreshTokens(refreshToken)
        }
    }

    suspend fun loginWithCode(code: String, receivedState: String? = null): Result<Unit> {
        // F-3: Validate OAuth state parameter to prevent CSRF
        val storedState = encryptedPrefs.getString(KEY_OAUTH_STATE, null)
        encryptedPrefs.edit().remove(KEY_OAUTH_STATE).apply()
        if (storedState != null && receivedState != storedState) {
            if (BuildConfig.DEBUG) {
                AuthDebugLogger.log("loginWithCode() → ❌ State mismatch — possible CSRF. received=$receivedState stored=$storedState")
            }
            return Result.failure(Exception("OAuth state mismatch — request rejected"))
        }

        // F-4: Read and clear PKCE code_verifier
        val codeVerifier = encryptedPrefs.getString(KEY_PKCE_VERIFIER, null)
        encryptedPrefs.edit().remove(KEY_PKCE_VERIFIER).apply()

        if (BuildConfig.DEBUG) {
            AuthDebugLogger.log("loginWithCode() → code=${code.take(8)}...", context)
            AuthDebugLogger.log("  clientId=${BuildConfig.NETATMO_CLIENT_ID}", context)
            AuthDebugLogger.log("  redirectUri=${BuildConfig.NETATMO_REDIRECT_URI}", context)
        }
        return try {
            val response = authApiService.getToken(
                grantType = "authorization_code",
                clientId = BuildConfig.NETATMO_CLIENT_ID,
                clientSecret = BuildConfig.NETATMO_CLIENT_SECRET,
                code = code,
                redirectUri = BuildConfig.NETATMO_REDIRECT_URI,
                codeVerifier = codeVerifier
            )
            if (BuildConfig.DEBUG) {
                AuthDebugLogger.log("  HTTP ${response.code()} ${response.message()}", context)
            }
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    if (BuildConfig.DEBUG) {
                        AuthDebugLogger.log("  ✅ Token OK · scope=${body.scope.joinToString(" ")} · expiresIn=${body.expiresIn}s", context)
                    }
                    saveTokens(body.accessToken, body.refreshToken, body.expiresIn)
                    Result.success(Unit)
                } else {
                    if (BuildConfig.DEBUG) {
                        AuthDebugLogger.log("  ❌ Body nulo a pesar de 200", context)
                    }
                    Result.failure(Exception("Respuesta vacía del servidor"))
                }
            } else {
                val errBody = response.errorBody()?.string() ?: "(sin cuerpo)"
                if (BuildConfig.DEBUG) {
                    AuthDebugLogger.log("  ❌ Error ${response.code()}: $errBody", context)
                }
                Result.failure(Exception("Auth error ${response.code()}: $errBody"))
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                AuthDebugLogger.log("  ❌ Excepción: ${e.javaClass.simpleName}: ${e.message}", context)
            }
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

    private fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Int) {
        encryptedPrefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + (expiresIn * 1000L))
            .apply()
    }

    fun setSelectedHome(homeId: String) {
        encryptedPrefs.edit().putString(KEY_HOME_ID, homeId).apply()
    }

    fun logout() {
        encryptedPrefs.edit().clear().apply()
    }

    fun getAuthUrl(): String {
        // F-3: Generate and store OAuth state parameter
        val stateBytes = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val state = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes)
        encryptedPrefs.edit().putString(KEY_OAUTH_STATE, state).apply()

        // F-4: Generate PKCE code_verifier and derive code_challenge (S256)
        val codeVerifierBytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val codeVerifier = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifierBytes)
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        val codeChallenge = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        encryptedPrefs.edit().putString(KEY_PKCE_VERIFIER, codeVerifier).apply()

        val redirectUri = Uri.encode(BuildConfig.NETATMO_REDIRECT_URI)
        val url = "https://api.netatmo.com/oauth2/authorize" +
                "?client_id=${BuildConfig.NETATMO_CLIENT_ID}" +
                "&redirect_uri=$redirectUri" +
                "&scope=read_thermostat%20write_thermostat" +
                "&response_type=code" +
                "&state=$state" +
                "&code_challenge=$codeChallenge" +
                "&code_challenge_method=S256"
        if (BuildConfig.DEBUG) {
            AuthDebugLogger.log("getAuthUrl() → $url", context)
        }
        return url
    }
}
