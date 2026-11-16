package com.example.bubbleb.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.bubbleb.data.settings.DataStoreSettingsRepository
import com.example.bubbleb.domain.ConsentPolicy
import com.example.bubbleb.domain.model.BubbleEdge
import com.example.bubbleb.domain.model.BubblePosition
import com.example.bubbleb.domain.model.UserSettings
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun repo(scope: kotlinx.coroutines.CoroutineScope, file: File) = DataStoreSettingsRepository(
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
    )

    @Test
    fun defaults_noConsent_bubbleEnabled_defaultPosition() = runTest {
        val s = repo(backgroundScope, File(tmp.root, "a.preferences_pb")).settings.first()
        assertFalse(s.hasCurrentConsent)
        assertTrue(s.bubbleEnabled)
        assertEquals(UserSettings.DEFAULT_POSITION, s.bubblePosition)
    }

    @Test
    fun consent_roundTrip_andRevoke() = runTest {
        val r = repo(backgroundScope, File(tmp.root, "b.preferences_pb"))
        r.acceptConsent(ConsentPolicy.CURRENT_VERSION, 1234L)
        assertTrue(r.settings.first().hasCurrentConsent)
        assertEquals(1234L, r.settings.first().consentAcceptedAtMs)
        r.revokeConsent()
        val s = r.settings.first()
        assertFalse(s.hasCurrentConsent)
        assertNull(s.consentVersion)
    }

    @Test
    fun outdatedConsentVersion_isNotCurrent() = runTest {
        val r = repo(backgroundScope, File(tmp.root, "c.preferences_pb"))
        r.acceptConsent(ConsentPolicy.CURRENT_VERSION - 1, 1L)
        assertFalse(r.settings.first().hasCurrentConsent)
    }

    @Test
    fun position_persistsAndClamps() = runTest {
        val r = repo(backgroundScope, File(tmp.root, "d.preferences_pb"))
        r.setBubblePosition(BubblePosition(BubbleEdge.LEFT, 3f))
        assertEquals(BubblePosition(BubbleEdge.LEFT, 1f), r.settings.first().bubblePosition)
    }

    @Test
    fun storedFile_containsNoUnexpectedKeys() = runTest {
        val file = File(tmp.root, "e.preferences_pb")
        val r = repo(backgroundScope, file)
        r.acceptConsent(ConsentPolicy.CURRENT_VERSION, 99L)
        r.setBubbleEnabled(false)
        r.setBubblePosition(BubblePosition(BubbleEdge.RIGHT, 0.2f))
        r.settings.first()
        val text = String(file.readBytes(), Charsets.ISO_8859_1)
        listOf("consent_version", "consent_accepted_at_ms", "bubble_enabled", "bubble_edge", "bubble_y_fraction")
            .forEach { assertTrue("missing $it", text.contains(it)) }
        assertFalse(text.contains("key", ignoreCase = true) && text.contains("api", ignoreCase = true))
    }
}
