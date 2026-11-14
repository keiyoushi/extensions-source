plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)

    alias(kei.plugins.android.base)
    alias(kei.plugins.spotless)
}

android {
    namespace = "keiyoushi.core"

    buildFeatures {
        resValues = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// When core's sources last changed, for the WebView proxy to let a newer build of the same version serve: the commit
// time of their last commit (the checked-out commit's in a shallow clone), or 0 outside git
val buildStamp = providers.exec {
    commandLine("git", "log", "-1", "--format=%ct", "--", "src/main")
    workingDir(projectDir)
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().toLongOrNull() ?: 0L }

abstract class CoreBuildSource : DefaultTask() {
    @get:Input
    abstract val stamp: Property<Long>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun write() {
        outputDir.get().file("keiyoushi/webview/internal/CoreBuild.kt").asFile.apply {
            parentFile.mkdirs()
            writeText(
                """
                |package keiyoushi.webview.internal
                |
                |internal object CoreBuild {
                |    /** When core's sources last changed, in seconds since the epoch; 0 if unknown. */
                |    const val STAMP: Long = ${stamp.get()}L
                |}
                |
                """.trimMargin(),
            )
        }
    }
}

val coreBuildSource = tasks.register<CoreBuildSource>("coreBuildSource") {
    stamp.set(buildStamp)
    outputDir.set(layout.buildDirectory.dir("generated/coreBuild"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.kotlin?.addGeneratedSourceDirectory(coreBuildSource, CoreBuildSource::outputDir)
    }
}

dependencies {
    compileOnly(libs.bundles.common)
    compileOnly(libs.tachiyomi.lib.v16)

    testImplementation(libs.bundles.common)
    testImplementation(libs.tachiyomi.lib.v16)
    testImplementation(libs.androidx.webkit)
    testImplementation(libs.junit)
}
