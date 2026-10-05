plugins {
    id("justintv.android.application")
    id("justintv.android.compose")
    id("justintv.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

// User-facing version. versionCode is derived so an upgrade integer cannot drift from the name.
private val appVersionName =
    providers
        .fileContents(rootProject.layout.projectDirectory.file("version.txt"))
        .asText
        .get()
        .trim()

android {
    namespace = "dev.teyd.justintv"

    defaultConfig {
        applicationId = "dev.teyd.justintv"
        versionName = appVersionName
        versionCode = versionCodeOf(appVersionName)

        // Prefer the process env (varlock). Fall back to .env.local so installDebug without
        // varlock still bakes the same client id. A blank id makes Helix 401 and used to wipe login.
        val twitchClientId = twitchClientId()
        buildConfigField("String", "TWITCH_CLIENT_ID", twitchClientId.asJavaStringLiteral())
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        releaseSigning()?.let { signing ->
            create("release") {
                storeFile = signing.storeFile
                storePassword = signing.storePassword
                keyAlias = signing.keyAlias
                keyPassword = signing.keyPassword
            }
        }
    }

    buildTypes {
        release {
            // R8 stays off until a minified release is verified on a device.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:adfree"))
    implementation(project(":core:player"))
    implementation(project(":core:chat"))
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":feature:streams"))
    implementation(project(":feature:watch"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.window)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    testImplementation(libs.turbine)

    debugImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

/**
 * `major * 10000 + minor * 100 + patch`. `0.1.0` is `100`.
 * Each component stays in 0..99 so a later version always sorts above an earlier one.
 */
private fun versionCodeOf(versionName: String): Int {
    require(Regex("(0|[1-9][0-9]?)\\.(0|[1-9][0-9]?)\\.(0|[1-9][0-9]?)").matches(versionName)) {
        "versionName must be canonical major.minor.patch with components in 0..99, was $versionName"
    }
    val parts = versionName.split('.')
    require(parts.size == 3) { "versionName must be major.minor.patch, was $versionName" }
    val numbers =
        parts.map { part ->
            part.toIntOrNull() ?: error("versionName must be major.minor.patch, was $versionName")
        }
    val (major, minor, patch) = numbers
    require(major in 0..99 && minor in 0..99 && patch in 0..99) {
        "versionName components must be 0..99, was $versionName"
    }
    val code = major * 10_000 + minor * 100 + patch
    require(code > 0) { "versionCode must be positive; 0.0.0 is not a release version" }
    return code
}

/**
 * Release signing, or null when it is not configured. An unconfigured release build is unsigned.
 *
 * CI sets `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and
 * `ANDROID_KEY_PASSWORD`. A local build reads `keystore.properties` in the repo root instead:
 *
 * ```
 * storeFile=release.keystore
 * storePassword=...
 * keyAlias=justintv
 * keyPassword=...
 * ```
 *
 * `storeFile` is relative to the repo root. Environment variables win over the file.
 */
private data class ReleaseSigning(
    val storeFile: java.io.File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

private fun releaseSigning(): ReleaseSigning? {
    val fromFile = keystoreProperties()

    fun value(
        property: String,
        env: String,
    ): String? = providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() } ?: fromFile[property]

    val storeFilePath = value("storeFile", "ANDROID_KEYSTORE_FILE")
    val storePassword = value("storePassword", "ANDROID_KEYSTORE_PASSWORD")
    val keyAlias = value("keyAlias", "ANDROID_KEY_ALIAS")
    val keyPassword = value("keyPassword", "ANDROID_KEY_PASSWORD")
    val values = listOf(storeFilePath, storePassword, keyAlias, keyPassword)
    if (values.all { it == null }) return null

    val storeFile = storeFilePath?.let { rootProject.file(it) }
    if (values.any { it == null } || storeFile?.isFile != true) {
        logger.warn(
            "Release signing is incomplete. Set keystore.properties or the ANDROID_KEYSTORE_* " +
                "environment variables. The release APK will be unsigned.",
        )
        return null
    }
    return ReleaseSigning(
        storeFile = storeFile,
        storePassword = storePassword!!,
        keyAlias = keyAlias!!,
        keyPassword = keyPassword!!,
    )
}

private fun keystoreProperties(): Map<String, String> {
    val file = rootProject.layout.projectDirectory.file("keystore.properties")
    val text = providers.fileContents(file).asText.orNull ?: return emptyMap()
    return buildMap {
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || "=" !in trimmed) continue
            val key = trimmed.substringBefore("=").trim()
            val value = trimmed.substringAfter("=").trim().trim('"', '\'')
            if (key.isNotEmpty() && value.isNotEmpty()) put(key, value)
        }
    }
}

/** Process env first, then `.env.local` / `.env`, so a plain `installDebug` keeps the same client id. */
fun twitchClientId(): String {
    System.getenv("TWITCH_CLIENT_ID")?.takeIf { it.isNotBlank() }?.let { return it.trim() }
    val root = rootProject.projectDir
    for (name in listOf(".env.local", ".env")) {
        val file = root.resolve(name)
        if (!file.isFile) continue
        for (line in file.readLines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || "=" !in trimmed) continue
            val key = trimmed.substringBefore("=").trim()
            if (key == "TWITCH_CLIENT_ID") {
                return trimmed.substringAfter("=").trim().trim('"', '\'')
            }
        }
    }
    return ""
}

private fun String.asJavaStringLiteral(): String =
    buildString {
        append('"')
        for (ch in this@asJavaStringLiteral) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n', '\r' -> Unit
                else -> append(ch)
            }
        }
        append('"')
    }
