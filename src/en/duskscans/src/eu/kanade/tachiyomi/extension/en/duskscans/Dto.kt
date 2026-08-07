package eu.kanade.tachiyomi.extension.en.duskscans

import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable

@Serializable
class MangaDto(
    val title: String,
    val slug: String,
    val description: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val cover: String? = null,
    val status: String? = null,
    val type: String? = null,
    val views: Int = 0,
    val rating: Double = 0.0,
    val genres: List<String> = emptyList(),
    val createdAt: String? = null,
    private val updatedAt: String? = null,
    private val chapters: List<ChapterDto> = emptyList(),
) {
    val latestUpdate get() = chapters.firstOrNull()?.releaseDate ?: updatedAt
}

@Serializable
class ChapterDto(
    val id: String,
    val number: Int,
    val title: String = "",
    val releaseDate: String? = null,
)

@Serializable
class ChapterDetailDto(
    private val pages: String,
) {
    // The API returns the page list as a JSON-encoded string, not an array.
    val pageUrls get() = pages.parseAs<List<String>>()
}
