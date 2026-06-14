package keiyoushi.gradle.extension.tasks

import keiyoushi.gradle.extension.codegen.ResolvedExtension
import keiyoushi.gradle.extension.codegen.ResolvedSource
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
            appendSourceBody(this, source, source.deeplinks.isNotEmpty())
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
        val hasAnyDeeplinks = sources.any { it.deeplinks.isNotEmpty() }

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
                appendSourceBody(this, source, hasAnyDeeplinks)
                appendLine("}")
                appendLine()
            }
        }
    }

    private fun buildConstructorArgs(source: ResolvedSource): String = source.overrides.map { (name, value) ->
        val v = when (value) {
            is OverrideValue.IntV -> "${value.v}"
            is OverrideValue.LongV -> "${value.v}L"
            is OverrideValue.BoolV -> "${value.v}"
            is OverrideValue.Str -> "\"${value.v}\""
        }
        "$name = $v"
    }.joinToString(",\n    ")

    private fun appendImports(
        builder: StringBuilder,
        sources: List<ResolvedSource>,
        isFactory: Boolean = false,
        needsPrefsImport: Boolean = false,
    ) {
        val anyMirrors = sources.any { it.baseUrl is BaseUrlSpec.Mirrors }
        val anyCustom = sources.any { it.baseUrl is BaseUrlSpec.Custom }
        val anyDeeplinks = sources.any { it.deeplinks.isNotEmpty() }

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
        if (anyDeeplinks) {
            builder.appendLine("import eu.kanade.tachiyomi.source.model.FilterList")
            builder.appendLine("import eu.kanade.tachiyomi.source.model.MangasPage")
            builder.appendLine("import rx.Observable")
            builder.appendLine("import okhttp3.HttpUrl.Companion.toHttpUrlOrNull")
        }
    }

    private fun appendSourceBody(
        builder: StringBuilder,
        source: ResolvedSource,
        hasAnyDeeplinks: Boolean,
    ) {
        val needsPrefs = source.baseUrl !is BaseUrlSpec.Static
        builder.appendLine("    override val name = \"${source.name}\"")
        builder.appendLine("    override val lang = \"${source.lang}\"")
        builder.appendLine("    override val id = ${source.id}L")
        builder.appendLine()

        if (needsPrefs) {
            builder.appendLine("    private val preferences: SharedPreferences = getPreferences()")
            builder.appendLine()
        }

        when (val baseUrl = source.baseUrl) {
            is BaseUrlSpec.Static -> {
                builder.appendLine("    override val baseUrl = \"${baseUrl.url}\"")
            }
            is BaseUrlSpec.Mirrors -> {
                builder.appendLine("    private val mirrorPrefs = MirrorPreferences(")
                builder.appendLine("        preferences = preferences,")
                builder.appendLine("        mirrors = arrayOf(")
                baseUrl.mirrors.forEach { url ->
                    builder.appendLine("            \"$url\",")
                }
                builder.appendLine("        ),")
                if (baseUrl.prefKey.isNotEmpty()) {
                    builder.appendLine("        prefKey = \"${baseUrl.prefKey}\",")
                }
                builder.appendLine("    )")
                builder.appendLine()
                builder.appendLine("    override val baseUrl: String")
                builder.appendLine("        get() = mirrorPrefs.baseUrl")
                builder.appendLine()
                builder.appendLine("    override fun setupPreferenceScreen(screen: PreferenceScreen) {")
                builder.appendLine("        mirrorPrefs.setupPreferenceScreen(screen)")
                if (source.isConfigurable) {
                    builder.appendLine("        super.setupPreferenceScreen(screen)")
                }
                builder.appendLine("    }")
            }
            is BaseUrlSpec.Custom -> {
                builder.appendLine("    private val customUrlPrefs = CustomUrlPreferences(")
                builder.appendLine("        preferences = preferences,")
                builder.appendLine("        defaultUrl = \"${baseUrl.defaultUrl}\",")
                if (baseUrl.prefKey.isNotEmpty()) {
                    builder.appendLine("        prefBaseKey = \"${baseUrl.prefKey}\",")
                }
                builder.appendLine("    )")
                builder.appendLine()
                builder.appendLine("    override val baseUrl: String")
                builder.appendLine("        get() = customUrlPrefs.baseUrl")
                builder.appendLine()
                builder.appendLine("    override fun setupPreferenceScreen(screen: PreferenceScreen) {")
                builder.appendLine("        customUrlPrefs.setupPreferenceScreen(screen)")
                if (source.isConfigurable) {
                    builder.appendLine("        super.setupPreferenceScreen(screen)")
                }
                builder.appendLine("    }")
            }
        }

        if (source.deeplinks.isNotEmpty()) {
            builder.appendLine()
            builder.appendLine("    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {")
            builder.appendLine("        val httpUrl = query.toHttpUrlOrNull()")
            builder.appendLine("        if (httpUrl == null) {")
            builder.appendLine("            return super.fetchSearchManga(page, query, filters)")
            builder.appendLine("        }")
            builder.appendLine()

            val groupedDeeplinks = source.deeplinks.groupBy { it.host }
            builder.appendLine("        val matches = when (httpUrl.host) {")
            groupedDeeplinks.forEach { (host, filtersForHost) ->
                builder.appendLine("            \"$host\" -> {")
                builder.appendLine("                val path = httpUrl.encodedPath")
                val conditions = filtersForHost.flatMap { it.pathPatterns }.map { pattern ->
                    val regexStr = androidPatternToRegexStr(pattern)
                    "path.matches(Regex(\"\"\"$regexStr\"\"\"))"
                }
                builder.appendLine("                ${conditions.joinToString(" || ")}")
                builder.appendLine("            }")
            }
            builder.appendLine("            else -> false")
            builder.appendLine("        }")
            builder.appendLine()
            builder.appendLine("        return if (matches) {")
            builder.appendLine("            super.fetchSearchManga(page, query, filters)")
            builder.appendLine("        } else {")
            builder.appendLine("            Observable.just(MangasPage(emptyList(), false))")
            builder.appendLine("        }")
            builder.appendLine("    }")
        } else if (hasAnyDeeplinks) {
            builder.appendLine()
            builder.appendLine("    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {")
            builder.appendLine("        if (query.toHttpUrlOrNull() != null) {")
            builder.appendLine("            return Observable.just(MangasPage(emptyList(), false))")
            builder.appendLine("        }")
            builder.appendLine("        return super.fetchSearchManga(page, query, filters)")
            builder.appendLine("    }")
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

    private fun androidPatternToRegexStr(pattern: String): String {
        val regexStr = buildString {
            append("^")
            var i = 0
            while (i < pattern.length) {
                when (val c = pattern[i]) {
                    '.' -> {
                        append('.')
                        i++
                    }
                    '*' -> {
                        append('*')
                        i++
                    }
                    '\\' -> {
                        if (i + 1 < pattern.length) {
                            val next = pattern[i + 1]
                            append(escapeRegexChar(next))
                            i += 2
                        } else {
                            append(escapeRegexChar('\\'))
                            i++
                        }
                    }
                    else -> {
                        append(escapeRegexChar(c))
                        i++
                    }
                }
            }
            append("$")
        }
        return regexStr
    }

    private fun escapeRegexChar(c: Char): String = when (c) {
        '\\', '^', '$', '.', '|', '?', '*', '+', '(', ')', '[', ']', '{', '}' -> "\\$c"
        else -> c.toString()
    }
}
