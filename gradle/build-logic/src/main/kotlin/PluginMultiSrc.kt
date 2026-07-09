import com.android.build.api.dsl.LibraryExtension
import keiyoushi.gradle.extensions.KeiyoushiMultisrcExtension
import keiyoushi.gradle.extensions.VALID_LIB_VERSIONS
import keiyoushi.gradle.extensions.alias
import keiyoushi.gradle.extensions.compileOnly
import keiyoushi.gradle.extensions.implementation
import keiyoushi.gradle.extensions.kei
import keiyoushi.gradle.extensions.libs
import keiyoushi.gradle.extensions.plugins
import keiyoushi.gradle.utils.assertWithoutFlag
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

        val keiyoushi = extensions.create("keiyoushi", KeiyoushiMultisrcExtension::class.java)

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

        afterEvaluate {
            val libVersionValue = keiyoushi.libVersion.get()
            assertWithoutFlag(libVersionValue in VALID_LIB_VERSIONS) {
                "libVersion $libVersionValue is not supported. Supported versions: $VALID_LIB_VERSIONS"
            }
        }
    }
}

private fun Project.android(block: LibraryExtension.() -> Unit) {
    extensions.configure(block)
}
