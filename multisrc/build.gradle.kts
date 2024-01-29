plugins {
    id("com.android.library")
    kotlin("android")
    id("kotlinx-serialization")
}

android {
    compileSdk = AndroidConfig.compileSdk

    defaultConfig {
        minSdk = 29
    }

    namespace = "eu.kanade.tachiyomi.lib.themesources"

    kotlinOptions {
        freeCompilerArgs += "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
    }
}

repositories {
    mavenCentral()
}

configurations {
    compileOnly {
        isCanBeResolved = true
    }
}

dependencies {
    compileOnly(libs.bundles.common)

    // Only PeachScan sources uses the image-decoder dependency.
    //noinspection UseTomlInstead
    compileOnly("com.github.tachiyomiorg:image-decoder:fbd6601290")

    // Implements all :lib libraries on the multisrc generator
    // Note that this does not mean that generated sources are going to
    // implement them too; this is just to be able to compile and generate sources.
    rootProject.subprojects
        .filter { it.path.startsWith(":lib:") }
        .forEach(::implementation)
}

tasks {
    register<JavaExec>("generateExtensions") {
        val buildDir = layout.buildDirectory.asFile.get()
        classpath = configurations.compileOnly.get() +
            configurations.androidApis.get() + // android.jar path
            files("$buildDir/intermediates/aar_main_jar/debug/classes.jar") // jar made from this module
        mainClass.set("generator.GeneratorMainKt")

        workingDir = workingDir.parentFile // project root

        errorOutput = System.out // for GitHub workflow commands

        if (!logger.isInfoEnabled) {
            standardOutput = org.gradle.internal.io.NullOutputStream.INSTANCE
        }

        dependsOn("ktLint", "assembleDebug")
    }

    register<org.jmailen.gradle.kotlinter.tasks.LintTask>("ktLint") {
        if (project.hasProperty("theme")) {
            val theme = project.property("theme")
            source(files("src/main/java/eu/kanade/tachiyomi/multisrc/$theme", "overrides/$theme"))
            return@register
        }
        source(files("src", "overrides"))
    }

    register<org.jmailen.gradle.kotlinter.tasks.FormatTask>("ktFormat") {
        if (project.hasProperty("theme")) {
            val theme = project.property("theme")
            source(files("src/main/java/eu/kanade/tachiyomi/multisrc/$theme", "overrides/$theme"))
            return@register
        }
        source(files("src", "overrides"))
    }
}
