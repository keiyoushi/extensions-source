package keiyoushi.gradle.extension

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import keiyoushi.gradle.extension.codegen.GenerateExtensionManifestTask
import keiyoushi.gradle.extension.codegen.GenerateSourceTask
import keiyoushi.gradle.extension.codegen.ResolvedExtension
import keiyoushi.gradle.extension.dsl.ExtensionSpec
import keiyoushi.gradle.extension.dsl.ThemeDeeplinkSpec
import keiyoushi.gradle.tasks.GenerateKeepRulesTask
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

// Property bridges populated in afterEvaluate, consumed by the variant API.
// AGP 9 locks defaultConfig early, so DSL-derived values must flow through providers.
data class VariantBridges(
    val versionCode: Property<Int>,
    val versionName: Property<String>,
    val appName: Property<String>,
    val nsfw: Property<String>,
)

fun Project.registerManifestTask(): TaskProvider<GenerateExtensionManifestTask> =
    tasks.register("generateExtensionManifest", GenerateExtensionManifestTask::class.java) {
        outputFile.set(layout.buildDirectory.file("generated/manifest/kei/AndroidManifest.xml"))
    }

fun Project.registerGenerateSourceTask(
    spec: ExtensionSpec,
    resolvedExtension: Property<ResolvedExtension>,
): TaskProvider<GenerateSourceTask> =
    tasks.register("generateSource", GenerateSourceTask::class.java) {
        this.resolvedExtension.set(resolvedExtension)
        this.outputDir.set(layout.buildDirectory.dir("generated/source/kei"))
        this.sourceDir.set(file("src"))

        val themeName = spec.theme.orNull
        if (themeName != null) {
            val themePath = ":lib-multisrc:$themeName"
            evaluationDependsOn(themePath)
            val themeProject = findProject(themePath)
            if (themeProject != null) {
                this.themeDir.set(themeProject.file("src"))
            }
        }
    }

fun Project.wireVariantApi(
    spec: ExtensionSpec,
    bridges: VariantBridges,
    manifestTask: TaskProvider<GenerateExtensionManifestTask>,
    sourceTask: TaskProvider<GenerateSourceTask>,
) {
    extensions.configure(ApplicationAndroidComponentsExtension::class.java) {
        onVariants { variant ->
            val extClass = spec.className.map { ".$it" + "Generated" }

            variant.outputs.forEach { output ->
                output.versionCode.set(bridges.versionCode)
                output.versionName.set(bridges.versionName)
            }
            variant.manifestPlaceholders.put("appName", bridges.appName)
            variant.manifestPlaceholders.put("extClass", extClass)
            variant.manifestPlaceholders.put("nsfw", bridges.nsfw)

            val variantName = variant.name.replaceFirstChar { it.uppercase() }
            @Suppress("UnstableApiUsage")
            val keepRules = variant.sources.keepRules
            if (keepRules != null) {
                val keepTask = tasks.register<GenerateKeepRulesTask>("generate${variantName}KeepRules") {
                    this.applicationId.set(variant.applicationId)
                    this.extClass.set(extClass)
                }
                keepRules.addGeneratedSourceDirectory(keepTask) { it.outputDir }
            }

            variant.sources.kotlin?.addGeneratedSourceDirectory(sourceTask) { it.outputDir }

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
                variant.sources.manifests.addGeneratedManifestFile(manifestTask) { it.outputFile }
            }
        }
    }
}
