plugins {
    id("justintv.android.library")
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:network"))
    api(libs.androidx.datastore.preferences)
    api(libs.kotlinx.coroutines.android)
}
