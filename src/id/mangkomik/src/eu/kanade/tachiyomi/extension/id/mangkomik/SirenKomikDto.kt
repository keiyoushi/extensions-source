package eu.kanade.tachiyomi.extension.id.mangkomik

import kotlinx.serialization.Serializable

@Serializable
data class SirenKomikDto(
    val `data`: DataWrapper,
) {
    val pages get() = data.data.sources.firstOrNull()?.images ?: emptyList()
}

@Serializable
class Source(
    val images: List<String>,
)

@Serializable
class DataWrapper(
    val `data`: Data,
)

@Serializable
class Data(
    val sources: List<Source>,
)
