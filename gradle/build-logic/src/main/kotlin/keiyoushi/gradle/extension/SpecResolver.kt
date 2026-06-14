package keiyoushi.gradle.extension

import keiyoushi.gradle.extension.codegen.ResolvedExtension
import keiyoushi.gradle.extension.codegen.ResolvedSource
import keiyoushi.gradle.extension.codegen.generateSourceId
import keiyoushi.gradle.extension.dsl.BaseUrlSpec
import keiyoushi.gradle.extension.dsl.DeeplinkSpec
import keiyoushi.gradle.extension.dsl.ExtensionSpec
import keiyoushi.gradle.extension.dsl.MultisrcSpec
import keiyoushi.gradle.extension.tasks.DeeplinkFilter
import keiyoushi.gradle.utils.assertWithoutFlag
import org.gradle.api.Project
import java.net.URI

data class ResolvedSpec(
    val extension: ResolvedExtension,
    val effectiveVersionCode: Int,
    val effectiveVersionName: String,
    val deeplinkFilters: List<DeeplinkFilter>,
)

fun Project.resolveExtensionSpec(spec: ExtensionSpec, pkg: String): ResolvedSpec {
    val extName = validateMetadata(spec)
    val className = spec.className.get()
    val sources = validateSources(spec)

    val themeProject = spec.theme.orNull?.let { findThemeProject(it) }
    val multisrcSpec = themeProject?.extensions?.findByType(MultisrcSpec::class.java)

    val themeSpecs = multisrcSpec?.deeplinks?.orNull.orEmpty()
    val resolvedSources = sources.map { it.resolve(themeSpecs) }
    val deeplinkFilters = resolvedSources.flatMap { it.deeplinks }.distinct()

    val isNsfw = spec.nsfw.getOrElse(false)

    val themeBaseVersion = multisrcSpec?.baseVersionCode?.getOrElse(0) ?: 0
    val effectiveVersionCode = themeBaseVersion + spec.versionCode.get()
    val effectiveVersionName = "1.4.$effectiveVersionCode"

    val resolvedExtension = ResolvedExtension(
        name = extName,
        pkg = pkg,
        className = className,
        sources = resolvedSources,
        isNsfw = isNsfw,
    )

    return ResolvedSpec(resolvedExtension, effectiveVersionCode, effectiveVersionName, deeplinkFilters)
}

private fun validateMetadata(spec: ExtensionSpec): String {
    assertWithoutFlag(spec.name.isPresent) { "keiyoushi { name = ... } is required" }
    assertWithoutFlag(spec.className.isPresent) { "keiyoushi { className = ... } is required" }
    assertWithoutFlag(spec.versionCode.isPresent) { "keiyoushi { versionCode = ... } is required" }
    val extName = spec.name.get()
    assertWithoutFlag(extName.isNotEmpty()) { "keiyoushi.name must not be empty" }
    assertWithoutFlag(extName.all { it.code < 0x180 }) { "Extension name should be romanized" }

    val className = spec.className.get()
    assertWithoutFlag(className.isNotEmpty()) { "keiyoushi.className must not be empty" }
    assertWithoutFlag(className.first().isUpperCase()) { "keiyoushi.className must be PascalCase (got '$className')" }

    return extName
}

private fun validateSources(spec: ExtensionSpec): List<keiyoushi.gradle.extension.dsl.SourceSpec> {
    val sources = spec.sources.orNull.orEmpty()
    assertWithoutFlag(sources.isNotEmpty()) { "At least one source { } block is required" }
    val seenIdKey = mutableSetOf<Any>()
    sources.forEach { src ->
        assertWithoutFlag(src.name.isPresent) { "source { name = ... } is required" }
        assertWithoutFlag(src.lang.isPresent) { "source { lang = ... } is required" }
        assertWithoutFlag(src.resolvedBaseUrl.isPresent) { "source { baseUrl(...) } is required (for '${src.name.orNull}')" }
        val key: Any = if (src.id.isPresent) {
            src.id.get()
        } else {
            Triple(src.name.get(), src.lang.get(), src.versionId.orElse(1).get())
        }
        assertWithoutFlag(seenIdKey.add(key)) {
            val name = src.name.get()
            val lang = src.lang.get()
            "Sources with (name, lang) = ('$name', '$lang') would produce duplicate IDs. " +
                "Set an explicit id = ... or change versionId so IDs differ"
        }
    }
    return sources
}

private fun keiyoushi.gradle.extension.dsl.SourceSpec.resolve(themeSpecs: List<DeeplinkSpec>): ResolvedSource {
    val baseUrl = resolvedBaseUrl.get()
    val versionId = versionId.orElse(1).get()
    val effectiveId = if (id.isPresent) id.get() else generateSourceId(name.get(), lang.get(), versionId)

    val specs = specs.orNull.orEmpty() + themeSpecs
    val sourceDeeplinks = specs.flatMap { spec ->
        val hosts = spec.hosts.orNull.orEmpty().ifEmpty { hostsFrom(baseUrl) }
        val paths = spec.pathPatterns.orNull.orEmpty()
        if (paths.isNotEmpty()) {
            hosts.map { host -> DeeplinkFilter(host, paths) }
        } else {
            emptyList()
        }
    }.distinct()

    return ResolvedSource(
        name = name.get(),
        lang = lang.get(),
        isConfigurable = configurable.getOrElse(false),
        versionId = versionId,
        id = effectiveId,
        baseUrl = baseUrl,
        overrides = overrides.orNull.orEmpty(),
        deeplinks = sourceDeeplinks,
    )
}

private fun hostsFrom(baseUrl: BaseUrlSpec): List<String> {
    val urls = when (baseUrl) {
        is BaseUrlSpec.Static -> listOf(baseUrl.url)
        is BaseUrlSpec.Mirrors -> baseUrl.mirrors
        is BaseUrlSpec.Custom -> listOf(baseUrl.defaultUrl)
    }
    return urls.mapNotNull { url ->
        runCatching { URI(url).host }.getOrNull()
    }
}

private fun Project.findThemeProject(themeName: String): Project {
    val themePath = ":lib-multisrc:$themeName"
    evaluationDependsOn(themePath)
    return project(themePath)
}
