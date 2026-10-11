package com.arsys.netatmo.di

import com.arsys.netatmo.BuildConfig
import com.arsys.netatmo.data.api.AuthApiService
import com.arsys.netatmo.data.api.GitHubApiService
import com.arsys.netatmo.data.api.MeteosourceApiService
import com.arsys.netatmo.data.api.NetatmoApiService
import com.arsys.netatmo.data.repository.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private fun buildOkHttp(
        logging: HttpLoggingInterceptor,
        readTimeoutSec: Long = 30
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
        .build()

    private val NETATMO_TOKEN_ERROR = Regex("\"code\"\\s*:\\s*(2|3)\\b")

    // Netatmo reports an invalid (2) or expired (3) access token as HTTP 403 with an error code,
    // not 401; other 403s (e.g. missing scope) must not trigger a token refresh.
    private fun isTokenRejected(response: Response): Boolean = when (response.code) {
        401 -> true
        403 -> NETATMO_TOKEN_ERROR.containsMatchIn(
            runCatching { response.peekBody(4096).string() }.getOrDefault("")
        )
        else -> false
    }

    private fun buildRetrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }

    @Provides
    @Singleton
    @Named("auth")
    fun provideAuthOkHttpClient(logging: HttpLoggingInterceptor): OkHttpClient =
        buildOkHttp(logging)

    @Provides
    @Singleton
    @Named("api")
    fun provideApiOkHttpClient(
        logging: HttpLoggingInterceptor,
        authRepository: AuthRepository
    ): OkHttpClient {
        // Blocking here is fine: interceptors run on OkHttp worker threads, and the refresh
        // goes through the separate "auth" client, so it can't deadlock on this dispatcher.
        val authInterceptor = Interceptor { chain ->
            fun Request.withBearer(token: String?): Request =
                if (token == null) this else newBuilder().header("Authorization", "Bearer $token").build()

            val token = runBlocking { authRepository.getValidAccessToken() }
                ?: authRepository.cachedAccessToken
            val response = chain.proceed(chain.request().withBearer(token))
            if (token != null && isTokenRejected(response)) {
                val newToken = runBlocking { authRepository.refreshAfterRejection(token) }
                if (newToken != null && newToken != token) {
                    response.close()
                    return@Interceptor chain.proceed(chain.request().withBearer(newToken))
                }
            }
            response
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApiService(@Named("auth") client: OkHttpClient): AuthApiService =
        buildRetrofit(BuildConfig.NETATMO_BASE_URL, client).create(AuthApiService::class.java)

    @Provides
    @Singleton
    fun provideNetatmoApiService(@Named("api") client: OkHttpClient): NetatmoApiService =
        buildRetrofit(BuildConfig.NETATMO_BASE_URL, client).create(NetatmoApiService::class.java)

    @Provides
    @Singleton
    @Named("github")
    fun provideGitHubOkHttpClient(logging: HttpLoggingInterceptor): OkHttpClient =
        buildOkHttp(logging, readTimeoutSec = 60)

    @Provides
    @Singleton
    fun provideGitHubApiService(@Named("github") client: OkHttpClient): GitHubApiService =
        buildRetrofit("https://api.github.com/", client).create(GitHubApiService::class.java)

    @Provides
    @Singleton
    @Named("meteosource")
    fun provideMeteosourceOkHttpClient(logging: HttpLoggingInterceptor): OkHttpClient =
        buildOkHttp(logging)

    @Provides
    @Singleton
    fun provideMeteosourceApiService(@Named("meteosource") client: OkHttpClient): MeteosourceApiService =
        buildRetrofit("https://www.meteosource.com/", client).create(MeteosourceApiService::class.java)
}
