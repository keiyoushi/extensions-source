include(":core")

// Load folder lib jika ada
if (File(rootDir, "lib").exists()) {
    File(rootDir, "lib").eachDir { include("lib:${it.name}") }
}

// Load folder lib-multisrc jika ada
if (File(rootDir, "lib-multisrc").exists()) {
    File(rootDir, "lib-multisrc").eachDir { include("lib-multisrc:${it.name}") }
}

// FUNGSI SCAN OTOMATIS (Hanya ambil folder yang NYATA)
fun loadAllIndividualExtensions() {
    val srcDir = File(rootDir, "src")
    if (srcDir.exists()) {
        srcDir.eachDir { langDir ->
            langDir.eachDir { extDir ->
                // Cek apakah folder build.gradle ada di dalamnya
                if (File(extDir, "build.gradle").exists()) {
                    include("src:${langDir.name}:${extDir.name}")
                }
            }
        }
    }
}

loadAllIndividualExtensions()

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory && it.name != ".gradle" && it.name != "build" }?.forEach(block)
}
