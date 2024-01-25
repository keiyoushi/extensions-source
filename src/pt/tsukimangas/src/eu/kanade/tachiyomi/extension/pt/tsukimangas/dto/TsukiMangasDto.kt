package eu.kanade.tachiyomi.extension.pt.tsukimangas.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MangaListDto(
    val data: List<SimpleMangaDto>,
    val page: Int,
    val lastPage: Int,
)

@Serializable
data class SimpleMangaDto(
    val id: Int,
    @SerialName("url") val slug: String,
    val title: String,
    val poster: String? = null,
    val cover: String? = null,
) {
    val imagePath = "/img/imgs/${poster ?: cover ?: "nobackground.jpg"}"
    val entryPath = "/$id/$slug"
}

@Serializable
data class CompleteMangaDto(
    val id: Int,
    @SerialName("url") val slug: String,

    val title: String,
    val poster: String? = null,
    val cover: String? = null,
    val status: String? = null,
    val synopsis: String? = null,
    val staff: String? = null,
    val genres: List<Genre> = emptyList(),
    val titles: List<Title> = emptyList(),
) {
    val entryPath = "/$id/$slug"

    val imagePath = "/img/imgs/${poster ?: cover ?: "nobackground.jpg"}"

    @Serializable
    data class Genre(val genre: String)

    @Serializable
    data class Title(val title: String)
}

@Serializable
data class ChapterListDto(val chapters: List<ChapterDto>)

@Serializable
data class ChapterDto(
    val number: String,
    val title: String? = null,
    val created_at: String? = null,
    private val versions: List<Version>,
) {
    @Serializable
    data class Version(val id: Int)

    val versionId = versions.first().id
}

@Serializable
data class PageListDto(val pages: List<PageDto>)

@Serializable
data class PageDto(val url: String)
