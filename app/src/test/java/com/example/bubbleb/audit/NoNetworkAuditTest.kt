package com.example.bubbleb.audit

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Network APIs remain confined to data/llm and its DI wiring; behavior gates use a fake gateway. */
class NoNetworkAuditTest {
    private val forbidden = listOf(
        "java.net.", "HttpURLConnection", "okhttp3", "retrofit2", "io.ktor", "Socket(", "URL(",
        "WebView", "DownloadManager",
    )

    @Test fun networkingIsConfinedToDataBoundary() {
        val root = File("src/main/java")
        assertTrue("source root not found: ${root.absolutePath}", root.isDirectory)
        val offenders = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
            val path = f.relativeTo(root).invariantSeparatorsPath
            if (path.contains("/data/llm/") || path.endsWith("/di/LlmModule.kt")) return@flatMap emptyList<String>()
            val text = f.readText()
            forbidden.filter { text.contains(it) }.map { "${f.name}: $it" }
        }.toList()
        assertTrue("networking API in main sources: $offenders", offenders.isEmpty())
    }

    @Test fun noScreenTextLoggedInCaptureCode() {
        val dirs = listOf("domain/capture", "domain/privacy", "domain/usecase", "domain/reply", "data/reply", "data/llm", "data/secrets", "presentation")
        val base = File("src/main/java/com/example/bubbleb")
        val offenders = dirs.flatMap { d -> File(base, d).walkTopDown().filter { it.isFile }.toList() }
            .filter { it.readText().contains("Log.") || it.readText().contains("println(") }
            .map { it.name }
        assertTrue("logging in capture/privacy code: $offenders", offenders.isEmpty())
    }
}
