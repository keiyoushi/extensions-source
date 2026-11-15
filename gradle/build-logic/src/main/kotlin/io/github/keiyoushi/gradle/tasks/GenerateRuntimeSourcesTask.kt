package io.github.keiyoushi.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Kotlin glue for the Keiyoushi Runtime.
 *
 * A consumer gets `RuntimeModules` in [packageName], one `RuntimeModule` per [requires] entry.
 * The runtime extension itself ([hostVersionCode] set) gets `keiyoushi.source.Generated`, a
 * `SourceFactory` that registers [provides] when the app loads it. Its one source only holds
 * settings, which show the [licenses].
 */
@CacheableTask
abstract class GenerateRuntimeSourcesTask : DefaultTask() {
    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val requires: MapProperty<String, Int>

    @get:Input
    abstract val provides: MapProperty<String, Int>

    @get:Input
    abstract val minApis: MapProperty<String, Int>

    @get:Input
    @get:Optional
    abstract val hostVersionCode: Property<Int>

    @get:Input
    @get:Optional
    abstract val hostVersionName: Property<String>

    @get:Input
    @get:Optional
    abstract val hostSourceId: Property<Long>

    @get:Input
    @get:Optional
    abstract val hostSourceName: Property<String>

    /** One license per bundled library, `assets/licenses/<library>.txt` */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val licenses: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val root = outputDir.get().asFile
        root.deleteRecursively()

        val requires = requires.get().toSortedMap()
        if (requires.isNotEmpty()) {
            val pkg = packageName.get()
            val properties = requires.entries.joinToString("\n") { (module, api) ->
                "    val $module = RuntimeModule(\"$module\", api = $api)"
            }
            root.write(
                pkg,
                "RuntimeModules",
                """
                |$HEADER
                |package $pkg
                |
                |import keiyoushi.lib.runtime.RuntimeModule
                |
                |/** The Keiyoushi Runtime modules declared in `keiyoushiRuntime { }` */
                |internal object RuntimeModules {
                |$properties
                |}
                |
                """.trimMargin(),
            )
        }

        val versionCode = hostVersionCode.orNull ?: return
        val provides = provides.get().toSortedMap()
        val apis = provides.entries.joinToString("") { (module, api) -> "\n|            \"$module\" to $api," }
        val minApis = minApis.get().toSortedMap().entries.joinToString("") { (module, api) -> "\n|            \"$module\" to $api," }
        val entries = provides.keys.joinToString("") { module ->
            "\n|            \"$module\" to keiyoushi.runtime.$module.Entry,"
        }
        val licenses = licenses.files.map { it.name }.sorted().joinToString("") { file ->
            "\n|                \"${file.substringBeforeLast('.')}\" to \"assets/licenses/$file\","
        }
        val versionName = hostVersionName.get()
        root.write(
            "keiyoushi.source",
            "Generated",
            """
            |$HEADER
            |package keiyoushi.source
            |
            |import eu.kanade.tachiyomi.source.Source
            |import eu.kanade.tachiyomi.source.SourceFactory
            |import keiyoushi.lib.runtime.RuntimeHost
            |import keiyoushi.lib.runtime.RuntimeSource
            |
            |/** Loading it makes this runtime's modules available to other extensions */
            |internal class Generated : SourceFactory {
            |    init {
            |        RuntimeHost.register(
            |            versionCode = $versionCode,
            |            versionName = "$versionName",
            |            apis = mapOf<String, Int>($apis
            |            ),
            |            minApis = mapOf<String, Int>($minApis
            |            ),
            |            entries = mapOf<String, Any>($entries
            |            ),
            |        )
            |    }
            |
            |    override fun createSources(): List<Source> = listOf(
            |        RuntimeSource(
            |            id = ${hostSourceId.get()}L,
            |            name = "${hostSourceName.get()}",
            |            versionName = "$versionName",
            |            apis = mapOf<String, Int>($apis
            |            ),
            |            licenses = mapOf<String, String>($licenses
            |            ),
            |        ),
            |    )
            |}
            |
            """.trimMargin(),
        )
    }

    private fun java.io.File.write(pkg: String, name: String, content: String) {
        val file = resolve(pkg.replace('.', '/')).resolve("$name.kt")
        file.parentFile.mkdirs()
        file.writeText(content)
    }
}

private const val HEADER = "// Generated by the keiyoushi build from build.gradle.kts. Do not edit."
