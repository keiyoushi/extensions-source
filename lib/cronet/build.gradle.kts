plugins {
    alias(kei.plugins.library)
}

val cronetRuntime = configurations.create("cronetRuntime") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("keiyoushi-cronet-runtime"))
    }
}

dependencies {
    cronetRuntime(project(":lib:cronetruntime"))
}

abstract class RuntimeAsset : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val runtime: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val apk = runtime.asFileTree.single { it.extension == "apk" }
        apk.copyTo(outputDir.get().file("cronet-runtime.apk").asFile, overwrite = true)
    }
}

val runtimeAsset = tasks.register<RuntimeAsset>("cronetRuntimeAsset") {
    runtime.from(cronetRuntime)
    outputDir.set(layout.buildDirectory.dir("generated/cronetRuntime"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(runtimeAsset, RuntimeAsset::outputDir)
    }
}
