package keiyoushi.gradle.extensions

import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.DependencyHandlerScope

fun DependencyHandlerScope.api(dependencyNotation: Provider<MinimalExternalModuleDependency>) {
    add("api", dependencyNotation)
}

@JvmName("compileOnlyBundle")
fun DependencyHandlerScope.compileOnly(dependencyNotation: Provider<ExternalModuleDependencyBundle>) {
    add("compileOnly", dependencyNotation)
}

fun DependencyHandlerScope.compileOnly(dependencyNotation: Provider<MinimalExternalModuleDependency>) {
    add("compileOnly", dependencyNotation.map { "${it.module.group}:${it.module.name}:${it.versionConstraint.requiredVersion}@aar" })
}

@JvmName("implementationBundle")
fun DependencyHandlerScope.implementation(dependencyNotation: Provider<ExternalModuleDependencyBundle>) {
    add("implementation", dependencyNotation)
}

fun DependencyHandlerScope.implementation(dependencyNotation: Provider<MinimalExternalModuleDependency>) {
    add("implementation", dependencyNotation)
}

fun DependencyHandlerScope.implementation(dependencyNotation: Project) {
    add("implementation", dependencyNotation)
}

fun DependencyHandlerScope.implementation(dependencyNotation: ProjectDependency) {
    add("implementation", dependencyNotation)
}
