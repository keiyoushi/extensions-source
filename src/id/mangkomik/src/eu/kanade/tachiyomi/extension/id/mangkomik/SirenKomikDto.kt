package eu.kanade.tachiyomi.extension.id.mangkomik

import kotlinx.serialization.Serializable

@Serializable
data class SirenKomikDto(
    val `data`: DataWrapper,
) {
    val pages: List<String>
        get() = data.data.sources
            .firstOrNull { it.images.isNotEmpty() }
            ?.images ?: emptyList()
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
