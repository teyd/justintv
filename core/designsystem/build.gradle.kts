plugins {
    id("justintv.android.library")
    id("justintv.android.compose")
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.material3)
    api(libs.androidx.material.icons.extended)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
