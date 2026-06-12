import com.android.build.api.dsl.ApplicationExtension
import com.android.build.gradle.tasks.PackageAndroidArtifact
import keiyoushi.gradle.extension.VariantBridges
import keiyoushi.gradle.extension.codegen.ResolvedExtension
import keiyoushi.gradle.extension.dsl.ExtensionSpec
import keiyoushi.gradle.extension.registerGenerateSourceTask
import keiyoushi.gradle.extension.registerManifestTask
import keiyoushi.gradle.extension.resolveExtensionSpec
import keiyoushi.gradle.extension.wireVariantApi
import keiyoushi.gradle.extensions.alias
import keiyoushi.gradle.extensions.compileOnly
import keiyoushi.gradle.extensions.implementation
import keiyoushi.gradle.extensions.kei
import keiyoushi.gradle.extensions.libs
import keiyoushi.gradle.extensions.plugins
import keiyoushi.gradle.utils.assertWithoutFlag
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

@Suppress("UNUSED")
class PluginExtension : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        plugins {
            alias(libs.plugins.android.application)
            alias(libs.plugins.kotlin.serialization)
            alias(kei.plugins.android.base)
            alias(kei.plugins.spotless)
        }

        val spec = extensions.create("keiyoushi", ExtensionSpec::class.java)
        val pkg = derivePackage()
        val applicationIdSuffix = "${project.parent?.name}.${project.name}"

        val bridges = VariantBridges(
            versionCode = objects.property(Int::class.javaObjectType),
            versionName = objects.property(String::class.java),
            appName = objects.property(String::class.java),
            nsfw = objects.property(String::class.java),
        )

        configureAndroidExtension(applicationIdSuffix)
        base {
            archivesName.set(bridges.versionName.map { vn -> "tachiyomi-$applicationIdSuffix-v$vn" })
        }

        val resolvedExtension = objects.property(ResolvedExtension::class.java)
        val manifestTask = registerManifestTask()
        val sourceTask = registerGenerateSourceTask(resolvedExtension)
        wireVariantApi(spec.className, bridges, manifestTask, sourceTask)

        afterEvaluate {
            val resolved = resolveExtensionSpec(spec, pkg)
            resolvedExtension.set(resolved.extension)

            bridges.versionCode.set(resolved.effectiveVersionCode)
            bridges.versionName.set(resolved.effectiveVersionName)
            bridges.appName.set("Tachiyomi: ${resolved.extension.name}")
            bridges.nsfw.set(if (resolved.extension.isNsfw) "1" else "0")

            manifestTask.configure {
                filters.set(resolved.deeplinkFilters)
            }

            dependencies {
                spec.theme.orNull?.let { implementation(project(":lib-multisrc:$it")) }
                implementation(project(":core"))
                compileOnly(libs.bundles.common)
            }

            tasks.withType<PackageAndroidArtifact>().configureEach {
                createdBy.set("")
                doFirst { appMetadata.asFile.orNull?.writeText("") }
            }
        }
    }

    // Package is derived from the project path as eu.kanade.tachiyomi.extension.<lang>.<ext>.
    private fun Project.derivePackage(): String {
        val segments = path.removePrefix(":").split(":")
        assertWithoutFlag(segments.size == 3 && segments[0] == "src") {
            "kei.plugins.extension can only be applied to :src:<lang>:<ext> projects (got $path)"
        }
        return "eu.kanade.tachiyomi.extension.${segments[1]}.${segments[2]}"
    }

    private fun Project.configureAndroidExtension(applicationIdSuffix: String) {
        extensions.configure(ApplicationExtension::class.java) {
            namespace = "eu.kanade.tachiyomi.extension"

            sourceSets {
                named("main") {
                    manifest.srcFile(rootProject.file("common/AndroidManifest.xml"))
                    java.directories.clear()
                    java.directories.add("src")
                    kotlin.directories.clear()
                    kotlin.directories.add("src")
                    res.directories.clear()
                    res.directories.add("res")
                    assets.directories.clear()
                    assets.directories.add("assets")
                }
            }

            defaultConfig {
                this.applicationIdSuffix = applicationIdSuffix
            }

            lint {
                checkReleaseBuilds = false
            }

            signingConfigs {
                create("release") {
                    storeFile = rootProject.file("signingkey.jks")
                    storePassword = providers.environmentVariable("KEY_STORE_PASSWORD").orNull
                    keyAlias = providers.environmentVariable("ALIAS").orNull
                    keyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
                }
            }

            buildTypes {
                named("release") {
                    signingConfig = if (rootProject.file("signingkey.jks").exists()) {
                        signingConfigs.getByName("release")
                    } else {
                        signingConfigs.getByName("debug")
                    }
                    isMinifyEnabled = true
                    proguardFiles(rootProject.file("common/proguard-rules.pro"))
                    @Suppress("UnstableApiUsage")
                    vcsInfo.include = false
                }
            }

            dependenciesInfo {
                includeInApk = false
            }

            buildFeatures {
                buildConfig = true
            }

            packaging {
                resources.excludes.add("kotlin-tooling-metadata.json")
            }
        }
    }

    private fun Project.base(block: BasePluginExtension.() -> Unit) {
        extensions.configure(block)
    }
}
