package keiyoushi.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
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

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val keepRuleFiles: ConfigurableFileCollection

    @get:Input
    abstract val applicationId: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifestFile: RegularFileProperty

    @get:Classpath
    abstract val r8Classpath: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @get:Inject
    abstract val execOps: ExecOperations

    @TaskAction
    fun create() {
        val out = outputJar.get().asFile
        out.parentFile.mkdirs()

        val keepRules = temporaryDir.resolve("keep.pro").apply {
            writeText("-keep class ${applicationId.get()}.ExtensionGenerated { <init>(); }\n")
        }

        val program = temporaryDir.resolve("program.jar")
        val written = HashSet<String>()
        JarOutputStream(program.outputStream().buffered()).use { jar ->
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

        val shrunk = temporaryDir.resolve("shrunk.jar")
        val args = buildList {
            add("--classfile")
            add("--output"); add(shrunk.absolutePath)
            libraryClasspath.files.forEach { add("--lib"); add(it.absolutePath) }
            keepRuleFiles.files.forEach { add("--pg-conf"); add(it.absolutePath) }
            add("--pg-conf"); add(keepRules.absolutePath)
            add(program.absolutePath)
        }

        execOps.javaexec {
            classpath = r8Classpath
            mainClass.set("com.android.tools.r8.R8")
            setArgs(args)
        }

        JarOutputStream(out.outputStream().buffered()).use { jar ->
            jar.putNextEntry(fixedTimeEntry("AndroidManifest.xml"))
            manifestFile.get().asFile.inputStream().use { it.copyTo(jar) }
            jar.closeEntry()
            JarFile(shrunk).use { source ->
                source.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                    jar.putNextEntry(fixedTimeEntry(entry.name))
                    source.getInputStream(entry).use { it.copyTo(jar) }
                    jar.closeEntry()
                }
            }
        }
    }
}

// Fixed entry time for reproducible jars (1980-02-01, the value Gradle uses for archives).
private const val FIXED_JAR_TIME = 320054400000L

internal fun fixedTimeEntry(name: String): JarEntry = JarEntry(name).apply { time = FIXED_JAR_TIME }
