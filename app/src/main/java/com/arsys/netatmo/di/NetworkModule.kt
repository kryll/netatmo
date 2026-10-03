package com.arsys.netatmo.di

import com.arsys.netatmo.BuildConfig
import com.arsys.netatmo.data.api.AuthApiService
import com.arsys.netatmo.data.api.GitHubApiService
import com.arsys.netatmo.data.api.NetatmoApiService
import com.arsys.netatmo.data.repository.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
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
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @Named("api")
    fun provideApiOkHttpClient(
        logging: HttpLoggingInterceptor,
        authRepository: AuthRepository
    ): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val token = runBlocking { authRepository.getValidAccessToken() }
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
        Retrofit.Builder()
            .baseUrl(BuildConfig.NETATMO_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApiService::class.java)

    @Provides
    @Singleton
    fun provideNetatmoApiService(@Named("api") client: OkHttpClient): NetatmoApiService =
        Retrofit.Builder()
            .baseUrl(BuildConfig.NETATMO_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NetatmoApiService::class.java)

    @Provides
    @Singleton
    @Named("github")
    fun provideGitHubOkHttpClient(logging: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideGitHubApiService(@Named("github") client: OkHttpClient): GitHubApiService =
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApiService::class.java)
}
