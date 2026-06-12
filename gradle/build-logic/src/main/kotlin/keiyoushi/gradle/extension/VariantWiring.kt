package keiyoushi.gradle.extension

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import keiyoushi.gradle.extension.codegen.GenerateExtensionManifestTask
import keiyoushi.gradle.extension.codegen.GenerateSourceTask
import keiyoushi.gradle.extension.codegen.ResolvedExtension
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
    resolvedExtension: Property<ResolvedExtension>,
): TaskProvider<GenerateSourceTask> =
    tasks.register("generateSource", GenerateSourceTask::class.java) {
        this.resolvedExtension.set(resolvedExtension)
        this.outputDir.set(layout.buildDirectory.dir("generated/source/kei"))
    }

fun Project.wireVariantApi(
    className: Property<String>,
    bridges: VariantBridges,
    manifestTask: TaskProvider<GenerateExtensionManifestTask>,
    sourceTask: TaskProvider<GenerateSourceTask>,
) {
    extensions.configure(ApplicationAndroidComponentsExtension::class.java) {
        onVariants { variant ->
            val extClass = className.map { ".$it" + "Generated" }

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
            variant.sources.manifests.addGeneratedManifestFile(manifestTask) { it.outputFile }
        }
    }
}
