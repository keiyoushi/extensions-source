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
                        jar.putNextEntry(JarEntry(name))
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
                            jar.putNextEntry(JarEntry(entry.name))
                            source.getInputStream(entry).use { it.copyTo(jar) }
                            jar.closeEntry()
                        }
                }
            }
        }
    }
}
