import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                // Modules live at :core:network, :feature:watch, ... and keep the matching package,
                // so the namespace never needs restating in the module build file.
                namespace = "dev.teyd.justintv" + path.replace(':', '.')
                compileSdk = catalogVersion("compileSdk").toInt()

                defaultConfig {
                    minSdk = catalogVersion("minSdk").toInt()
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }

            configureKotlinAndroid()

            dependencies {
                add("testImplementation", libs.findLibrary("junit").get())
                add("testImplementation", libs.findLibrary("truth").get())
                add("testImplementation", libs.findLibrary("kotlinx.coroutines.test").get())
            }
        }
}
