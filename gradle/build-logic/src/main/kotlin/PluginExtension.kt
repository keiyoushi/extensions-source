import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.gradle.tasks.PackageAndroidArtifact
import com.google.devtools.ksp.gradle.KspExtension
import keiyoushi.gradle.extensions.BaseUrlSpec
import keiyoushi.gradle.extensions.DeeplinkFilter
import keiyoushi.gradle.extensions.DeeplinkSpec
import keiyoushi.gradle.extensions.KeiyoushiExtension
import keiyoushi.gradle.extensions.KeiyoushiMultisrcExtension
import keiyoushi.gradle.extensions.VALID_LIB_VERSIONS
import keiyoushi.gradle.extensions.alias
import keiyoushi.gradle.extensions.compileOnly
import keiyoushi.gradle.extensions.implementation
import keiyoushi.gradle.extensions.kei
import keiyoushi.gradle.extensions.ksp
import keiyoushi.gradle.extensions.libs
import keiyoushi.gradle.extensions.plugins
import keiyoushi.gradle.tasks.GenerateExtensionManifestTask
import keiyoushi.gradle.tasks.GenerateKeepRulesTask
import keiyoushi.gradle.utils.assertWithoutFlag
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
            alias(libs.plugins.ksp)

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
            evaluationDependsOn(":lib-multisrc:$themeName").extensions.findByType(KeiyoushiMultisrcExtension::class.java)
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

        val classNameProvider = keiyoushi.sources.flatMap { sources ->
            if (sources.isNotEmpty()) {
                providers.provider { "ExtensionGenerated" }
            } else {
                keiyoushi.className.map { name ->
                    assertWithoutFlag(!name.startsWith(".")) { "className must not start with '.'" }
                    name
                }
            }
        }

        val themeDeeplinks = themeExtension
            .flatMap { it.deeplinks }
            .orElse(emptyList())

        val defaultHostsProvider = keiyoushi.sources.flatMap { sources ->
            if (sources.isNotEmpty()) {
                providers.provider { sources.flatMap { it.resolvedBaseUrl.get().allUrls() }.mapNotNull(::extractHost) }
            } else {
                keiyoushi.baseUrl.map { listOfNotNull(extractHost(it)) }
            }
        }.orElse(emptyList())

        val deeplinksProvider = keiyoushi.deeplinks
            .zip(themeDeeplinks) { local, theme -> local + theme }
            .zip(defaultHostsProvider) { specs, defaultHosts ->
                specsToFilters(specs, defaultHosts)
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
            ksp(project(":compiler"))
        }

        afterEvaluate {
            val specs = keiyoushi.sources.get()
            if (specs.isNotEmpty()) {
                val extName = keiyoushi.name.get()
                val resolvedSources = specs.map { spec ->
                    val name = spec.name.orElse(extName).get()
                    val lang = spec.lang.get()
                    val baseUrlSpec = spec.resolvedBaseUrl.get()
                    val skipCodeGen = spec.skipCodeGen.getOrElse(false)

                    if (skipCodeGen && specs.size > 1) {
                        error("skipCodeGen cannot be used with multiple source {} blocks")
                    }
                    if (skipCodeGen && baseUrlSpec !is BaseUrlSpec.Static) {
                        error("skipCodeGen cannot be used with mirror or custom baseUrl — those require property injection")
                    }

                    val baseUrl = baseUrlSpec.toData()
                    val id = spec.id.orElse(
                        providers.provider {
                            computeSourceId(name, lang, spec.versionId.orElse(1).get())
                        },
                    ).get()
                    ResolvedSourceData(name, lang, id, baseUrl, skipCodeGen)
                }
                val translationsFile = project(":core").projectDir.resolve("translations/strings.json")
                extensions.configure<KspExtension> {
                    arg("kei_sources", Json.encodeToString<List<ResolvedSourceData>>(resolvedSources))
                    arg("kei_translations", translationsFile.absolutePath)
                }
                tasks.matching { it.name.startsWith("ksp") }.configureEach {
                    inputs.file(translationsFile)
                }
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

@Serializable
private data class ResolvedSourceData(val name: String, val lang: String, val id: Long, val baseUrl: BaseUrlSpecData, val skipCodeGen: Boolean = false)

@Serializable
private data class BaseUrlSpecData(
    val type: String,
    val urls: List<String>,
)

private fun BaseUrlSpec.toData(): BaseUrlSpecData = when (this) {
    is BaseUrlSpec.Static -> BaseUrlSpecData("static", listOf(url))
    is BaseUrlSpec.Mirrors -> BaseUrlSpecData("mirrors", mirrors)
    is BaseUrlSpec.Custom -> BaseUrlSpecData("custom", listOf(defaultUrl))
}

private fun computeSourceId(name: String, lang: String, versionId: Int = 1): Long {
    val key = "${name.lowercase()}/$lang/$versionId"
    val bytes = java.security.MessageDigest.getInstance("MD5").digest(key.toByteArray())
    return (0..7).map { bytes[it].toLong() and 0xff }
        .reduce { acc, l -> (acc shl 8) or l } and Long.MAX_VALUE
}

private fun extractHost(url: String): String? = url.split("://").getOrNull(1)?.split("/")?.first()?.takeIf { it.isNotEmpty() }

private fun specsToFilters(specs: List<DeeplinkSpec>, defaultHosts: List<String>): List<DeeplinkFilter> = specs.mapNotNull { spec ->
    val hosts = spec.hosts.getOrElse(emptyList()).ifEmpty { defaultHosts }
    val paths = spec.pathPatterns.getOrElse(emptyList())
    if (paths.isNotEmpty()) {
        check(hosts.isNotEmpty()) {
            "deeplink has path patterns but no host could be resolved — set a source baseUrl or specify host() explicitly"
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
