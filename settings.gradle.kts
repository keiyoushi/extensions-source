pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "keiyoushi-extensions"

include(":core")

// 1. LIBRARY (Tetap load jika ada, untuk jaga-jaga)
val libDir = File(rootDir, "lib")
if (libDir.exists()) {
    libDir.listFiles()?.filter { it.isDirectory }?.forEach { include(":lib:${it.name}") }
}

val multiSrcDir = File(rootDir, "lib-multisrc")
if (multiSrcDir.exists()) {
    multiSrcDir.listFiles()?.filter { it.isDirectory }?.forEach { include(":lib-multisrc:${it.name}") }
}

// 2. FOKUS HANYA INDONESIA (SRC/ID)
// Script ini TIDAK AKAN melihat folder bg, zh, all, dll.
val idDir = File(rootDir, "src/id")
if (idDir.exists()) {
    idDir.listFiles()?.filter { it.isDirectory }?.forEach { extDir ->
        // Cek apakah ada file build di dalam folder ekstensi indonesia
        if (File(extDir, "build.gradle.kts").exists() || File(extDir, "build.gradle").exists()) {
             include(":src:id:${extDir.name}")
        }
    }
}
