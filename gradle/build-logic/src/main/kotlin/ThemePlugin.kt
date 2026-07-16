import com.android.build.api.dsl.LibraryExtension
import io.github.keiyoushi.gradle.api.dsl.KeiyoushiThemeExtension
import io.github.keiyoushi.gradle.internal.VALID_LIB_VERSIONS
import io.github.keiyoushi.gradle.internal.extensions.alias
import io.github.keiyoushi.gradle.internal.extensions.compileOnly
import io.github.keiyoushi.gradle.internal.extensions.implementation
import io.github.keiyoushi.gradle.internal.extensions.kei
import io.github.keiyoushi.gradle.internal.extensions.libs
import io.github.keiyoushi.gradle.internal.extensions.plugins
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies

@Suppress("UNUSED")
class ThemePlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        plugins {
            alias(libs.plugins.android.library)
            alias(libs.plugins.kotlin.serialization)

            alias(kei.plugins.android.base)
            alias(kei.plugins.spotless)
        }

        val keiyoushi = extensions.create<KeiyoushiThemeExtension>("keiyoushi")

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
            compileOnly(keiyoushi.libVersion.flatMap { if (it == "1.6") libs.tachiyomi.lib.v16 else libs.tachiyomi.lib.v14 })
            implementation(project(":core"))
        }

        afterEvaluate {
            val libVersionValue = keiyoushi.libVersion.get()
            check(libVersionValue in VALID_LIB_VERSIONS) {
                "libVersion $libVersionValue is not supported. Supported versions: $VALID_LIB_VERSIONS"
            }
        }
    }
}

private fun Project.android(block: LibraryExtension.() -> Unit) {
    extensions.configure(block)
}
