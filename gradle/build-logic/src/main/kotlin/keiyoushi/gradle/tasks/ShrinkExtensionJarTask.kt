package keiyoushi.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

@CacheableTask
abstract class ShrinkExtensionJarTask : DefaultTask() {
    @get:Classpath
    abstract val inputJar: RegularFileProperty

    @get:Classpath
    abstract val libraryClasspath: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val keepRuleFiles: ConfigurableFileCollection

    @get:Input
    abstract val applicationId: Property<String>

    @get:Classpath
    abstract val r8Classpath: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @get:Inject
    abstract val execOps: ExecOperations

    @TaskAction
    fun shrink() {
        val out = outputJar.get().asFile
        out.parentFile.mkdirs()

        val keepRules = temporaryDir.resolve("keep.pro").apply {
            writeText("-keep class ${applicationId.get()}.ExtensionGenerated { <init>(); }\n")
        }

        val args = buildList {
            add("--classfile")
            add("--output"); add(out.absolutePath)
            libraryClasspath.files.forEach { add("--lib"); add(it.absolutePath) }
            keepRuleFiles.files.forEach { add("--pg-conf"); add(it.absolutePath) }
            add("--pg-conf"); add(keepRules.absolutePath)
            add(inputJar.get().asFile.absolutePath)
        }

        execOps.javaexec {
            classpath = r8Classpath
            mainClass.set("com.android.tools.r8.R8")
            setArgs(args)
        }
    }
}
