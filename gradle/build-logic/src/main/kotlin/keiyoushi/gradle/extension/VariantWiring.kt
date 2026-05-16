package keiyoushi.gradle.extension

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import keiyoushi.gradle.extension.codegen.GenerateExtensionManifestTask
import keiyoushi.gradle.extension.codegen.GenerateExtensionSourceTask
import keiyoushi.gradle.extension.codegen.GenerateUrlActivityTask
import keiyoushi.gradle.extension.dsl.ExtensionSpec
import keiyoushi.gradle.extension.dsl.ThemeDeeplinkSpec
import keiyoushi.gradle.tasks.GenerateKeepRulesTask
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

data class CodegenTasks(
    val source: TaskProvider<GenerateExtensionSourceTask>,
    val urlActivity: TaskProvider<GenerateUrlActivityTask>,
    val manifest: TaskProvider<GenerateExtensionManifestTask>,
)

// Property bridges populated in afterEvaluate, consumed by the variant API.
// AGP 9 locks defaultConfig early, so DSL-derived values must flow through providers.
data class VariantBridges(
    val versionCode: Property<Int>,
    val versionName: Property<String>,
    val appName: Property<String>,
    val nsfw: Property<String>,
    val extClass: Property<String>,
)

fun Project.registerCodegenTasks(): CodegenTasks {
    val source = tasks.register("generateExtensionSource", GenerateExtensionSourceTask::class.java) {
        outputDir.set(layout.buildDirectory.dir("generated/source/kei/main"))
    }
    val urlActivity = tasks.register("generateUrlActivity", GenerateUrlActivityTask::class.java) {
        outputDir.set(layout.buildDirectory.dir("generated/source/kei/urlactivity"))
    }
    val manifest = tasks.register("generateExtensionManifest", GenerateExtensionManifestTask::class.java) {
        outputFile.set(layout.buildDirectory.file("generated/manifest/kei/AndroidManifest.xml"))
    }
    return CodegenTasks(source, urlActivity, manifest)
}

fun Project.wireVariantApi(spec: ExtensionSpec, bridges: VariantBridges, codegenTasks: CodegenTasks) {
    extensions.configure(ApplicationAndroidComponentsExtension::class.java) {
        onVariants { variant ->
            variant.outputs.forEach { output ->
                output.versionCode.set(bridges.versionCode)
                output.versionName.set(bridges.versionName)
            }
            variant.manifestPlaceholders.put("appName", bridges.appName)
            variant.manifestPlaceholders.put("extClass", bridges.extClass)
            variant.manifestPlaceholders.put("nsfw", bridges.nsfw)

            val variantName = variant.name.replaceFirstChar { it.uppercase() }
            @Suppress("UnstableApiUsage")
            val keepRules = variant.sources.keepRules
            if (keepRules != null) {
                val keepTask = tasks.register<GenerateKeepRulesTask>("generate${variantName}KeepRules") {
                    this.applicationId.set(variant.applicationId)
                    this.extClass.set(bridges.extClass)
                }
                keepRules.addGeneratedSourceDirectory(keepTask) { it.outputDir }
            }

            variant.sources.kotlin?.addGeneratedSourceDirectory(codegenTasks.source) { it.outputDir }

            // Read spec directly: onVariants runs inside AGP's afterEvaluate, before ours,
            // so bridge values aren't populated yet.
            val sourceDeeplinks = spec.sources.orNull?.any { it.deeplinkSpec.isPresent } ?: false
            val themeDeeplinks = spec.theme.orNull?.let { themeName ->
                val themePath = ":lib-multisrc:$themeName"
                evaluationDependsOn(themePath)
                findProject(themePath)
                    ?.extensions
                    ?.findByType(ThemeDeeplinkSpec::class.java)
                    ?.pathPatterns
                    ?.orNull
                    ?.isNotEmpty()
            } == true
            if (sourceDeeplinks || themeDeeplinks) {
                variant.sources.kotlin?.addGeneratedSourceDirectory(codegenTasks.urlActivity) { it.outputDir }
                variant.sources.manifests.addGeneratedManifestFile(codegenTasks.manifest) { it.outputFile }
            }
        }
    }
}
