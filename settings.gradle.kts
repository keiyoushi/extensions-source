pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

// KITA HAPUS BAGIAN KONFLIK DISINI. BIARKAN BUILD.GRADLE YANG ATUR.

rootProject.name = "keiyoushi-extensions"

include(":core")

// 1. SCAN LIBRARY (Hanya load kalau foldernya ADA)
val libDir = File(rootDir, "lib")
if (libDir.exists()) {
    libDir.listFiles()?.filter { it.isDirectory }?.forEach { include(":lib:${it.name}") }
}

val multiSrcDir = File(rootDir, "lib-multisrc")
if (multiSrcDir.exists()) {
    multiSrcDir.listFiles()?.filter { it.isDirectory }?.forEach { include(":lib-multisrc:${it.name}") }
}

// 2. SCAN KHUSUS INDONESIA (SRC/ID)
// Script ini otomatis MENGABAIKAN folder bg, zh, all, dll yang error.
val idDir = File(rootDir, "src/id")
if (idDir.exists()) {
    idDir.listFiles()?.filter { it.isDirectory }?.forEach { extDir ->
        // Cek apakah ada file build di dalam folder ekstensi indonesia
        if (File(extDir, "build.gradle.kts").exists() || File(extDir, "build.gradle").exists()) {
             include(":src:id:${extDir.name}")
        }
    }
}
