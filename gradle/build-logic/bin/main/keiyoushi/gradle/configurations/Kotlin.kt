package keiyoushi.gradle.configurations

import keiyoushi.gradle.extensions.kei
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.HasConfigurableKotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import tapmoc.configureJavaCompatibility

fun Project.configureKotlin() {
    configureJavaCompatibility(kei.versions.java.get().toInt())

    kotlin {
        compilerOptions {
            optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
            freeCompilerArgs.add("-Xcontext-parameters")
        }
    }
}

private fun Project.kotlin(block: KotlinBaseExtension.() -> Unit) {
    extensions.configure(block)
}

private fun KotlinBaseExtension.compilerOptions(block: KotlinCommonCompilerOptions.() -> Unit) {
    if (this is HasConfigurableKotlinCompilerOptions<*>) compilerOptions(block)
}
