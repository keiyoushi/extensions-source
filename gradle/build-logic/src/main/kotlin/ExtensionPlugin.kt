import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.tasks.PackageAndroidArtifact
import com.google.devtools.ksp.gradle.KspExtension
import io.github.keiyoushi.gradle.api.DeeplinkFilter
import io.github.keiyoushi.gradle.api.dsl.ExtensionDeeplink
import io.github.keiyoushi.gradle.api.dsl.KeiyoushiExtension
import io.github.keiyoushi.gradle.api.dsl.KeiyoushiThemeExtension
import io.github.keiyoushi.gradle.internal.ExtensionMetadata
import io.github.keiyoushi.gradle.internal.ResolvedSource
import io.github.keiyoushi.gradle.internal.SourceMetadata
import io.github.keiyoushi.gradle.internal.VALID_LIB_VERSIONS
import io.github.keiyoushi.gradle.internal.extensions.alias
import io.github.keiyoushi.gradle.internal.extensions.compileOnly
import io.github.keiyoushi.gradle.internal.extensions.implementation
import io.github.keiyoushi.gradle.internal.extensions.kei
import io.github.keiyoushi.gradle.internal.extensions.ksp
import io.github.keiyoushi.gradle.internal.extensions.libs
import io.github.keiyoushi.gradle.internal.extensions.plugins
import io.github.keiyoushi.gradle.internal.toMetadata
import io.github.keiyoushi.gradle.tasks.CreateExtensionJarTask
import io.github.keiyoushi.gradle.tasks.GenerateKeepRulesTask
import io.github.keiyoushi.gradle.tasks.GenerateManifestTask
import io.github.keiyoushi.gradle.tasks.GenerateSourceInfoTask
import io.github.keiyoushi.gradle.tasks.SignExtensionJarTask
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

@Suppress("UNUSED")
class ExtensionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        plugins {
            alias(libs.plugins.android.application)
            alias(libs.plugins.kotlin.serialization)
            alias(libs.plugins.ksp)

            alias(kei.plugins.android.base)
            alias(kei.plugins.spotless)
        }

        val keiyoushi = extensions.create<KeiyoushiExtension>("keiyoushi")
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
            evaluationDependsOn(":lib-multisrc:$themeName").extensions.findByType<KeiyoushiThemeExtension>()
                ?: throw AssertionError("Theme project :lib-multisrc:$themeName must apply kei.plugins.multisrc")
        }

        val versionCodeProvider = themeExtension.flatMap { themeKeiyoushi ->
            val themeLib = themeKeiyoushi.libVersion.get()
            val extLib = keiyoushi.libVersion.get()
            check(themeLib == extLib) {
                "Multisrc libVersion ($themeLib) and extension libVersion ($extLib) must match."
            }
            themeKeiyoushi.baseVersionCode.zip(keiyoushi.versionCode) { base, ext -> base + ext }
        }.orElse(keiyoushi.versionCode)

        val versionNameProvider = keiyoushi.libVersion.flatMap { libVersion ->
            check(libVersion in VALID_LIB_VERSIONS) {
                "libVersion $libVersion is not supported. Supported versions: $VALID_LIB_VERSIONS"
            }
            versionCodeProvider.map { "$libVersion.$it" }
        }

        val themeDeeplinks = themeExtension
            .flatMap { it.deeplinks }
            .orElse(emptyList())

        val defaultHostsProvider = keiyoushi.sources.map { sources ->
            sources.flatMap { it.resolvedBaseUrl.get().allUrls() }.mapNotNull(::extractHost)
        }

        val deeplinksProvider = keiyoushi.deeplinks
            .zip(themeDeeplinks) { local, theme -> local + theme }
            .zip(defaultHostsProvider) { specs, defaultHosts ->
                specsToFilters(specs, defaultHosts)
            }

        val manifestTask = tasks.register<GenerateManifestTask>("generateExtensionManifest") {
            this.filters.set(deeplinksProvider)
            this.extensionName.set(keiyoushi.name)
            this.contentWarning.set(keiyoushi.contentWarning)
            this.extensionLib.set(keiyoushi.libVersion)
        }

        val sourceInfoTask = tasks.register<GenerateSourceInfoTask>("generateSourceInfo") {
            this.outputFile.set(layout.buildDirectory.file("keiyoushi-source-info.json"))
        }

        val proguardConfiguration = configurations.create("proguard") {
            isCanBeConsumed = false
            isCanBeResolved = true
        }
        dependencies { add("proguard", libs.proguard) }

        val providedClasspath = configurations.create("extensionProvidedClasspath") {
            isCanBeConsumed = false
            isCanBeResolved = true
            extendsFrom(configurations.getByName("compileOnly"))
        }

        val signingConfig = extensions.getByType(ApplicationExtension::class.java).signingConfigs
            .getByName(if (rootProject.file("signingkey.jks").exists()) "release" else "debug")

        androidComponents {
            val bootClasspath = sdkComponents.bootClasspath

            onVariants { variant ->
                val variantName = variant.name.replaceFirstChar { it.uppercase() }

                @Suppress("UnstableApiUsage")
                val keepRules = variant.sources.keepRules
                if (keepRules != null) {
                    val task = tasks.register<GenerateKeepRulesTask>("generate${variantName}KeepRules") {
                        this.applicationId.set(variant.applicationId)
                    }
                    keepRules.addGeneratedSourceDirectory(task) { it.outputDir }
                }

                variant.sources.manifests.addStaticManifestFile("AndroidManifest.xml")
                variant.sources.manifests.addGeneratedManifestFile(manifestTask) { it.outputFile }

                variant.outputs.forEach { output ->
                    output.versionCode.set(versionCodeProvider)
                    output.versionName.set(versionNameProvider)
                }

                if (variant.buildType == "release") {
                    val externalLibs = providedClasspath.incoming.artifactView {
                        attributes.attribute(ARTIFACT_TYPE_ATTRIBUTE, "android-classes-jar")
                    }.files

                    val createTask = tasks.register<CreateExtensionJarTask>("create${variantName}ExtensionJar") {
                        libraryClasspath.from(externalLibs, bootClasspath)
                        proguardConfigFile.set(layout.buildDirectory.file("outputs/mapping/${variant.name}/configuration.txt"))
                        @Suppress("UnstableApiUsage")
                        manifestFile.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                        @Suppress("UnstableApiUsage")
                        apkDir.set(variant.artifacts.get(SingleArtifact.APK))
                        proguardClasspath.from(proguardConfiguration)
                        outputJar.set(layout.buildDirectory.file("intermediates/extension_jar/${variant.name}/unsigned.jar"))
                    }

                    @Suppress("UnstableApiUsage")
                    variant.artifacts.forScope(ScopedArtifacts.Scope.ALL)
                        .use(createTask)
                        .toGet(
                            ScopedArtifact.CLASSES,
                            CreateExtensionJarTask::jars,
                            CreateExtensionJarTask::dirs,
                        )

                    val signTask = tasks.register<SignExtensionJarTask>("sign${variantName}ExtensionJar") {
                        inputJar.set(createTask.flatMap { it.outputJar })
                        signingConfig.storeFile?.let { keystore.from(it) }
                        storePassword.set(signingConfig.storePassword.orEmpty())
                        keyAlias.set(signingConfig.keyAlias.orEmpty())
                        keyPassword.set(signingConfig.keyPassword.orEmpty())
                        minSdkVersion.set(kei.versions.android.sdk.min.map { it.toInt() })
                        val jarName = versionNameProvider.map { "tachiyomi-$applicationIdSuffix-v$it.jar" }
                        outputJar.set(layout.buildDirectory.file(jarName.map { "outputs/jar/${variant.name}/$it" }))
                    }

                    tasks.matching { it.name == "assemble$variantName" }
                        .configureEach { dependsOn(signTask) }
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
            check(specs.isNotEmpty()) { "At least one source { } block is required" }

            val extName = keiyoushi.name.get()
            val resolvedSources = specs.map { spec ->
                val name = spec.name.orElse(extName).get()
                val lang = spec.lang.get()
                val baseUrl = spec.resolvedBaseUrl.get().toMetadata()

                val id = spec.id.orElse(
                    providers.provider {
                        computeSourceId(name, lang, spec.versionId.orElse(1).get())
                    },
                ).get()
                ResolvedSource(name, lang, id, baseUrl)
            }
            val translationsFile = project(":core").projectDir.resolve("translations/strings.json")
            extensions.configure<KspExtension> {
                arg("kei_sources", Json.encodeToString<List<ResolvedSource>>(resolvedSources))
                arg("kei_translations", translationsFile.absolutePath)
            }
            tasks.matching { it.name.startsWith("ksp") }.configureEach {
                inputs.file(translationsFile)
            }

            val packageName = "eu.kanade.tachiyomi.extension.$applicationIdSuffix"
            val sourceInfos = resolvedSources.map { source ->
                SourceMetadata(
                    id = source.id,
                    name = source.name,
                    lang = source.lang,
                    baseUrl = source.baseUrl.defaultUrl,
                    mirrorUrls = source.baseUrl.mirrors.map { it.url },
                )
            }
            val extensionInfo = ExtensionMetadata(
                packageName = packageName,
                name = extName,
                versionCode = versionCodeProvider.get(),
                versionName = versionNameProvider.get(),
                extensionLib = keiyoushi.libVersion.get(),
                // Proto keiyoushi.gradle.api.ContentWarning: UNSPECIFIED=0, SAFE=1, MIXED=2, NSFW=3 (enum ordinal + 1).
                contentWarning = keiyoushi.contentWarning.get().ordinal + 1,
                sources = sourceInfos,
            )
            val sourceInfoJson = Json.encodeToString(extensionInfo)
            sourceInfoTask.configure { content.set(sourceInfoJson) }
            tasks.named("assembleRelease").configure { dependsOn(sourceInfoTask) }

            tasks.withType<PackageAndroidArtifact>().configureEach {
                createdBy.set("")
                doFirst {
                    appMetadata.asFile.orNull?.writeText("")
                }
            }
        }
    }
}

private fun computeSourceId(name: String, lang: String, versionId: Int = 1): Long {
    val key = "${name.lowercase()}/$lang/$versionId"
    val bytes = java.security.MessageDigest.getInstance("MD5").digest(key.toByteArray())
    return (0..7).map { bytes[it].toLong() and 0xff }
        .reduce { acc, l -> (acc shl 8) or l } and Long.MAX_VALUE
}

private fun extractHost(url: String): String? = url.split("://").getOrNull(1)?.split("/")?.first()?.takeIf { it.isNotEmpty() }

private fun specsToFilters(specs: List<ExtensionDeeplink>, defaultHosts: List<String>): List<DeeplinkFilter> = specs.mapNotNull { spec ->
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
