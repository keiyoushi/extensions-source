import com.android.build.api.dsl.LibraryExtension
import keiyoushi.gradle.api.alias
import keiyoushi.gradle.api.compileOnly
import keiyoushi.gradle.api.implementation
import keiyoushi.gradle.api.kei
import keiyoushi.gradle.api.libs
import keiyoushi.gradle.api.plugins
import keiyoushi.gradle.extension.dsl.MultisrcSpec
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

@Suppress("UNUSED")
class PluginMultiSrc : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        plugins {
            alias(libs.plugins.android.library)
            alias(libs.plugins.kotlin.serialization)

            alias(kei.plugins.android.base)
            alias(kei.plugins.spotless)
        }

        android {
            namespace = "eu.kanade.tachiyomi.multisrc.${project.name}"

            sourceSets {
                named("main") {
                    manifest.srcFile("AndroidManifest.xml")
                    kotlin.directories.clear()
                    kotlin.directories.add("src")
                    res.directories.clear()
                    res.directories.add("res")
                    assets.directories.clear()
                    assets.directories.add("assets")
                }
            }
        }

        dependencies {
            compileOnly(libs.bundles.common)
            implementation(project(":core"))
        }

        extensions.create("multisrc", MultisrcSpec::class.java)
    }
}

private fun Project.android(block: LibraryExtension.() -> Unit) {
    extensions.configure(block)
}
