package eu.kanade.tachiyomi.extension.ar.mangadar

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
data class SearchResponse(
    val success: Boolean = false,
    val data: List<SearchMangaItemDto> = emptyList(),
)

@Serializable
data class SearchMangaItemDto(
    val id: Int,
    val title: String,
    val url: String,
    val cover: String,
)

@Serializable
data class ChapterDto(
    val id: Int,
    val number: String,
    val title: String,
    val url: String,
    val date: String,
    val timestamp: Long,
    val ago: String,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        name = this@ChapterDto.title
        chapter_number = number.toFloatOrNull() ?: 0f
        url = this@ChapterDto.url.toHttpUrl().encodedPath
        date_upload = timestamp * 1000
    }
}
