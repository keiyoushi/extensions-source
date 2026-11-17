package io.github.keiyoushi.gradle.internal

import com.android.build.api.variant.AndroidComponentsExtension
import io.github.keiyoushi.gradle.api.dsl.KeiyoushiRuntimeExtension
import io.github.keiyoushi.gradle.internal.extensions.libs
import io.github.keiyoushi.gradle.tasks.GenerateRuntimeSourcesTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

private val MODULE_NAME = Regex("^[a-z][a-z0-9]*$")

internal fun checkRuntimeModule(module: String, api: Int) {
    check(MODULE_NAME.matches(module)) {
        "Runtime module '$module' must be lowercase letters and digits: it names the package keiyoushi.runtime.$module"
    }
    check(api >= 1) { "Runtime module '$module': API levels start at 1, got $api" }
}

/**
 * Adds `keiyoushiRuntime { requires(...) }` to a lib, theme or extension and generates its
 * `RuntimeModules` object in [packageName].
 */
internal fun Project.configureRuntimeConsumer(packageName: Provider<String>) {
    val runtime = extensions.create<KeiyoushiRuntimeExtension>("keiyoushiRuntime")
    val task = tasks.register<GenerateRuntimeSourcesTask>("generateRuntimeSources") {
        this.packageName.set(packageName)
        requires.set(runtime.requires)
        outputDir.set(layout.buildDirectory.dir("generated/source/keiyoushiRuntime"))
    }

    extensions.getByType(AndroidComponentsExtension::class.java).onVariants { variant ->
        if (runtime.requires.get().isNotEmpty()) {
            checkDependsOnRuntimeContract()
            variant.sources.kotlin?.addGeneratedSourceDirectory(task, GenerateRuntimeSourcesTask::outputDir)
        }
    }
}

/**
 * The generated code calls `keiyoushi.lib.runtime`, which keiyoushi/runtime publishes as its `contract`
 * artifact. Its `api-<module>` artifacts bring the contract along, so either one written in the build
 * file will do.
 */
private fun Project.checkDependsOnRuntimeContract() {
    val group = libs.keiyoushi.runtime.contract.get().module.group
    val declared = listOf("api", "implementation")
        .mapNotNull { configurations.findByName(it) }
        .flatMap { it.dependencies.withType<ExternalModuleDependency>() }
        .any { it.group == group }
    check(declared) {
        "$path uses the Keiyoushi Runtime: add implementation(libs.keiyoushi.runtime.contract) to its dependencies"
    }
}
