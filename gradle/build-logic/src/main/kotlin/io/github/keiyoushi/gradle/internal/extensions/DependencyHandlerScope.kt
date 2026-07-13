package io.github.keiyoushi.gradle.internal.extensions

import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.DependencyHandlerScope

internal fun DependencyHandlerScope.api(dependencyNotation: Provider<MinimalExternalModuleDependency>) {
    add("api", dependencyNotation)
}

internal fun DependencyHandlerScope.compileOnly(dependencyNotation: Provider<ExternalModuleDependencyBundle>) {
    add("compileOnly", dependencyNotation)
}

@JvmName("implementationBundle")
internal fun DependencyHandlerScope.implementation(dependencyNotation: Provider<ExternalModuleDependencyBundle>) {
    add("implementation", dependencyNotation)
}

internal fun DependencyHandlerScope.implementation(dependencyNotation: Provider<MinimalExternalModuleDependency>) {
    add("implementation", dependencyNotation)
}

internal fun DependencyHandlerScope.implementation(dependencyNotation: Project) {
    add("implementation", dependencyNotation)
}

internal fun DependencyHandlerScope.implementation(dependencyNotation: ProjectDependency) {
    add("implementation", dependencyNotation)
}

internal fun DependencyHandlerScope.compileOnly(dependencyNotation: ProjectDependency) {
    add("compileOnly", dependencyNotation)
}

internal fun DependencyHandlerScope.ksp(dependencyNotation: ProjectDependency) {
    add("ksp", dependencyNotation)
}
