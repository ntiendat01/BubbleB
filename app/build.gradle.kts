import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Debug-only provider defaults from the repo-root `.env` (ADR-029). Missing file => all empty.
 * Values are never printed. Release builds always get empty strings.
 */
val envFile = rootProject.layout.projectDirectory.file(".env")
val dotEnv: Map<String, String> = providers.fileContents(envFile).asText.orNull.orEmpty()
    .lineSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
    .associate { line ->
        val key = line.substringBefore('=').trim().removePrefix("export ").trim()
        val value = line.substringAfter('=').trim().removeSurrounding("\"").removeSurrounding("'")
        key to value
    }

fun envValue(key: String): String = dotEnv[key].orEmpty()

fun httpsUrl(key: String): String = envValue(key).also {
    if (it.isNotEmpty() && !it.lowercase(Locale.ROOT).startsWith("https://")) {
        throw GradleException(".env: $key must be an https:// URL")
    }
}

fun javaString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

fun modelSelector(): String =
    envValue("DEFAULT_MODEL").lowercase(Locale.ROOT).takeIf { it in setOf("deepseek", "qwen") }.orEmpty()

val providerDefaultFields = listOf(
    "LLM_BASE_URL" to { httpsUrl("LLM_BASE_URL") },
    "LLM_API_KEY" to { envValue("LLM_API_KEY") },
    "DEEPSEEK_MODEL" to { envValue("DEEPSEEK_MODEL") },
    "QWEN_MODEL" to { envValue("QWEN_MODEL") },
    "DEFAULT_MODEL" to { modelSelector() },
    // Optional direct-provider overrides (commented out in .env.example).
    "DEEPSEEK_API_KEY" to { envValue("DEEPSEEK_API_KEY") },
    "DEEPSEEK_BASE_URL" to { httpsUrl("DEEPSEEK_BASE_URL") },
    "QWEN_API_KEY" to { envValue("QWEN_API_KEY") },
    "QWEN_BASE_URL" to { httpsUrl("QWEN_BASE_URL") },
)

android {
    namespace = "com.example.bubbleb"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.bubbleb"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.1-m2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            providerDefaultFields.forEach { (name, value) -> buildConfigField("String", name, javaString(value())) }
        }
        release {
            // Never ship .env values: release gets empty strings (in-app Keystore entry from M3).
            providerDefaultFields.forEach { (name, _) -> buildConfigField("String", name, "\"\"") }
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = false
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    // Room schema export is intentionally off for the M0 skeleton (no migrations yet); see docs/DECISIONS.md.
    arg("room.generateKotlin", "true")
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(composeBom)
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
