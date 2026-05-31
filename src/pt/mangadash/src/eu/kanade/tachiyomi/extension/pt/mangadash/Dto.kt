package eu.kanade.tachiyomi.extension.pt.mangadash

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MangaListDto(
    private val items: List<MangaItemDto> = emptyList(),
    private val pagination: PaginationDto? = null,
) {
    val mangas get() = items.map { it.toSManga() }
    val hasNext get() = pagination?.hasNext ?: false
}

@Serializable
class MangaItemDto(
    private val id: Int,
    private val slug: String,
    private val nome: String,
    private val capa: String,
) {
    fun toSManga() = SManga.create().apply {
        title = nome
        url = "/manga/$id-$slug"
        thumbnail_url = capa
    }
}

@Serializable
class PaginationDto(
    @SerialName("has_next") val hasNext: Boolean = false,
)

@Serializable
class ChapterDataDto(
    private val pdfUrl: String? = null,
    @SerialName("pdf_url") private val pdfUrlSnake: String? = null,
    private val images: List<String>? = null,
    private val imagens: List<String>? = null,
    private val pages: List<String>? = null,
) {
    val resolvedImages get() = images ?: imagens ?: pages
    val resolvedPdfUrl get() = pdfUrl ?: pdfUrlSnake
}
