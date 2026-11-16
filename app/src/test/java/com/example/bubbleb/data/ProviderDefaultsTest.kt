package com.example.bubbleb.data

import com.example.bubbleb.BuildConfig
import com.example.bubbleb.data.config.LlmEndpoint
import com.example.bubbleb.data.config.ProviderDefaults
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDefaultsTest {
    private val empty = LlmEndpoint("", "")

    @Test fun buildConfigDefaults_areWellFormed() {
        val d = ProviderDefaults.fromBuildConfig()
        assertTrue(d.defaultModelFamily in setOf("", ProviderDefaults.DEEPSEEK, ProviderDefaults.QWEN))
        listOf(d.gateway, d.directDeepseek, d.directQwen).forEach { e ->
            assertTrue(e.baseUrl.isEmpty() || e.baseUrl.startsWith("https://"))
            if (!BuildConfig.DEBUG) assertTrue(e.apiKey.isEmpty() && e.baseUrl.isEmpty())
        }
    }

    @Test fun toString_neverRevealsKey() {
        val fake = "unit" + "-test-" + "value-123456789"
        val text = LlmEndpoint("https://api.inference.wandb.ai/v1", fake).toString()
        assertFalse(text.contains(fake))
        assertTrue(text.contains("<redacted>"))
    }

    @Test fun isConfigured_requiresKeyAndHttps() {
        assertFalse(LlmEndpoint("https://x", "").isConfigured)
        assertFalse(LlmEndpoint("http" + "://x", "k").isConfigured)
        assertTrue(LlmEndpoint("https://x", "k").isConfigured)
    }

    @Test fun defaultModelId_followsFamily() {
        fun d(family: String) = ProviderDefaults(empty, "ds-model", "qw-model", family, empty, empty)
        assertEquals("qw-model", d("qwen").defaultModelId)
        assertEquals("ds-model", d("deepseek").defaultModelId)
        assertEquals("ds-model", d("").defaultModelId)
    }

    /** The tracked template must never carry a real key, and must use HTTPS. */
    @Test fun envExample_hasEmptyKeysAndHttpsGateway() {
        val active = File("../.env.example").readLines().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
        assertEquals("", active.getValue("LLM_API_KEY"))
        assertTrue(active.getValue("LLM_BASE_URL").startsWith("https://"))
        assertTrue(active.keys.none { it.endsWith("API_KEY") && active.getValue(it).isNotEmpty() })
        val commented = File("../.env.example").readLines().filter { it.trim().startsWith("#") && "API_KEY=" in it }
        assertTrue(commented.all { it.trim().endsWith("API_KEY=") })
    }
}
