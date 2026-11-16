package com.example.bubbleb.di

import android.content.Context
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.bubbleb.data.config.ProviderDefaults
import com.example.bubbleb.data.llm.OkHttpLlmGateway
import com.example.bubbleb.data.llm.PublicProviderDns
import com.example.bubbleb.data.secrets.EncryptedFileSecretStore
import com.example.bubbleb.data.secrets.KeystoreSecretCipher
import com.example.bubbleb.data.settings.StoredProviderRepository
import com.example.bubbleb.domain.Clock
import com.example.bubbleb.domain.llm.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ElapsedTime

@Module
@InstallIn(SingletonComponent::class)
object LlmModule {
    @Provides @Singleton
    fun secrets(@ApplicationContext context: Context): SecretStore = EncryptedFileSecretStore(
        File(context.noBackupFilesDir, "provider-key.enc"), KeystoreSecretCipher(),
    )

    @Provides @Singleton
    fun providers(store: DataStore<Preferences>, secrets: SecretStore): ProviderRepository =
        StoredProviderRepository(store, secrets, ProviderDefaults.fromBuildConfig())

    @Provides @Singleton
    fun client(): OkHttpClient = OkHttpClient.Builder()
        .dns(PublicProviderDns())
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(LlmLimits.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(LlmLimits.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton
    fun gateway(client: OkHttpClient): LlmGateway = OkHttpLlmGateway(client)

    @Provides @Singleton fun usage(): UsageCounter = UsageCounter()
    @Provides @ElapsedTime fun elapsedClock(): Clock = Clock { SystemClock.elapsedRealtime() }
}
