import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.gradle.tasks.PackageAndroidArtifact
import keiyoushi.gradle.extensions.alias
import keiyoushi.gradle.extensions.baseVersionCode
import keiyoushi.gradle.extensions.compileOnly
import keiyoushi.gradle.extensions.implementation
import keiyoushi.gradle.extensions.kei
import keiyoushi.gradle.extensions.libs
import keiyoushi.gradle.extensions.plugins
import keiyoushi.gradle.tasks.GenerateKeepRulesTask
import keiyoushi.gradle.utils.assertWithoutFlag
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

@Suppress("UNUSED")
class PluginExtensionLegacy : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        plugins {
            alias(libs.plugins.android.application)
            alias(libs.plugins.kotlin.serialization)

            alias(kei.plugins.android.base)
            alias(kei.plugins.spotless)
        }

        assertWithoutFlag(!extra.has("pkgNameSuffix")) { "Gradle configuration cannot contain 'pkgNameSuffix'" }
        assertWithoutFlag(!extra.has("libVersion")) { "Gradle configuration cannot contain 'libVersion'" }

        assertWithoutFlag(extName.max().code < 0x180) { "Extension name should be romanized" }

        val theme: Project? = if (extra.has("themePkg")) project(":lib-multisrc:$themePkg") else null
        if (theme != null) evaluationDependsOn(theme.path)

        android {
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
                applicationIdSuffix = project.parent?.name + "." + project.name
                versionCode = if (theme == null) extVersionCode else theme.baseVersionCode + overrideVersionCode
                versionName = "1.4.$versionCode"
                base {
                    archivesName.set("tachiyomi-$applicationIdSuffix-v$versionName")
                }
                assertWithoutFlag(extClass.startsWith(".")) { "'extClass' must start with '.'" }
                manifestPlaceholders += mapOf(
                    "appName" to "Tachiyomi: $extName",
                    "extClass" to extClass,
                    "nsfw" to if (isNsfw) 1 else 0,
                )
                if (theme != null && baseUrl.isNotEmpty()) {
                    val split = baseUrl.split("://")
                    assertWithoutFlag(split.size == 2) { "'baseUrl' must be in the format of 'https://example.com'" }
                    val path = split[1].split("/")
                    manifestPlaceholders += mapOf(
                        "SOURCEHOST" to path[0],
                        "SOURCESCHEME" to split[0],
                    )
                }
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

        androidComponents {
            onVariants { variant ->
                val variantName = variant.name.replaceFirstChar { it.uppercase() }

                @Suppress("UnstableApiUsage")
                val keepRules = variant.sources.keepRules
                if (keepRules != null) {
                    val task = tasks.register<GenerateKeepRulesTask>("generate${variantName}KeepRules") {
                        this.applicationId.set(variant.applicationId)
                        this.extClass.set(this@with.extClass)
                    }
                    keepRules.addGeneratedSourceDirectory(task) { it.outputDir }
                }

                variant.sources.manifests.addStaticManifestFile("AndroidManifest.xml")
            }
        }

        dependencies {
            if (theme != null) implementation(theme) // Overrides core launcher icons
            implementation(project(":core"))
            compileOnly(libs.bundles.common)
        }

        afterEvaluate {
            tasks.withType<PackageAndroidArtifact>().configureEach {
                createdBy.set("")
                doFirst {
                    appMetadata.asFile.orNull?.writeText("")
                }
            }
        }
    }
}

private fun Project.android(block: ApplicationExtension.() -> Unit) {
    extensions.configure(block)
}

private fun Project.androidComponents(block: ApplicationAndroidComponentsExtension.() -> Unit) {
    extensions.configure(block)
}

private fun Project.base(block: BasePluginExtension.() -> Unit) {
    extensions.configure(block)
}

private val Project.extName: String
    get() = extra.get("extName") as String

private val Project.extVersionCode: Int
    get() = extra.get("extVersionCode") as Int

private val Project.extClass: String
    get() = extra.get("extClass") as String

private val Project.isNsfw: Boolean
    get() = extra.getOrNull("isNsfw") == true

private val Project.baseUrl: String
    get() = (extra.getOrNull("baseUrl") as String?).orEmpty()

private val Project.overrideVersionCode: Int
    get() = extra.get("overrideVersionCode") as Int

private val Project.themePkg: String
    get() = extra.get("themePkg") as String

private fun ExtraPropertiesExtension.getOrNull(name: String) = if (has(name)) get(name) else null
