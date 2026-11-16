package io.github.keiyoushi.gradle.internal

import com.android.build.api.variant.AndroidComponentsExtension
import io.github.keiyoushi.gradle.api.dsl.KeiyoushiRuntimeExtension
import io.github.keiyoushi.gradle.tasks.GenerateRuntimeSourcesTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

/** The shim every runtime consumer compiles against */
internal const val RUNTIME_LIB = ":lib:runtime"

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
            checkDependsOnRuntimeLib()
            variant.sources.kotlin?.addGeneratedSourceDirectory(task, GenerateRuntimeSourcesTask::outputDir)
        }
    }
}

/**
 * The generated code calls `keiyoushi.lib.runtime`. The dependency must be written in the build
 * file: CI finds a lib's dependents by reading build files, so the runtime shim's consumers must be
 * visible there.
 */
private fun Project.checkDependsOnRuntimeLib() {
    val declared = listOf("api", "implementation")
        .mapNotNull { configurations.findByName(it) }
        .flatMap { it.dependencies.withType<ProjectDependency>() }
        .any { it.path == RUNTIME_LIB }
    check(declared) {
        "$path uses the Keiyoushi Runtime: add implementation(project(\"$RUNTIME_LIB\")) to its dependencies"
    }
}

/**
 * Every runtime module this project calls, directly or through the libs and theme it depends on,
 * at the highest API level any of them needs.
 */
internal fun Project.collectRuntimeRequirements(): Map<String, Int> {
    val requirements = sortedMapOf<String, Int>()
    val seen = mutableSetOf<String>()

    fun visit(project: Project) {
        if (!seen.add(project.path)) return
        project.extensions.findByType<KeiyoushiRuntimeExtension>()?.requires?.get()?.forEach { (module, api) ->
            requirements.merge(module, api, ::maxOf)
        }
        listOf("api", "implementation")
            .mapNotNull { project.configurations.findByName(it) }
            .flatMap { it.dependencies.withType<ProjectDependency>() }
            .forEach { visit(evaluationDependsOn(it.path)) }
    }

    visit(this)
    return requirements
}
