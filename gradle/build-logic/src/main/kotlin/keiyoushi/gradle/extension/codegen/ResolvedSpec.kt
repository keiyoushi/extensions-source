package keiyoushi.gradle.extension.codegen

import keiyoushi.gradle.extension.dsl.BaseUrlSpec
import keiyoushi.gradle.extension.dsl.OverrideValue
import keiyoushi.gradle.extension.tasks.DeeplinkFilter
import java.io.Serializable

data class ResolvedExtension(
    val name: String,
    val pkg: String,
    val className: String,
    val sources: List<ResolvedSource>,
    val isNsfw: Boolean,
) : Serializable

data class ResolvedSource(
    val name: String,
    val lang: String,
    val isConfigurable: Boolean,
    val versionId: Int,
    val id: Long,
    val baseUrl: BaseUrlSpec,
    val overrides: Map<String, OverrideValue>,
    val deeplinks: List<DeeplinkFilter> = emptyList(),
) : Serializable
