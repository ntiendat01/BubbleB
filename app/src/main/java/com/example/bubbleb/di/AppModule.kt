package com.example.bubbleb.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.example.bubbleb.data.db.AppProfileDao
import com.example.bubbleb.data.db.BubbleBDatabase
import com.example.bubbleb.data.db.RoomAppProfileRepository
import com.example.bubbleb.data.settings.DataStoreSettingsRepository
import com.example.bubbleb.domain.Clock
import com.example.bubbleb.domain.repository.AccessibilityStatusSource
import com.example.bubbleb.domain.repository.AppProfileRepository
import com.example.bubbleb.domain.repository.SettingsRepository
import com.example.bubbleb.service.SettingsSecureAccessibilityStatus
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
    private const val SETTINGS_FILE = "bubbleb_settings"

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(SETTINGS_FILE) }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BubbleBDatabase =
        Room.databaseBuilder(context, BubbleBDatabase::class.java, BubbleBDatabase.NAME).build()

    @Provides
    fun provideAppProfileDao(db: BubbleBDatabase): AppProfileDao = db.appProfileDao()

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

    @Binds
    abstract fun bindAccessibilityStatusSource(impl: SettingsSecureAccessibilityStatus): AccessibilityStatusSource
}
