package io.github.keiyoushi.gradle.internal

import kotlinx.serialization.Serializable

@Serializable
internal data class ResolvedSource(val name: String, val lang: String, val id: Long, val baseUrl: BaseUrlMetadata)

@Serializable
internal data class ExtensionMetadata(
    val packageName: String,
    val name: String,
    val versionCode: Int,
    val versionName: String,
    val extensionLib: String,
    val contentWarning: Int,
    val sources: List<SourceMetadata>,
)

@Serializable
internal data class SourceMetadata(
    val id: Long,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val mirrorUrls: List<String> = emptyList(),
)

@Serializable
internal data class BaseUrlMetadata(
    val type: String,
    val defaultUrl: String,
    val mirrors: List<MirrorMetadata> = emptyList(),
)

@Serializable
internal data class MirrorMetadata(
    val url: String,
    val label: String = "",
)

internal fun BaseUrl.toMetadata(): BaseUrlMetadata = when (this) {
    is BaseUrl.Static -> BaseUrlMetadata("static", url)
    is BaseUrl.Mirrors -> BaseUrlMetadata("mirrors", mirrors.first().url, mirrors.map { it.toMetadata() })
    is BaseUrl.Custom -> BaseUrlMetadata("custom", defaultUrl)
}

private fun BaseUrl.Mirror.toMetadata(): MirrorMetadata = MirrorMetadata(url, label.orEmpty())
