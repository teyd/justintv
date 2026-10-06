plugins {
    id("justintv.android.library")
    id("justintv.android.hilt")
    id("justintv.android.compose")
}

dependencies {
    api(project(":core:adfree"))

    api(libs.media3.exoplayer)
    api(libs.media3.exoplayer.hls)
    api(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.coil.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
}
