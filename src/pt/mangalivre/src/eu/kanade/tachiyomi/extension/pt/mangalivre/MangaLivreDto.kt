package eu.kanade.tachiyomi.extension.pt.mangalivre

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MangaLivreDto(
    @SerialName("data")
    val mangas: List<MangaDto>,
    val path: String,
    @SerialName("current_page")
    val currentPage: Int,
    @SerialName("last_page")
    val lastPage: Int,
) {
    fun hasNextPage(): Boolean = currentPage < lastPage
}

@Serializable
data class MangaDto(
    val id: Int,
    val name: String,
    val photo: String,
    val slug: String,
    val synopsis: String,
    val status: Name?,
    @SerialName("categories")
    val genre: List<Name>?,
)

@Serializable
data class Name(
    @SerialName("name")
    val value: String,
)

@Serializable
data class ChapterDto(
    val id: Int,
    val title: String,
    val chapter: String,
    @SerialName("created_at")
    val createdAt: String,
)

@Serializable
data class MangaPageDto(
    @SerialName("manga_pages")
    val pages: List<PageDto>,
)

@Serializable
data class PageDto(
    @SerialName("page")
    val url: String,
)
