package keiyoushi.gradle.extension.codegen

import keiyoushi.gradle.extension.dsl.BaseUrlSpec
import keiyoushi.gradle.extension.dsl.OverrideValue
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GenerateSourceTask : DefaultTask() {
    @get:Input
    abstract val resolvedExtension: Property<ResolvedExtension>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val ext = resolvedExtension.get()
        val pkg = ext.pkg
        val className = ext.className
        val generatedClassName = "${className}Generated"
        val sources = ext.sources

        val fileContent = if (sources.size == 1) {
            generateSingleSource(pkg, className, generatedClassName, sources.single())
        } else {
            generateSourceFactory(pkg, className, generatedClassName, sources)
        }

        val packageDir = outputDir.get().asFile.resolve(pkg.replace('.', '/'))
        packageDir.mkdirs()
        packageDir.resolve("$generatedClassName.kt").writeText(fileContent)
    }

    private fun generateSingleSource(
        pkg: String,
        className: String,
        generatedClassName: String,
        source: ResolvedSource,
    ): String {
        val constructorArgs = buildConstructorArgs(source)

        return buildString {
            appendLine("package $pkg")
            appendLine()
            appendImports(this, listOf(source), needsPrefsImport = source.baseUrl !is BaseUrlSpec.Static)
            appendLine()
            append("class $generatedClassName")
            if (constructorArgs.isEmpty()) {
                append(" : $className()")
            } else {
                appendLine(" : $className(")
                appendLine("    $constructorArgs")
                append(")")
            }
            if (source.baseUrl !is BaseUrlSpec.Static) append(", ConfigurableSource")
            appendLine(" {")
            appendSourceBody(this, source)
            appendLine("}")
        }
    }

    private fun generateSourceFactory(
        pkg: String,
        className: String,
        generatedClassName: String,
        sources: List<ResolvedSource>,
    ): String {
        val suffixes = sourceSuffixes(sources)
        val needsImports = sources.any { it.baseUrl !is BaseUrlSpec.Static }

        return buildString {
            appendLine("package $pkg")
            appendLine()
            appendImports(this, sources, isFactory = true, needsPrefsImport = needsImports)
            appendLine()
            appendLine("class $generatedClassName : SourceFactory {")
            appendLine("    override fun createSources(): List<Source> = listOf(")
            suffixes.forEach { suffix ->
                appendLine("        ${generatedClassName}_$suffix(),")
            }
            appendLine("    )")
            appendLine("}")
            appendLine()

            sources.forEachIndexed { index, source ->
                val suffix = suffixes[index]
                val constructorArgs = buildConstructorArgs(source)

                append("private class ${generatedClassName}_$suffix")
                if (constructorArgs.isEmpty()) {
                    append(" : $className()")
                } else {
                    appendLine(" : $className(")
                    appendLine("    $constructorArgs")
                    append(")")
                }
                if (source.baseUrl !is BaseUrlSpec.Static) append(", ConfigurableSource")
                appendLine(" {")
                appendSourceBody(this, source)
                appendLine("}")
                appendLine()
            }
        }
    }

    private fun buildConstructorArgs(source: ResolvedSource): String {
        return source.overrides.map { (name, value) ->
            val v = when (value) {
                is OverrideValue.IntV -> "${value.v}"
                is OverrideValue.LongV -> "${value.v}L"
                is OverrideValue.BoolV -> "${value.v}"
                is OverrideValue.Str -> "\"${value.v}\""
            }
            "$name = $v"
        }.joinToString(",\n    ")
    }

    private fun appendImports(
        builder: StringBuilder,
        sources: List<ResolvedSource>,
        isFactory: Boolean = false,
        needsPrefsImport: Boolean = false,
    ) {
        val anyMirrors = sources.any { it.baseUrl is BaseUrlSpec.Mirrors }
        val anyCustom = sources.any { it.baseUrl is BaseUrlSpec.Custom }

        if (needsPrefsImport) {
            builder.appendLine("import android.content.SharedPreferences")
            builder.appendLine("import androidx.preference.PreferenceScreen")
            builder.appendLine("import eu.kanade.tachiyomi.source.ConfigurableSource")
        }
        if (isFactory) {
            builder.appendLine("import eu.kanade.tachiyomi.source.Source")
            builder.appendLine("import eu.kanade.tachiyomi.source.SourceFactory")
        }
        if (anyCustom) {
            builder.appendLine("import keiyoushi.source.CustomUrlPreferences")
        }
        if (anyMirrors) {
            builder.appendLine("import keiyoushi.source.MirrorPreferences")
        }
        if (anyMirrors || anyCustom) {
            builder.appendLine("import keiyoushi.utils.getPreferences")
        }
    }

    private fun appendSourceBody(
        builder: StringBuilder,
        source: ResolvedSource,
    ) {
        val needsPrefs = source.baseUrl !is BaseUrlSpec.Static
        builder.appendLine("override val name = \"${source.name}\"")
        builder.appendLine("override val lang = \"${source.lang}\"")
        builder.appendLine("override val id = ${source.id}L")
        builder.appendLine()

        if (needsPrefs) {
            builder.appendLine("private val preferences: SharedPreferences = getPreferences()")
            builder.appendLine()
        }

        when (val baseUrl = source.baseUrl) {
            is BaseUrlSpec.Static -> {
                builder.appendLine("override val baseUrl = \"${baseUrl.url}\"")
            }
            is BaseUrlSpec.Mirrors -> {
                builder.appendLine("private val mirrorPrefs = MirrorPreferences(")
                builder.appendLine("    preferences = preferences,")
                builder.appendLine("    mirrors = arrayOf(")
                baseUrl.mirrors.forEach { mirror ->
                    builder.appendLine("        \"${mirror.label}\" to \"${mirror.url}\",")
                }
                builder.appendLine("    ),")
                builder.appendLine(")")
                builder.appendLine()
                builder.appendLine("override val baseUrl: String")
                builder.appendLine("    get() = mirrorPrefs.baseUrl")
                builder.appendLine()
                builder.appendLine("override fun setupPreferenceScreen(screen: PreferenceScreen) {")
                builder.appendLine("    mirrorPrefs.setupPreferenceScreen(screen)")
                if (source.isConfigurable) {
                    builder.appendLine("    super.setupPreferenceScreen(screen)")
                }
                builder.appendLine("}")
            }
            is BaseUrlSpec.Custom -> {
                builder.appendLine("private val customUrlPrefs = CustomUrlPreferences(")
                builder.appendLine("    preferences = preferences,")
                builder.appendLine("    defaultUrl = \"${baseUrl.defaultUrl}\",")
                builder.appendLine(")")
                builder.appendLine()
                builder.appendLine("override val baseUrl: String")
                builder.appendLine("    get() = customUrlPrefs.baseUrl")
                builder.appendLine()
                builder.appendLine("override fun setupPreferenceScreen(screen: PreferenceScreen) {")
                builder.appendLine("    customUrlPrefs.setupPreferenceScreen(screen)")
                if (source.isConfigurable) {
                    builder.appendLine("    super.setupPreferenceScreen(screen)")
                }
                builder.appendLine("}")
            }
        }
    }

    private fun sourceSuffixes(sources: List<ResolvedSource>): List<String> {
        val seen = mutableMapOf<String, Int>()
        return sources.map { src ->
            val base = src.lang.replace('-', '_').replace('.', '_')
            val count = seen.getOrDefault(base, 0) + 1
            seen[base] = count
            if (count == 1) base else "${base}_$count"
        }
    }
}
