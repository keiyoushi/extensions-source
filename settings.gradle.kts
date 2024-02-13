include(":core")

// Load all modules under /lib
File(rootDir, "lib").eachDir { include("lib:${it.name}") }

// Load all modules under /lib-multisrc
File(rootDir, "lib-multisrc").eachDir { include("lib-multisrc:${it.name}") }

if (System.getenv("CI") != "true") {
    // Local development (full project build)

    /**
     * Add or remove modules to load as needed for local development here.
     */
    loadAllIndividualExtensions()
    // loadIndividualExtension("all", "mangadex")
} else {
    // Running in CI (GitHub Actions)

    val chunkSize = System.getenv("CI_CHUNK_SIZE").toInt()
    val chunk = System.getenv("CI_CHUNK_NUM").toInt()

    run {
        // Loads individual extensions
        File(rootDir, "src").getChunk(chunk, chunkSize)?.forEach {
            val name = ":extensions:individual:${it.parentFile.name}:${it.name}"
            println(name)
            include(name)
            project(name).projectDir = File("src/${it.parentFile.name}/${it.name}")
        }
    }
}

fun loadAllIndividualExtensions() {
    File(rootDir, "src").eachDir { dir ->
        dir.eachDir { subdir ->
            val name = ":extensions:individual:${dir.name}:${subdir.name}"
            include(name)
            project(name).projectDir = File("src/${dir.name}/${subdir.name}")
        }
    }
}
fun loadIndividualExtension(lang: String, name: String) {
    val projectName = ":extensions:individual:$lang:$name"
    include(projectName)
    project(projectName).projectDir = File("src/${lang}/${name}")
}

fun File.getChunk(chunk: Int, chunkSize: Int): List<File>? {
    return listFiles()
        // Lang folder
        ?.filter { it.isDirectory }
        // Extension subfolders
        ?.mapNotNull { dir -> dir.listFiles()?.filter { it.isDirectory } }
        ?.flatten()
        ?.sortedBy { it.name }
        ?.chunked(chunkSize)
        ?.get(chunk)
}

fun File.eachDir(block: (File) -> Unit) {
    val files = listFiles() ?: return
    for (file in files) {
        if (file.isDirectory && file.name != ".gradle" && file.name != "build") {
            block(file)
        }
    }
}
