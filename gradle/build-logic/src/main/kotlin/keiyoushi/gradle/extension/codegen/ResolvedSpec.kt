package keiyoushi.gradle.extension.codegen

import keiyoushi.gradle.extension.dsl.BaseUrlSpec
import keiyoushi.gradle.extension.dsl.OverrideValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.Serializable as JvmSerializable

// Spec data — the @Serializable surface is what GenerateSourceTask consumes.
// Deeplink fields are @Transient because only the manifest task reads them.
@Serializable
data class ResolvedExtension(
    val name: String,
    val pkg: String,
    val className: String,
    val sources: List<ResolvedSource>,
) : JvmSerializable

@Serializable
data class ResolvedSource(
    val name: String,
    val lang: String,
    val versionId: Int,
    val id: Long,
    val baseUrl: BaseUrlSpec,
    val overrides: Map<String, OverrideValue>,
    @Transient val deeplinks: List<DeeplinkFilter> = emptyList(),
) : JvmSerializable {
    val hasDeeplink: Boolean get() = deeplinks.isNotEmpty()
}
