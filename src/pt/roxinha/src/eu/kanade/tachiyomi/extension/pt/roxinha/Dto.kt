package eu.kanade.tachiyomi.extension.pt.roxinha

import eu.kanade.tachiyomi.source.model.Page
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
class SearchResponseDto(
    private val mangas: List<MangaDto> = emptyList(),
    val hasMore: Boolean = false,
) {
    fun toMangasPage(baseUrl: String): Pair<List<SManga>, Boolean> = mangas.map { it.toSManga(baseUrl) } to hasMore
}

@Serializable
class MangaDto(
    private val id: Int,
    private val title: String,
    private val cover: String? = null,
    private val author: String? = null,
    private val description: String? = null,
    private val status: String? = null,
    private val genres: String? = null,
    private val chapters: List<ChapterDto>? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "$id"
        this.title = this@MangaDto.title
        thumbnail_url = cover?.let { baseUrl + it }
        this.author = this@MangaDto.author
        this.description = this@MangaDto.description
        genre = this@MangaDto.genres
        this.status = when (this@MangaDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    fun toSChapters(): List<SChapter> = chapters?.map {
        SChapter.create().apply {
            url = "${it.id}"
            name = it.title?.takeIf { t -> t.isNotBlank() }
                ?: "Capítulo ${it.chapterNumber?.toString()?.removeSuffix(".0") ?: ""}".trim()
            chapter_number = it.chapterNumber ?: -1f
            date_upload = dateFormat.tryParse(it.createdAt)
        }
    }?.sortedByDescending { it.chapter_number } ?: emptyList()
}

@Serializable
class ChapterDto(
    val id: Int,
    val chapterNumber: Float? = null,
    val title: String? = null,
    val createdAt: String? = null,
)

@Serializable
class ChapterDetailsDto(
    private val pages: List<String> = emptyList(),
) {
    fun toPages(baseUrl: String): List<Page> = pages.mapIndexed { index, url ->
        Page(index, imageUrl = baseUrl + url)
    }
}
