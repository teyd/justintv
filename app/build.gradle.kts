plugins {
    id("justintv.android.application")
    id("justintv.android.compose")
    id("justintv.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.teyd.justintv"

    defaultConfig {
        applicationId = "dev.teyd.justintv"
        versionCode = 1
        versionName = "0.1.0"

        // Prefer the process env (varlock). Fall back to .env.local so installDebug without
        // varlock still bakes the same client id. A blank id makes Helix 401 and used to wipe login.
        val twitchClientId = twitchClientId()
        buildConfigField("String", "TWITCH_CLIENT_ID", twitchClientId.asJavaStringLiteral())
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            // R8 is enabled in M7, together with the release signing setup.
            isMinifyEnabled = false
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
