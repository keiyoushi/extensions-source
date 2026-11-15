import com.android.build.api.dsl.LibraryExtension
import io.github.keiyoushi.gradle.internal.configureRuntimeConsumer
import io.github.keiyoushi.gradle.internal.extensions.alias
import io.github.keiyoushi.gradle.internal.extensions.compileOnly
import io.github.keiyoushi.gradle.internal.extensions.implementation
import io.github.keiyoushi.gradle.internal.extensions.kei
import io.github.keiyoushi.gradle.internal.extensions.libs
import io.github.keiyoushi.gradle.internal.extensions.lintChecks
import io.github.keiyoushi.gradle.internal.extensions.plugins
import io.github.keiyoushi.gradle.internal.extensions.testImplementation
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

@Suppress("UNUSED")
class LibraryPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        plugins {
            alias(libs.plugins.android.library)
            alias(libs.plugins.kotlin.serialization)

            alias(kei.plugins.android.base)
            alias(kei.plugins.spotless)
        }

        val namespace = "keiyoushi.lib.${project.name}"
        configureRuntimeConsumer(provider { namespace })

        android {
            this.namespace = namespace

            sourceSets {
                named("main") {
                    java.directories.clear()
                    java.directories.add("src")
                    kotlin.directories.clear()
                    kotlin.directories.add("src")
                    assets.directories.clear()
                    assets.directories.add("assets")
                }
                named("test") {
                    kotlin.directories.clear()
                    kotlin.directories.add("test")
                    resources.directories.clear()
                    resources.directories.add("test-resources")
                }
            }

            androidResources.enable = false

            testOptions {
                unitTests.isReturnDefaultValues = true
            }
        }

        dependencies {
            compileOnly(libs.bundles.common)
            compileOnly(libs.tachiyomi.lib.v16)
            implementation(project(":core"))
            lintChecks(project(":lint-rules"))

            testImplementation(libs.bundles.common)
            testImplementation(libs.tachiyomi.lib.v16)
            testImplementation(libs.junit)
        }
    }
}

private fun Project.android(block: LibraryExtension.() -> Unit) {
    extensions.configure(block)
}
