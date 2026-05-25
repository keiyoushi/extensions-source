package keiyoushi.gradle.extension

import keiyoushi.gradle.extension.codegen.DeeplinkFilter
import keiyoushi.gradle.extension.codegen.ResolvedExtension
import keiyoushi.gradle.extension.codegen.ResolvedSource
import keiyoushi.gradle.extension.codegen.generateSourceId
import keiyoushi.gradle.extension.dsl.BaseUrlSpec
import keiyoushi.gradle.extension.dsl.DeeplinkSpec
import keiyoushi.gradle.extension.dsl.ExtensionSpec
import keiyoushi.gradle.extension.dsl.defaultUrl
import keiyoushi.gradle.extensions.baseVersionCode
import keiyoushi.gradle.utils.assertWithoutFlag
import org.gradle.api.Project
import java.net.URI

data class ResolvedSpec(
    val extension: ResolvedExtension,
    val effectiveVersionCode: Int,
    val effectiveVersionName: String,
)

fun Project.resolveExtensionSpec(spec: ExtensionSpec, pkg: String): ResolvedSpec {
    val (extName, className) = validateAndGetMetadata(spec)
    val sources = validateAndGetSources(spec)

    val themeProject = spec.theme.orNull?.let { themeName ->
        val themePath = ":lib-multisrc:$themeName"
        evaluationDependsOn(themePath)
        project(themePath)
    }
    val themePaths = themeProject
        ?.extensions
        ?.findByType(DeeplinkSpec::class.java)
        ?.pathPatterns
        ?.orNull
        .orEmpty()

    val resolvedSources = sources.map { it.resolve(themePaths) }

    val effectiveVersionCode = (themeProject?.baseVersionCode ?: 0) + spec.versionCode.get()
    val effectiveVersionName = "1.4.$effectiveVersionCode"

    val resolvedExtension = ResolvedExtension(
        name = extName,
        pkg = pkg,
        className = className,
        sources = resolvedSources,
    )

    return ResolvedSpec(resolvedExtension, effectiveVersionCode, effectiveVersionName)
}

private fun validateAndGetMetadata(spec: ExtensionSpec): Pair<String, String> {
    assertWithoutFlag(spec.name.isPresent) { "extension { name = ... } is required" }
    assertWithoutFlag(spec.className.isPresent) { "extension { className = ... } is required" }
    assertWithoutFlag(spec.versionCode.isPresent) { "extension { versionCode = ... } is required" }
    assertWithoutFlag(spec.nsfw.isPresent) { "extension { nsfw = ... } is required" }
    val extName = spec.name.get()
    assertWithoutFlag(extName.isNotEmpty()) { "extension.name must not be empty" }
    assertWithoutFlag(extName.all { it.code < 0x180 }) { "Extension name should be romanized" }

    val className = spec.className.get()
    assertWithoutFlag(className.isNotEmpty()) { "extension.className must not be empty" }
    assertWithoutFlag(className.first().isUpperCase()) { "extension.className must be PascalCase (got '$className')" }

    return extName to className
}

private fun validateAndGetSources(spec: ExtensionSpec): List<keiyoushi.gradle.extension.dsl.SourceSpec> {
    val sources = spec.sources.orNull.orEmpty()
    assertWithoutFlag(sources.isNotEmpty()) { "At least one source { } block is required" }
    val seenNameLang = mutableSetOf<Pair<String, String>>()
    sources.forEach { src ->
        assertWithoutFlag(src.name.isPresent) { "source { name = ... } is required" }
        assertWithoutFlag(src.lang.isPresent) { "source { lang = ... } is required" }
        assertWithoutFlag(src.resolvedBaseUrl.isPresent) { "source { baseUrl(...) } is required (for '${src.name.orNull}')" }
        val key = src.name.get() to src.lang.get()
        assertWithoutFlag(seenNameLang.add(key)) {
            "Duplicate (name, lang) = ('${key.first}', '${key.second}') across sources — IDs would collide. " +
                "Make names distinct or set an explicit id = ..."
        }
    }
    return sources
}

private fun keiyoushi.gradle.extension.dsl.SourceSpec.resolve(themePaths: List<String>): ResolvedSource {
    val baseUrl = resolvedBaseUrl.get()
    val versionId = versionId.orElse(1).get()
    val effectiveId = if (id.isPresent) id.get() else generateSourceId(name.get(), lang.get(), versionId)

    val deeplink = deeplinkSpec.orNull
    val explicitPaths = deeplink?.pathPatterns?.orNull.orEmpty()
    val combinedPaths = (themePaths + explicitPaths).distinct()

    val resolvedDeeplinks = if (combinedPaths.isEmpty()) {
        emptyList()
    } else {
        val explicitScheme = deeplink?.scheme?.orNull
        val explicitHost = deeplink?.host?.orNull

        if (explicitScheme != null && explicitHost != null) {
            listOf(DeeplinkFilter(explicitScheme, explicitHost, combinedPaths))
        } else {
            // Infer from mirrors/base URL
            val urls = when (baseUrl) {
                is BaseUrlSpec.Static -> listOf(baseUrl.url)
                is BaseUrlSpec.Mirrors -> baseUrl.urls
                is BaseUrlSpec.Custom -> listOf(baseUrl.defaultUrl)
            }
            urls.map { url ->
                val uri = runCatching { URI(url) }.getOrNull()
                val scheme = explicitScheme ?: uri?.scheme
                val host = explicitHost ?: uri?.host
                assertWithoutFlag(scheme != null && host != null) {
                    "source '${name.get()}' has deeplink paths but URL '$url' could not be " +
                        "parsed into scheme://host; set deeplink { scheme = ...; host = ... } explicitly."
                }
                DeeplinkFilter(scheme!!, host!!, combinedPaths)
            }
        }
    }

    return ResolvedSource(
        name = name.get(),
        lang = lang.get(),
        isConfigurable = configurableSource.getOrElse(false),
        versionId = versionId,
        id = effectiveId,
        baseUrl = baseUrl,
        overrides = overrides.orNull.orEmpty(),
        deeplinks = resolvedDeeplinks,
    )
}
