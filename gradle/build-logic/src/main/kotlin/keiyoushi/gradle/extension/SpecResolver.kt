package keiyoushi.gradle.extension

import keiyoushi.gradle.extension.analysis.analyzeSuperClass
import keiyoushi.gradle.extension.codegen.ResolvedExtension
import keiyoushi.gradle.extension.codegen.ResolvedSource
import keiyoushi.gradle.extension.codegen.generateSourceId
import keiyoushi.gradle.extension.dsl.BaseUrlSpec
import keiyoushi.gradle.extension.dsl.ExtensionSpec
import keiyoushi.gradle.extension.dsl.ThemeDeeplinkSpec
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
    val extName = validateAndGetName(spec)
    val extensionClass = validateAndGetExtensionClass(spec, pkg)
    val sources = validateAndGetSources(spec)

    val themeProject = spec.theme.orNull?.let { themeName ->
        val themePath = ":lib-multisrc:$themeName"
        evaluationDependsOn(themePath)
        project(themePath)
    }
    val themePaths = themeProject
        ?.extensions
        ?.findByType(ThemeDeeplinkSpec::class.java)
        ?.pathPatterns
        ?.orNull
        .orEmpty()

    val resolvedSources = sources.map { it.resolve(themePaths) }

    enforceConfigurableContract(extensionClass, resolvedSources)

    val effectiveVersionCode = (themeProject?.baseVersionCode ?: 0) + spec.versionCode.get()
    val effectiveVersionName = "1.4.$effectiveVersionCode"

    val resolvedExtension = ResolvedExtension(
        name = extName,
        pkg = pkg,
        extensionClass = extensionClass,
        superIsConfigurable = analyzeSuperClass(file("src"), extensionClass).declaresConfigurable,
        sources = resolvedSources,
    )

    return ResolvedSpec(resolvedExtension, effectiveVersionCode, effectiveVersionName)
}

private fun validateAndGetName(spec: ExtensionSpec): String {
    assertWithoutFlag(spec.name.isPresent) { "extension { name = ... } is required" }
    assertWithoutFlag(spec.versionCode.isPresent) { "extension { versionCode = ... } is required" }
    assertWithoutFlag(spec.nsfw.isPresent) { "extension { nsfw = ... } is required" }
    val extName = spec.name.get()
    assertWithoutFlag(extName.isNotEmpty()) { "extension.name must not be empty" }
    assertWithoutFlag(extName.all { it.code < 0x180 }) { "Extension name should be romanized" }
    return extName
}

private fun Project.validateAndGetExtensionClass(spec: ExtensionSpec, pkg: String): String {
    assertWithoutFlag(spec.extensionClass.isPresent) { "extension { extensionClass = ... } is required" }
    val extensionClass = spec.extensionClass.get()
    assertWithoutFlag(extensionClass.matches(Regex("^[A-Z][A-Za-z0-9_]*$"))) {
        "extension.extensionClass must be a simple PascalCase class name (got '$extensionClass')"
    }
    val classFile = file("src/${pkg.replace('.', '/')}/$extensionClass.kt")
    assertWithoutFlag(classFile.exists()) {
        "extension.extensionClass '$extensionClass' references missing file: $classFile"
    }
    return extensionClass
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
    val baseDefault = baseUrl.defaultUrl
    val uri = runCatching { URI(baseDefault) }.getOrNull()
    val explicitPaths = deeplink?.pathPatterns?.orNull.orEmpty()
    val combinedPaths = (themePaths + explicitPaths).distinct()
    val scheme = deeplink?.scheme?.orNull ?: uri?.scheme
    val host = deeplink?.host?.orNull ?: uri?.host

    if (combinedPaths.isNotEmpty()) {
        assertWithoutFlag(scheme != null && host != null) {
            "source '${name.get()}' has deeplink paths but baseUrl '$baseDefault' could not be " +
                "parsed into scheme://host; set deeplink { scheme = ...; host = ... } explicitly."
        }
    }

    return ResolvedSource(
        name = name.get(),
        lang = lang.get(),
        versionId = versionId,
        id = effectiveId,
        baseUrl = baseUrl,
        overrides = overrides.orNull.orEmpty(),
        pathPatterns = combinedPaths,
        deeplinkScheme = scheme,
        deeplinkHost = host,
    )
}

private fun Project.enforceConfigurableContract(extensionClass: String, sources: List<ResolvedSource>) {
    val anyConfigurable = sources.any { it.baseUrl !is BaseUrlSpec.Static }
    if (!anyConfigurable) return
    val analysis = analyzeSuperClass(file("src"), extensionClass)
    if (analysis.declaresConfigurable && !analysis.overridesSetupPrefs) {
        throw AssertionError(
            "$extensionClass declares ConfigurableSource as a supertype but does not override " +
                "fun setupPreferenceScreen(screen: PreferenceScreen). Codegen needs the super class " +
                "to provide a concrete impl so super.setupPreferenceScreen(screen) can be called from Generated.",
        )
    }
}
