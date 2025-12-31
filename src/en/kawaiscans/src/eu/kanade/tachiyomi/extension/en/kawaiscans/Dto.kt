package eu.kanade.tachiyomi.extension.en.kawaiscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Serializable
class PopularResponseDto(
    val manga: List<MangaDto>,
    val totalPages: Int,
    val currentPage: Int,
)

@Serializable
class MangaDto(
    val title: String,
    val slug: String,
    val imageUrl: String,
    val genres: List<String>?,
    val synopsis: String?,
    val type: String?,
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        title = this@MangaDto.title
        url = "/$slug"
        thumbnail_url = baseUrl + imageUrl
        description = synopsis
        genre = (listOfNotNull(type) + genres.orEmpty()).joinToString()
    }
}

@Serializable
class ChapterDto(
    val slug: String,
    val title: String,
    val createdAt: String,
) {
    fun toSChapter(mangaUrl: String): SChapter = SChapter.create().apply {
        name = title
        url = "/$mangaUrl/$slug"
        date_upload = dateFormat.tryParse(createdAt)
    }
}

@Serializable
class PageListDto(
    val pages: List<String>,
)
