import com.diffplug.spotless.LineEnding

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
    id("com.diffplug.spotless") version "8.10.0"
}

spotless {
    lineEndings = LineEnding.UNIX

    val ktlintConfig =
        mapOf(
            "ij_kotlin_packages_to_use_import_on_demand" to "**",
        )

    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/generated/**")
        ktlint("1.8.0").editorConfigOverride(ktlintConfig)
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint("1.8.0").editorConfigOverride(ktlintConfig)
    }
}

tasks.register("ktlintCheck") {
    group = "verification"
    description = "Checks Kotlin formatting with ktlint."
    dependsOn("spotlessCheck")
}

tasks.register("ktlintFormat") {
    group = "formatting"
    description = "Formats Kotlin files with ktlint."
    dependsOn("spotlessApply")
}
