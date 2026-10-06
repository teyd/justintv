plugins {
    id("justintv.android.library")
    id("justintv.android.compose")
    id("justintv.android.hilt")
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:adfree"))
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
