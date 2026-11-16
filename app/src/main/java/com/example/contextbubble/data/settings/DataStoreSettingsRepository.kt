package com.example.contextbubble.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.contextbubble.domain.model.BubbleEdge
import com.example.contextbubble.domain.model.BubblePosition
import com.example.contextbubble.domain.model.UserSettings
import com.example.contextbubble.domain.repository.SettingsRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * DataStore for NON-secret preferences only (ARCHITECTURE.md §4). API keys never go here; screen
 * text is never written here. Only the consent version + timestamp are stored for consent.
 */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<UserSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::toSettings)

    override suspend fun acceptConsent(version: Int, acceptedAtMs: Long) {
        dataStore.edit {
            it[Keys.CONSENT_VERSION] = version
            it[Keys.CONSENT_AT] = acceptedAtMs
        }
    }

    override suspend fun revokeConsent() {
        dataStore.edit {
            it.remove(Keys.CONSENT_VERSION)
            it.remove(Keys.CONSENT_AT)
        }
    }

    override suspend fun setBubbleEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BUBBLE_ENABLED] = enabled }
    }

    override suspend fun setBubblePosition(position: BubblePosition) {
        dataStore.edit {
            it[Keys.BUBBLE_EDGE] = position.edge.name
            it[Keys.BUBBLE_Y_FRACTION] = position.yFraction.coerceIn(0f, 1f)
        }
    }

    private fun toSettings(prefs: Preferences): UserSettings {
        val defaults = UserSettings()
        val edge = prefs[Keys.BUBBLE_EDGE]
            ?.let { name -> BubbleEdge.entries.firstOrNull { it.name == name } }
            ?: defaults.bubblePosition.edge
        val fraction = prefs[Keys.BUBBLE_Y_FRACTION]?.coerceIn(0f, 1f) ?: defaults.bubblePosition.yFraction
        return UserSettings(
            consentVersion = prefs[Keys.CONSENT_VERSION],
            consentAcceptedAtMs = prefs[Keys.CONSENT_AT],
            bubbleEnabled = prefs[Keys.BUBBLE_ENABLED] ?: defaults.bubbleEnabled,
            bubblePosition = BubblePosition(edge, fraction),
        )
    }

    private object Keys {
        val CONSENT_VERSION = intPreferencesKey("consent_version")
        val CONSENT_AT = longPreferencesKey("consent_accepted_at_ms")
        val BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
        val BUBBLE_EDGE = stringPreferencesKey("bubble_edge")
        val BUBBLE_Y_FRACTION = floatPreferencesKey("bubble_y_fraction")
    }
}
