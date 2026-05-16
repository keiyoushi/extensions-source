package keiyoushi.gradle.extension.codegen

import keiyoushi.gradle.extension.dsl.BaseUrlSpec
import keiyoushi.gradle.extension.dsl.OverrideValue
import java.io.Serializable

data class ResolvedExtension(
    val name: String,
    val pkg: String,
    val extensionClass: String,
    val superIsConfigurable: Boolean,
    val sources: List<ResolvedSource>,
) : Serializable

data class ResolvedSource(
    val name: String,
    val lang: String,
    val versionId: Int,
    val id: Long,
    val baseUrl: BaseUrlSpec,
    val overrides: Map<String, OverrideValue>,
    val pathPatterns: List<String>,
    val deeplinkScheme: String?,
    val deeplinkHost: String?,
) : Serializable {
    val hasDeeplink: Boolean get() = pathPatterns.isNotEmpty()
}
