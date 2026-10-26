package com.arsys.netatmo.di

import com.arsys.netatmo.service.MigrationFlags
import com.arsys.netatmo.service.MigrationStore
import com.arsys.netatmo.service.PrefsMigrationFlags
import com.arsys.netatmo.service.RoomMigrationStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class MigrationModule {
    @Binds
    abstract fun bindMigrationStore(impl: RoomMigrationStore): MigrationStore

    @Binds
    abstract fun bindMigrationFlags(impl: PrefsMigrationFlags): MigrationFlags
}
