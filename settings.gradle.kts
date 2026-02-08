pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

rootProject.name = "keiyoushi-extensions" 

include(":core")

// SCAN OTOMATIS: Hanya ambil folder yang BENAR-BENAR ADA
val srcDir = File(rootDir, "src")
if (srcDir.exists()) {
    srcDir.listFiles()?.filter { it.isDirectory }?.forEach { langDir ->
        langDir.listFiles()?.filter { it.isDirectory }?.forEach { extDir ->
            // Cek apakah di dalamnya ada file build script
            if (File(extDir, "build.gradle.kts").exists() || File(extDir, "build.gradle").exists()) {
                include(":src:${langDir.name}:${extDir.name}")
            }
        }
    }
}


