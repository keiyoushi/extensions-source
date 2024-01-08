package generator

import java.io.File

/**
 * Finds all themes and creates an Intellij Idea run configuration for their generators
 * Should be run after creation/deletion of each theme
 */
fun main(args: Array<String>) {
    val userDir = System.getProperty("user.dir")!!
    val sourcesDirPath = "$userDir/multisrc/src/main/java/eu/kanade/tachiyomi/multisrc"
    val sourcesDir = File(sourcesDirPath)

    // cleanup from past runs
    File("$userDir/.run").apply {
        if (exists()) {
            deleteRecursively()
        }
        mkdirs()
    }

    // find all theme packages
    sourcesDir.list()!!
        .filter { File(sourcesDir, it).isDirectory }
        .forEach { themeSource ->
            // Find all XxxGenerator.kt files
            File("$sourcesDirPath/$themeSource").list()!!
                .filter { it.endsWith("Generator.kt") }
                .map { it.substringBefore(".kt") }
                .forEach { generatorClass ->
                    val file = File("$userDir/.run/$generatorClass.run.xml")
                    val intellijConfStr = """
                        <component name="ProjectRunConfigurationManager">
                          <configuration default="false" name="$generatorClass" type="JetRunConfigurationType" nameIsGenerated="true">
                            <module name="tachiyomi-extensions.multisrc.main" />
                            <option name="MAIN_CLASS_NAME" value="eu.kanade.tachiyomi.multisrc.$themeSource.$generatorClass" />
                            <method v="2">
                              <option name="Make" enabled="true" />
                              <option name="Gradle.BeforeRunTask" enabled="true" tasks="ktFormat" externalProjectPath="${'$'}PROJECT_DIR${'$'}/multisrc" vmOptions="" scriptParameters="-Ptheme=$themeSource" />
                              <option name="Gradle.BeforeRunTask" enabled="true" tasks="ktLint" externalProjectPath="${'$'}PROJECT_DIR${'$'}/multisrc" vmOptions="" scriptParameters="-Ptheme=$themeSource" />
                            </method>
                          </configuration>
                        </component>
                    """.trimIndent()
                    file.writeText(intellijConfStr)
                    file.appendText("\n")
                }
        }
}
