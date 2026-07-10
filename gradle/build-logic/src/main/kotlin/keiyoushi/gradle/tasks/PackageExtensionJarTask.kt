package keiyoushi.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

@CacheableTask
abstract class PackageExtensionJarTask : DefaultTask() {
    @get:Classpath
    abstract val jars: ListProperty<RegularFile>

    @get:Classpath
    abstract val dirs: ListProperty<Directory>

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun pack() {
        val out = outputJar.get().asFile
        out.parentFile.mkdirs()

        val written = HashSet<String>()
        JarOutputStream(out.outputStream().buffered()).use { jar ->
            dirs.get().forEach { dir ->
                val root = dir.asFile
                root.walkTopDown().filter { it.isFile }.forEach { file ->
                    val name = file.relativeTo(root).invariantSeparatorsPath
                    if (written.add(name)) {
                        jar.putNextEntry(fixedTimeEntry(name))
                        file.inputStream().use { it.copyTo(jar) }
                        jar.closeEntry()
                    }
                }
            }
            jars.get().forEach { regularFile ->
                JarFile(regularFile.asFile).use { source ->
                    source.entries().asSequence()
                        .filter { !it.isDirectory && written.add(it.name) }
                        .forEach { entry ->
                            jar.putNextEntry(fixedTimeEntry(entry.name))
                            source.getInputStream(entry).use { it.copyTo(jar) }
                            jar.closeEntry()
                        }
                }
            }
        }
    }
}

// Fixed entry time for reproducible jars (1980-02-01, the value Gradle uses for archives).
private const val FIXED_JAR_TIME = 320054400000L

internal fun fixedTimeEntry(name: String): JarEntry = JarEntry(name).apply { time = FIXED_JAR_TIME }
