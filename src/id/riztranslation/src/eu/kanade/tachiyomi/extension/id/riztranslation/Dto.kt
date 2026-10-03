package eu.kanade.tachiyomi.extension.id.riztranslation

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.format.DateTimeFormatter

@Serializable
class BookDto(
    val id: Int,
    private val judul: String,
    private val cover: String? = null,
    private val status: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val synopsis: String? = null,
    private val genres: List<BookGenreDto>? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = judul
        thumbnail_url = cover
    }

    fun toSMangaDetails() = SManga.create().apply {
        title = judul
        thumbnail_url = cover
        author = this@BookDto.author
        artist = this@BookDto.artist
        description = synopsis
        status = parseStatus(this@BookDto.status)
        genre = genres?.mapNotNull { it.genre?.nama }?.joinToString()
    }

    private fun parseStatus(status: String?) = when (status?.lowercase()) {
        "completed", "complete", "oneshot" -> SManga.COMPLETED
        "ongoing" -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }
}

@Serializable
class LatestChapterDto(
    @SerialName("Book")
    val book: BookDto? = null,
)

@Serializable
class BookGenreDto(
    val genre: GenreDto? = null,
)

@Serializable
class GenreDto(
    val nama: String? = null,
)

@Serializable
class ChapterDto(
    private val id: Int,
    private val bookId: Int,
    private val chapter: Float? = null,
    private val nama: String? = null,
    @SerialName("created_at")
    private val createdAt: String? = null,
    val isigambar: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "$bookId/$id"
        name = buildString {
            val chapNum = chapter?.toString()?.removeSuffix(".0")
            if (chapNum != null) {
                append("Chapter $chapNum")
            }
            if (!nama.isNullOrBlank()) {
                if (isNotEmpty()) append(" - ")
                append(nama)
            }
        }
        chapter_number = chapter ?: -1f
        date_upload = DateTimeFormatter.ISO_LOCAL_DATE_TIME.tryParseDateTime(createdAt)
    }
}
