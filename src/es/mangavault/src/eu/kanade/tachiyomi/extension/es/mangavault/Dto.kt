package eu.kanade.tachiyomi.extension.es.mangavault

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
class SearchResponse(
    val results: List<MangaDto>,
    val hasMore: Boolean,
)

@Serializable
class MangaDto(
    private val slug: String,
    private val title: String,
    @SerialName("cover_url") private val coverUrl: String?,
    @SerialName("content_type") val contentType: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = "/manga/$slug"
        title = this@MangaDto.title
        thumbnail_url = coverUrl
    }
}

@Serializable
class ChapterListProps(
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    @SerialName("chapter_number") private val number: Float,
    private val title: String?,
    @SerialName("created_at") private val createdAt: String?,
) {
    fun toSChapter(mangaUrl: String) = SChapter.create().apply {
        val number = number.toString().removeSuffix(".0")
        url = "$mangaUrl/$number"
        name = listOfNotNull("Capítulo $number", title?.takeIf(String::isNotBlank)).joinToString(": ")
        chapter_number = this@ChapterDto.number
        date_upload = DATE_FORMAT.tryParseDateTime(createdAt, ZoneOffset.UTC)
    }
}

@Serializable
class ReaderProps(
    val imageUrls: List<String>,
)

@Serializable
class ExplorerProps(
    val genreOptions: List<GenreOption>,
)

@Serializable
class GenreOption(
    val value: String,
    val label: String,
)

private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
