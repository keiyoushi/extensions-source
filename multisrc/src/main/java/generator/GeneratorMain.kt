package generator

import java.io.File

/**
 * Finds and calls all `ThemeSourceGenerator`s
 */
fun main(args: Array<String>) {
    val userDir = System.getProperty("user.dir")!!
    val sourcesDirPath = "$userDir/multisrc/src/main/java/eu/kanade/tachiyomi/multisrc"
    val sourcesDir = File(sourcesDirPath)

    // find all theme packages
    sourcesDir.list()!!
        .filter { File(sourcesDir, it).isDirectory }
        .forEach { themeSource ->
            // Find all XxxGenerator.kt files and invoke main from them
            File("$sourcesDirPath/$themeSource").list()!!
                .filter { it.endsWith("Generator.kt") }
                .mapNotNull { generatorClass ->
                    // Find Java class and extract method lists
                    Class.forName("eu/kanade/tachiyomi/multisrc/$themeSource/$generatorClass".replace("/", ".").substringBefore(".kt"))
                        .methods
                        .find { it.name == "main" }
                }
                .forEach { it.invoke(null, emptyArray<String>()) }
        }
}
