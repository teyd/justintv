plugins {
    id("justintv.android.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:network"))
}
