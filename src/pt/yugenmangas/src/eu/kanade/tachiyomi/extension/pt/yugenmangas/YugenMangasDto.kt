package eu.kanade.tachiyomi.extension.pt.yugenmangas

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class LibraryWrapper(
    @SerialName("initialSeries")
    val mangas: List<MangaDetailsDto>,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
) {
    fun hasNextPage() = currentPage < totalPages
}

@Serializable
class MangaDetailsDto(
    val title: String,
    @SerialName("path_cover")
    val cover: String,
    val code: String,
    val author: String? = null,
    val artist: String? = null,
    val genres: List<String> = emptyList(),
    val synopsis: String? = null,
    val status: String? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@MangaDetailsDto.title
        author = this@MangaDetailsDto.author
        artist = this@MangaDetailsDto.artist
        description = synopsis
        status = when (this@MangaDetailsDto.status) {
            "Em Lançamento" -> SManga.ONGOING
            "Hiato" -> SManga.ON_HIATUS
            "Cancelado" -> SManga.CANCELLED
            "Finalizado" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = genres.joinToString()
        thumbnail_url = cover
        url = "/series/$code"
    }
}

@Serializable
class SearchDto(
    val query: String,
)

@Serializable
class SearchMangaDto(
    val series: List<MangaDto>,
)

@Serializable
class MangaDto(
    val code: String,
    val cover: String,
    val name: String,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        thumbnail_url = cover
        url = "/series/$code"
    }
}
