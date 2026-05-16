package keiyoushi.gradle.extension.codegen

import keiyoushi.gradle.extension.dsl.BaseUrlSpec
import keiyoushi.gradle.extension.dsl.OverrideValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

// Spec data — the @Serializable surface is what KSP consumes (encoded as base64-JSON via
// the "keiyoushi.spec" KSP option). Deeplink fields are @Transient because only the manifest
// task on the Gradle side reads them; KSP doesn't need them.
@Serializable
data class ResolvedExtension(
    val name: String,
    val pkg: String,
    val sources: List<ResolvedSource>,
)

@Serializable
data class ResolvedSource(
    val name: String,
    val lang: String,
    val versionId: Int,
    val id: Long,
    val baseUrl: BaseUrlSpec,
    val overrides: Map<String, OverrideValue>,
    @Transient val pathPatterns: List<String> = emptyList(),
    @Transient val deeplinkScheme: String? = null,
    @Transient val deeplinkHost: String? = null,
) {
    val hasDeeplink: Boolean get() = pathPatterns.isNotEmpty()
}
