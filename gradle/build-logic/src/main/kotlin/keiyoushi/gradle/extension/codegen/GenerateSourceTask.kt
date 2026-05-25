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
        val needsSystemPrefs = source.baseUrl !is BaseUrlSpec.Static
        val implementConfigurable = source.isConfigurable || needsSystemPrefs

        return buildString {
            appendLine("package $pkg")
            appendLine()
            appendImports(this, listOf(source), needsConfigurable = implementConfigurable)
            appendLine()
            append("class $generatedClassName : $className()")
            if (implementConfigurable) append(", ConfigurableSource")
            appendLine(" {")
            appendSourceBody(this, source, indent = "    ")
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
        val anyNeedsConfigurable = sources.any { it.isConfigurable || it.baseUrl !is BaseUrlSpec.Static }

        return buildString {
            appendLine("package $pkg")
            appendLine()
            appendImports(this, sources, isFactory = true, needsConfigurable = anyNeedsConfigurable)
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
                val needsSystemPrefs = source.baseUrl !is BaseUrlSpec.Static
                val implementConfigurable = source.isConfigurable || needsSystemPrefs

                append("private class ${generatedClassName}_$suffix : $className()")
                if (implementConfigurable) append(", ConfigurableSource")
                appendLine(" {")
                appendSourceBody(this, source, indent = "    ")
                appendLine("}")
                appendLine()
            }
        }
    }

    private fun appendImports(
        builder: StringBuilder,
        sources: List<ResolvedSource>,
        isFactory: Boolean = false,
        needsConfigurable: Boolean = false,
    ) {
        val anyMirrors = sources.any { it.baseUrl is BaseUrlSpec.Mirrors }
        val anyCustom = sources.any { it.baseUrl is BaseUrlSpec.Custom }
        val anySystemPrefs = anyMirrors || anyCustom

        if (anySystemPrefs) {
            builder.appendLine("import android.content.SharedPreferences")
        }
        if (needsConfigurable) {
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
        if (anySystemPrefs) {
            builder.appendLine("import keiyoushi.utils.getPreferences")
        }
    }

    private fun appendSourceBody(
        builder: StringBuilder,
        source: ResolvedSource,
        indent: String,
    ) {
        val needsSystemPrefs = source.baseUrl !is BaseUrlSpec.Static
        builder.appendLine("${indent}override val name = \"${source.name}\"")
        builder.appendLine("${indent}override val lang = \"${source.lang}\"")
        builder.appendLine("${indent}override val id = ${source.id}L")
        builder.appendLine()

        if (needsSystemPrefs) {
            builder.appendLine("${indent}private val preferences: SharedPreferences = getPreferences()")
            builder.appendLine()
        }

        when (val baseUrl = source.baseUrl) {
            is BaseUrlSpec.Static -> {
                builder.appendLine("${indent}override val baseUrl = \"${baseUrl.url}\"")
                if (source.isConfigurable) {
                    builder.appendLine()
                    builder.appendLine("${indent}override fun setupPreferenceScreen(screen: PreferenceScreen) {")
                    builder.appendLine("${indent}    super.setupPreferenceScreen(screen)")
                    builder.appendLine("${indent}}")
                }
            }
            is BaseUrlSpec.Mirrors -> {
                builder.appendLine("${indent}private val mirrorPrefs = MirrorPreferences(")
                builder.appendLine("${indent}    preferences = preferences,")
                builder.appendLine("${indent}    mirrors = arrayOf(")
                baseUrl.urls.forEach { builder.appendLine("${indent}        \"$it\",") }
                builder.appendLine("${indent}    ),")
                builder.appendLine("${indent})")
                builder.appendLine()
                builder.appendLine("${indent}override val baseUrl: String")
                builder.appendLine("${indent}    get() = mirrorPrefs.baseUrl")
                builder.appendLine()
                builder.appendLine("${indent}override fun setupPreferenceScreen(screen: PreferenceScreen) {")
                builder.appendLine("${indent}    mirrorPrefs.setupPreferenceScreen(screen)")
                if (source.isConfigurable) {
                    builder.appendLine("${indent}    super.setupPreferenceScreen(screen)")
                }
                builder.appendLine("${indent}}")
            }
            is BaseUrlSpec.Custom -> {
                builder.appendLine("${indent}private val customUrlPrefs = CustomUrlPreferences(")
                builder.appendLine("${indent}    preferences = preferences,")
                builder.appendLine("${indent}    defaultUrl = \"${baseUrl.defaultUrl}\",")
                builder.appendLine("${indent})")
                builder.appendLine()
                builder.appendLine("${indent}override val baseUrl: String")
                builder.appendLine("${indent}    get() = customUrlPrefs.baseUrl")
                builder.appendLine()
                builder.appendLine("${indent}override fun setupPreferenceScreen(screen: PreferenceScreen) {")
                builder.appendLine("${indent}    customUrlPrefs.setupPreferenceScreen(screen)")
                if (source.isConfigurable) {
                    builder.appendLine("${indent}    super.setupPreferenceScreen(screen)")
                }
                builder.appendLine("${indent}}")
            }
        }

        source.overrides.forEach { (name, value) ->
            val v = when (value) {
                is OverrideValue.IntV -> "${value.v}"
                is OverrideValue.LongV -> "${value.v}L"
                is OverrideValue.BoolV -> "${value.v}"
                is OverrideValue.Str -> "\"${value.v}\""
            }
            builder.appendLine("${indent}override val $name = $v")
        }
    }

    private fun sourceSuffixes(sources: List<ResolvedSource>): List<String> {
        val seen = mutableMapOf<String, Int>()
        return sources.map { src ->
            val base = src.lang.replace('-', '_').replace('.', '_')
            val count = seen.merge(base, 1) { existing, _ -> existing + 1 }!!
            if (count == 1) base else "${base}_$count"
        }
    }
}
