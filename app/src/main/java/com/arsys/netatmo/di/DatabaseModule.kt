package com.arsys.netatmo.di

import android.content.Context
import androidx.room.Room
import com.arsys.netatmo.data.local.AppDatabase
import com.arsys.netatmo.data.local.dao.*
import com.arsys.netatmo.data.local.dao.FamilyMemberDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "netatmo_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideAutomationDao(db: AppDatabase): AutomationDao = db.automationDao()
    @Provides fun provideGeofenceDao(db: AppDatabase): GeofenceDao = db.geofenceDao()
    @Provides fun provideScheduleDao(db: AppDatabase): ScheduleDao = db.scheduleDao()
    @Provides fun provideScenarioDao(db: AppDatabase): ScenarioDao = db.scenarioDao()
    @Provides fun provideTemperatureHistoryDao(db: AppDatabase): TemperatureHistoryDao = db.temperatureHistoryDao()
    @Provides fun provideCalendarAutomationDao(db: AppDatabase): CalendarAutomationDao = db.calendarAutomationDao()
    @Provides fun provideHomeCacheDao(db: AppDatabase): HomeCacheDao = db.homeCacheDao()
    @Provides fun provideAutomationLogDao(db: AppDatabase): AutomationLogDao = db.automationLogDao()
    @Provides fun provideFamilyMemberDao(db: AppDatabase): FamilyMemberDao = db.familyMemberDao()
}
