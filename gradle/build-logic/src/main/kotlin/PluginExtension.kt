import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.gradle.tasks.PackageAndroidArtifact
import keiyoushi.gradle.extensions.DeeplinkFilter
import keiyoushi.gradle.extensions.DeeplinkSpec
import keiyoushi.gradle.extensions.KeiyoushiExtension
import keiyoushi.gradle.extensions.KeiyoushiMultisrcExtension
import keiyoushi.gradle.extensions.VALID_LIB_VERSIONS
import keiyoushi.gradle.extensions.alias
import keiyoushi.gradle.extensions.compileOnly
import keiyoushi.gradle.extensions.implementation
import keiyoushi.gradle.extensions.kei
import keiyoushi.gradle.extensions.libs
import keiyoushi.gradle.extensions.plugins
import keiyoushi.gradle.tasks.GenerateExtensionManifestTask
import keiyoushi.gradle.tasks.GenerateKeepRulesTask
import keiyoushi.gradle.utils.assertWithoutFlag
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.register
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

        val keiyoushi = extensions.create("keiyoushi", KeiyoushiExtension::class.java)
        val applicationIdSuffix = "${project.parent?.name}.${project.name}"

        android {
            namespace = "eu.kanade.tachiyomi.extension"

            defaultConfig {
                this.applicationIdSuffix = applicationIdSuffix
            }

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

        val themeExtension = keiyoushi.theme.map { themeName ->
            project(":lib-multisrc:$themeName").extensions.findByType(KeiyoushiMultisrcExtension::class.java)
                ?: throw AssertionError("Theme project :lib-multisrc:$themeName must apply kei.plugins.multisrc")
        }

        val versionCodeProvider = themeExtension.flatMap { themeKeiyoushi ->
            val themeLib = themeKeiyoushi.libVersion.get()
            val extLib = keiyoushi.libVersion.get()
            assertWithoutFlag(themeLib == extLib) {
                "Multisrc libVersion ($themeLib) and extension libVersion ($extLib) must match."
            }
            themeKeiyoushi.baseVersionCode.zip(keiyoushi.versionCode) { base, ext -> base + ext }
        }.orElse(keiyoushi.versionCode)

        val versionNameProvider = keiyoushi.libVersion.flatMap { libVersion ->
            assertWithoutFlag(libVersion in VALID_LIB_VERSIONS) {
                "libVersion $libVersion is not supported. Supported versions: $VALID_LIB_VERSIONS"
            }
            versionCodeProvider.map { "$libVersion.$it" }
        }

        val classNameProvider = keiyoushi.className.map { name ->
            assertWithoutFlag(!name.startsWith(".")) { "className must not start with '.'" }
            name
        }

        val themeDeeplinks = themeExtension
            .flatMap { it.deeplinks }
            .orElse(emptyList())

        val deeplinksProvider = keiyoushi.deeplinks
            .zip(themeDeeplinks) { local, theme -> local + theme }
            .zip(keiyoushi.baseUrl.orElse("")) { specs, baseUrl ->
                val defaultHost = baseUrl.takeIf { it.isNotEmpty() }
                    ?.split("://")?.getOrNull(1)
                    ?.split("/")?.first()
                    ?.takeIf { it.isNotEmpty() }
                specsToFilters(specs, defaultHost)
            }

        val manifestTask = tasks.register<GenerateExtensionManifestTask>("generateExtensionManifest") {
            this.filters.set(deeplinksProvider)
            this.extensionName.set(keiyoushi.name)
            this.className.set(classNameProvider)
            this.contentWarning.set(keiyoushi.contentWarning)
            this.extensionLib.set(keiyoushi.libVersion)
        }

        androidComponents {
            onVariants { variant ->
                val variantName = variant.name.replaceFirstChar { it.uppercase() }

                @Suppress("UnstableApiUsage")
                val keepRules = variant.sources.keepRules
                if (keepRules != null) {
                    val task = tasks.register<GenerateKeepRulesTask>("generate${variantName}KeepRules") {
                        this.applicationId.set(variant.applicationId)
                        this.className.set(classNameProvider)
                    }
                    keepRules.addGeneratedSourceDirectory(task) { it.outputDir }
                }

                variant.sources.manifests.addStaticManifestFile("AndroidManifest.xml")
                variant.sources.manifests.addGeneratedManifestFile(manifestTask) { it.outputFile }

                variant.outputs.forEach { output ->
                    output.versionCode.set(versionCodeProvider)
                    output.versionName.set(versionNameProvider)
                }

            }
        }

        base {
            archivesName.set(versionNameProvider.map { "tachiyomi-$applicationIdSuffix-v$it" })
        }

        dependencies {
            addProvider("implementation", keiyoushi.theme.map { project(":lib-multisrc:$it") })
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

private fun specsToFilters(specs: List<DeeplinkSpec>, defaultHost: String?): List<DeeplinkFilter> =
    specs.mapNotNull { spec ->
        val hosts = spec.hosts.getOrElse(emptyList()).ifEmpty { listOfNotNull(defaultHost) }
        val paths = spec.pathPatterns.getOrElse(emptyList())
        if (paths.isNotEmpty()) {
            check(hosts.isNotEmpty()) {
                "deeplink has path patterns but no host could be resolved — set baseUrl or specify host() explicitly"
            }
            DeeplinkFilter(hosts, paths)
        } else {
            null
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
