package eu.kanade.tachiyomi.extension.id.mangkomik

import kotlinx.serialization.Serializable

@Serializable
data class SirenKomikDto(
    val `data`: Data,
) {
    val pages get() = data.sources.firstOrNull()?.images ?: emptyList()
}

@Serializable
class Source(
    val images: List<String>,
    val source: String,
)

@Serializable
class Data(
    val sources: List<Source>,
)
