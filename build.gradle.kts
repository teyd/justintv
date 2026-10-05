// AGP 9 has built-in Kotlin support, so the `org.jetbrains.kotlin.android` plugin is not applied.
// The Kotlin Gradle plugin on this classpath pins the Kotlin version used by AGP.
// Keep it in sync with `kotlin` in gradle/libs.versions.toml.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
