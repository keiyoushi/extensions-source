import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.gradle.tasks.PackageAndroidArtifact
import keiyoushi.gradle.extensions.KeiyoushiExtension
import keiyoushi.gradle.extensions.KeiyoushiMultisrcExtension
import keiyoushi.gradle.extensions.VALID_LIB_VERSIONS
import keiyoushi.gradle.extensions.alias
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

        val extClassProvider = keiyoushi.className.map { if (it.startsWith(".")) it else ".$it" }

        val versionCodeProvider = keiyoushi.theme.map { themeName ->
            val themeProject = project(":lib-multisrc:$themeName")
            val themeKeiyoushi = themeProject.extensions.findByType(KeiyoushiMultisrcExtension::class.java)
                ?: throw AssertionError("Theme project ${themeProject.path} must apply kei.plugins.multisrc")
            val themeLibVersion = themeKeiyoushi.libVersion.get()
            val extLibVersion = keiyoushi.libVersion.get()
            assertWithoutFlag(themeLibVersion == extLibVersion) {
                "Multisrc ($themeName) libVersion ($themeLibVersion) and extension libVersion ($extLibVersion) must match."
            }
            themeKeiyoushi.baseVersionCode.get() + keiyoushi.versionCode.get()
        }.orElse(keiyoushi.versionCode)

        val versionNameProvider = keiyoushi.libVersion.flatMap { libVersion ->
            assertWithoutFlag(libVersion in VALID_LIB_VERSIONS) {
                "libVersion $libVersion is not supported. Supported versions: $VALID_LIB_VERSIONS"
            }
            versionCodeProvider.map { "$libVersion.$it" }
        }

        val appNameProvider = keiyoushi.name.map { name ->
            assertWithoutFlag(name.all { it.code < 0x180 }) { "Extension name should be romanized" }
            "Tachiyomi: $name"
        }

        val nsfwProvider = keiyoushi.contentWarning.map { if (it == ContentWarning.SAFE) "0" else "1" }

        val contentWarningProvider = keiyoushi.contentWarning.map {
            when (it) {
                ContentWarning.SAFE -> "0"
                ContentWarning.MIXED -> "1"
                ContentWarning.NSFW -> "2"
            }
        }

        val sourceHostProvider = keiyoushi.baseUrl.map { baseUrl ->
            if (baseUrl.isEmpty()) {
                ""
            } else {
                val split = baseUrl.split("://")
                assertWithoutFlag(split.size == 2) { "'baseUrl' must be in the format of 'https://example.com'" }
                split[1].split("/")[0]
            }
        }.orElse("")

        androidComponents {
            onVariants { variant ->
                val variantName = variant.name.replaceFirstChar { it.uppercase() }

                @Suppress("UnstableApiUsage")
                val keepRules = variant.sources.keepRules
                if (keepRules != null) {
                    val task = tasks.register<GenerateKeepRulesTask>("generate${variantName}KeepRules") {
                        this.applicationId.set(variant.applicationId)
                        this.extClass.set(extClassProvider)
                    }
                    keepRules.addGeneratedSourceDirectory(task) { it.outputDir }
                }

                variant.sources.manifests.addStaticManifestFile("AndroidManifest.xml")

                variant.outputs.forEach { output ->
                    output.versionCode.set(versionCodeProvider)
                    output.versionName.set(versionNameProvider)
                }

                variant.manifestPlaceholders.put("appName", appNameProvider)
                variant.manifestPlaceholders.put("tachiyomix.name", keiyoushi.name)
                variant.manifestPlaceholders.put("extClass", extClassProvider)
                variant.manifestPlaceholders.put("nsfw", nsfwProvider)
                variant.manifestPlaceholders.put("tachiyomix.contentWarning", contentWarningProvider)
                variant.manifestPlaceholders.put("tachiyomix.extensionLib", keiyoushi.libVersion.map { it.toString() })
                variant.manifestPlaceholders.put("SOURCEHOST", sourceHostProvider)
                variant.manifestPlaceholders.put("SOURCESCHEME", "https")
            }
        }

        base {
            archivesName.set(versionNameProvider.map { "tachiyomi-$applicationIdSuffix-v$it" })
        }

        afterEvaluate {
            val themeName = keiyoushi.theme.orNull
            if (themeName != null) {
                evaluationDependsOn(":lib-multisrc:$themeName")
            }

            dependencies {
                if (themeName != null) {
                    implementation(project(":lib-multisrc:$themeName"))
                }
                implementation(project(":core"))
                compileOnly(libs.bundles.common)
            }

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
