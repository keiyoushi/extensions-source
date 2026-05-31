package eu.kanade.tachiyomi.extension.es.codearc

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchResponseDto(
    val items: List<SearchItemDto>,
)

@Serializable
class SearchItemDto(
    private val slug: String,
    private val titulo: String,
    private val portada: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/$slug"
        title = titulo
        thumbnail_url = portada?.let { if (it.startsWith("http")) it else "$baseUrl$it" }
    }
}

@Serializable
class PageDto(
    @SerialName("imagen_url") val imagenUrl: String,
)

@Serializable
class ReaderDto(
    val pages: List<PageDto>,
)
