package com.arsys.netatmo.di

import android.content.Context
import com.arsys.netatmo.service.GeofenceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGeofenceManager(
        @ApplicationContext context: Context
    ): GeofenceManager = GeofenceManager(context)
}
