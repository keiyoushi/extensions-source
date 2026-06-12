package keiyoushi.gradle.extension

import keiyoushi.gradle.extension.codegen.DeeplinkFilter
import keiyoushi.gradle.extension.codegen.ResolvedExtension
import keiyoushi.gradle.extension.codegen.ResolvedSource
import keiyoushi.gradle.extension.codegen.generateSourceId
import keiyoushi.gradle.extension.dsl.BaseUrlSpec
import keiyoushi.gradle.extension.dsl.DeeplinkSpec
import keiyoushi.gradle.extension.dsl.ExtensionSpec
import keiyoushi.gradle.extension.dsl.MultisrcSpec
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
    val themePaths = multisrcSpec
        ?.deeplinks?.orNull?.flatMap { it.pathPatterns.orNull.orEmpty() }.orEmpty()

    val resolvedSources = sources.map { it.resolve() }
    val deeplinkFilters = resolveDeeplinkFilters(sources, themePaths)

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

private fun keiyoushi.gradle.extension.dsl.SourceSpec.resolve(): ResolvedSource {
    val baseUrl = resolvedBaseUrl.get()
    val versionId = versionId.orElse(1).get()
    val effectiveId = if (id.isPresent) id.get() else generateSourceId(name.get(), lang.get(), versionId)

    return ResolvedSource(
        name = name.get(),
        lang = lang.get(),
        isConfigurable = configurable.getOrElse(false),
        versionId = versionId,
        id = effectiveId,
        baseUrl = baseUrl,
        overrides = overrides.orNull.orEmpty(),
    )
}

private fun resolveDeeplinkFilters(
    sources: List<keiyoushi.gradle.extension.dsl.SourceSpec>,
    themePaths: List<String>,
): List<DeeplinkFilter> {
    val combinedPaths = themePaths.distinct()
    val result = mutableListOf<DeeplinkFilter>()

    sources.forEach { source ->
        val baseUrl = source.resolvedBaseUrl.get()
        val specs = source.specs.orNull.orEmpty()

        if (specs.isEmpty()) {
            if (combinedPaths.isNotEmpty()) {
                parseUrls(baseUrl).forEach { (scheme, host) ->
                    if (scheme != null && host != null) {
                        result.add(DeeplinkFilter(scheme, host, combinedPaths))
                    }
                }
            }
        } else {
            specs.forEach { spec ->
                val explicitScheme = spec.scheme.orNull
                val explicitHost = spec.host.orNull
                val paths = (spec.pathPatterns.orNull.orEmpty() + combinedPaths).distinct()

                if (explicitScheme != null && explicitHost != null) {
                    result.add(DeeplinkFilter(explicitScheme, explicitHost, paths))
                } else {
                    parseUrls(baseUrl).forEach { (scheme, host) ->
                        val s = explicitScheme ?: scheme
                        val h = explicitHost ?: host
                        if (s != null && h != null) {
                            result.add(DeeplinkFilter(s, h, paths))
                        }
                    }
                }
            }
        }
    }

    return result.distinct()
}

private fun parseUrls(baseUrl: BaseUrlSpec): List<Pair<String?, String?>> {
    val urls = when (baseUrl) {
        is BaseUrlSpec.Static -> listOf(baseUrl.url)
        is BaseUrlSpec.Mirrors -> baseUrl.mirrors
        is BaseUrlSpec.Custom -> listOf(baseUrl.defaultUrl)
    }
    return urls.map { url ->
        val uri = runCatching { URI(url) }.getOrNull()
        uri?.scheme to uri?.host
    }
}

private fun Project.findThemeProject(themeName: String): Project {
    val themePath = ":lib-multisrc:$themeName"
    evaluationDependsOn(themePath)
    return project(themePath)
}
