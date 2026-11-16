package com.example.contextbubble.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.example.contextbubble.data.db.AppProfileDao
import com.example.contextbubble.data.db.ContextBubbleDatabase
import com.example.contextbubble.data.db.RoomAppProfileRepository
import com.example.contextbubble.data.settings.DataStoreSettingsRepository
import com.example.contextbubble.domain.Clock
import com.example.contextbubble.domain.repository.AppProfileRepository
import com.example.contextbubble.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OwnPackageName

@Module
@InstallIn(SingletonComponent::class)
object AppProvidesModule {
    private const val SETTINGS_FILE = "contextbubble_settings"

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(SETTINGS_FILE) }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ContextBubbleDatabase =
        Room.databaseBuilder(context, ContextBubbleDatabase::class.java, ContextBubbleDatabase.NAME).build()

    @Provides
    fun provideAppProfileDao(db: ContextBubbleDatabase): AppProfileDao = db.appProfileDao()

    @Provides
    @OwnPackageName
    fun provideOwnPackageName(@ApplicationContext context: Context): String = context.packageName

    @Provides
    fun provideClock(): Clock = Clock { System.currentTimeMillis() }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindsModule {
    @Binds
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindAppProfileRepository(impl: RoomAppProfileRepository): AppProfileRepository
}
