package eu.kanade.tachiyomi.extension.pt.yugenmangas

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
class LibraryWrapper<T>(
    @SerialName("initialData")
    private val library: Library<T>,
) {
    val mangas: List<T> get() = library.series
    fun hasNextPage() = library.hasNextPage()
}

@Serializable
class Library<T>(
    @JsonNames("updates")
    val series: List<T> = emptyList(),
    val pagination: Pagination,
) {
    fun hasNextPage() = pagination.hasNextPage()
}

@Serializable
class Pagination(
    @SerialName("current_page")
    val currentPage: Int = 0,
    @SerialName("total_pages")
    val totalPages: Int = 0,
) {
    fun hasNextPage() = currentPage < totalPages
}

@Serializable
class LatestUpdateDto(
    val series: MangaDto,
) {
    fun toSManga(baseUrl: String) = series.toSManga(baseUrl)
}

@Serializable
class MangaDto(
    val code: String,
    val cover: String,
    val name: String = "",
    val title: String = "",
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = this@MangaDto.title.takeIf(String::isNotBlank) ?: name
        thumbnail_url = "$baseUrl/$cover&w=640&q=75"
        url = "/series/$code"
    }
}

@Serializable
class PageDto(
    val path: String,
    val number: Long,
)
