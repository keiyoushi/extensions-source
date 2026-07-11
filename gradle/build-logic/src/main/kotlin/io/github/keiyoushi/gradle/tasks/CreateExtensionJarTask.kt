package io.github.keiyoushi.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import javax.inject.Inject

@CacheableTask
abstract class CreateExtensionJarTask : DefaultTask() {
    @get:Classpath
    abstract val jars: ListProperty<RegularFile>

    @get:Classpath
    abstract val dirs: ListProperty<Directory>

    @get:Classpath
    abstract val libraryClasspath: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val proguardConfigFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifestFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val apkDir: DirectoryProperty

    @get:Classpath
    abstract val proguardClasspath: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @get:Inject
    abstract val execOps: ExecOperations

    @TaskAction
    fun create() {
        val out = outputJar.get().asFile
        out.parentFile.mkdirs()

        val libraryEntries = HashSet<String>()
        libraryClasspath.files.filter { it.isFile }.forEach { file ->
            runCatching {
                JarFile(file).use { jf ->
                    jf.entries().asSequence().forEach { libraryEntries.add(it.name) }
                }
            }.onFailure { logger.warn("Could not read library jar ${file.name}: ${it.message}") }
        }

        val program = temporaryDir.resolve("program.jar")
        val written = HashSet<String>()
        var excludedClassCount = 0
        fun logExcludedClass(name: String) {
            if (name.endsWith(".class")) {
                excludedClassCount++
                logger.info("Excluding $name from jar: also present on the library classpath")
            }
        }

        JarOutputStream(program.outputStream().buffered()).use { jar ->
            dirs.get().forEach { dir ->
                val root = dir.asFile
                root.walkTopDown().filter { it.isFile }
                    .map { it to it.relativeTo(root).invariantSeparatorsPath }
                    .sortedBy { (_, name) -> name }
                    .forEach { (file, name) ->
                        if (name in libraryEntries) {
                            logExcludedClass(name)
                        } else if (written.add(name)) {
                            jar.putNextEntry(fixedTimeEntry(name))
                            file.inputStream().use { it.copyTo(jar) }
                            jar.closeEntry()
                        }
                    }
            }

            jars.get().forEach { regularFile ->
                JarFile(regularFile.asFile).use { source ->
                    source.entries().asSequence()
                        .filter { !it.isDirectory }
                        .sortedBy { it.name }
                        .onEach { if (it.name in libraryEntries) logExcludedClass(it.name) }
                        .filter { it.name !in libraryEntries && written.add(it.name) }
                        .forEach { entry ->
                            jar.putNextEntry(fixedTimeEntry(entry.name))
                            source.getInputStream(entry).use { it.copyTo(jar) }
                            jar.closeEntry()
                        }
                }
            }
        }

        if (excludedClassCount > 0) {
            logger.lifecycle(
                "$path: excluded $excludedClassCount class(es) also present on the library classpath (rerun with --info for the full list)",
            )
        }

        val shrunk = temporaryDir.resolve("shrunk.jar")

        val modifiedProguardConfigFile = temporaryDir.resolve("sanitized-proguard-configuration.txt").apply {
            val sanitizedRules = proguardConfigFile.get().asFile
                .readLines().toMutableList()

            // make it forgiving like r8
            sanitizedRules.add("-dontwarn **")
            // don't print the aforementioned warnings
            sanitizedRules.add("-dontnote **")

            writeText(sanitizedRules.joinToString("\n"))
        }

        val args = buildList {
            add("-injars")
            add(program.absolutePath)
            add("-outjars")
            add(shrunk.absolutePath)
            libraryClasspath.files.forEach {
                add("-libraryjars")
                add(it.absolutePath)
            }
            add("-include")
            add(modifiedProguardConfigFile.absolutePath)
        }

        execOps.javaexec {
            classpath = proguardClasspath
            mainClass.set("proguard.ProGuard")
            setArgs(args)
        }

        val apk = apkDir.get().asFile.walkTopDown().first { it.extension == "apk" }

        JarOutputStream(out.outputStream().buffered()).use { jar ->
            jar.putNextEntry(fixedTimeEntry("AndroidManifest.xml"))
            manifestFile.get().asFile.inputStream().use { it.copyTo(jar) }
            jar.closeEntry()

            JarFile(shrunk).use { source ->
                source.entries().asSequence().filter { !it.isDirectory }.sortedBy { it.name }.forEach { entry ->
                    jar.putNextEntry(fixedTimeEntry(entry.name))
                    source.getInputStream(entry).use { it.copyTo(jar) }
                    jar.closeEntry()
                }
            }

            JarFile(apk).use { source ->
                source.entries().asSequence()
                    .filter { !it.isDirectory && (it.name.startsWith("res/") || it.name.startsWith("assets/") || it.name == "resources.arsc") }
                    .sortedBy { it.name }
                    .forEach { entry ->
                        jar.putNextEntry(fixedTimeEntry(entry.name))
                        source.getInputStream(entry).use { it.copyTo(jar) }
                        jar.closeEntry()
                    }
            }
        }
    }
}

// keiyoushi's birthday:  Jan 6, 2024
private const val FIXED_JAR_TIME = 1704499200000L
internal fun fixedTimeEntry(name: String): JarEntry = JarEntry(name).apply { time = FIXED_JAR_TIME }
