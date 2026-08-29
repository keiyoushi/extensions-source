package io.github.keiyoushi.gradle.internal.extensions

import org.gradle.accessors.dm.LibrariesForKei
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.kotlin.dsl.the

internal val Project.libs get() = the<LibrariesForLibs>()
internal val Project.kei get() = the<LibrariesForKei>()

internal fun Project.spotlessTaskName() = if (providers.environmentVariable("CI").orNull != "true") "spotlessApply" else "spotlessCheck"
