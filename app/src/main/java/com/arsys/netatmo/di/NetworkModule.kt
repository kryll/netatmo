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
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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
        val authInterceptor = Interceptor { chain ->
            // Read the non-suspending cache — no blocking on OkHttp's IO thread pool.
            // Token refresh (when near expiry) is driven proactively from ViewModel/coroutine scope.
            val token = authRepository.cachedAccessToken
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
            chain.proceed(request)
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
