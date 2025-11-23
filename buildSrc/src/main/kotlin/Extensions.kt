import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.extra

var ExtensionAware.baseVersionCode: Int
    get() = extra.get("baseVersionCode") as Int
    set(value) = extra.set("baseVersionCode", value)

private val reverseDependencyCache = mutableMapOf<String, Set<Project>>()
private var reverseDependencyCacheInitialized = false

private fun Project.buildReverseDependencyCache() {
    if (reverseDependencyCacheInitialized) return
    reverseDependencyCacheInitialized = true

    // Build reverse dependency map
    val map = mutableMapOf<String, MutableSet<Project>>()

    rootProject.allprojects.forEach { p ->
        p.configurations.forEach { config ->
            config.dependencies.forEach { dep ->
                if (dep is ProjectDependency) {
                    val dependents = map.getOrPut(dep.path) { mutableSetOf() }
                    dependents.add(p)
                }
            }
        }
    }

    // finalize cache
    map.forEach { (k, v) -> reverseDependencyCache[k] = v }
}

fun Project.getDependents(): Set<Project> {
    buildReverseDependencyCache()
    return reverseDependencyCache[path] ?: emptySet()
}

fun Project.printDependentExtensions() =
    printDependentExtensions(mutableSetOf())

private fun Project.printDependentExtensions(visited: MutableSet<String>) {
    if (!visited.add(this.path)) return

    getDependents().forEach { project ->
        when {
            project.path.startsWith(":src:") -> {
                println(project.path)
            }

            project.path.startsWith(":lib-multisrc:") -> {
                project.getDependents().forEach {
                    if (visited.add(it.path)) println(it.path)
                }
            }

            project.path.startsWith(":lib:") -> {
                project.printDependentExtensions(visited)
            }
        }
    }
}

