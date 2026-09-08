package eu.kanade.tachiyomi.extension.pt.sssscanlator

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant

@Serializable
class ListDto(
    val series: List<SeriesDto> = emptyList(),
    val hasNextPage: Boolean = false,
)

@Serializable
class SeriesDto(
    private val slug: String,
    private val title: String,
    private val cover: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/obra/$slug"
        title = this@SeriesDto.title
        thumbnail_url = cover?.takeIf(String::isNotBlank)
    }
}

@Serializable
class MangaDto(
    private val slug: String,
    private val title: String,
    private val cover: String? = null,
    private val description: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val status: String? = null,
    private val genres: List<String> = emptyList(),
    private val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "/obra/$slug"
        title = this@MangaDto.title
        thumbnail_url = cover?.takeIf(String::isNotBlank)
        description = this@MangaDto.description?.takeIf(String::isNotBlank)
        author = this@MangaDto.author?.takeIf(String::isNotBlank)
        artist = this@MangaDto.artist?.takeIf(String::isNotBlank)
        genre = genres.joinToString()
        status = when (this@MangaDto.status?.uppercase()) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            "CANCELED", "CANCELLED" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    val chapterList: List<SChapter> get() = chapters.map { it.toSChapter(slug) }
}

@Serializable
class ChapterDto(
    private val id: String,
    private val number: Double,
    private val title: String? = null,
    private val releaseDate: String? = null,
    private val releaseAt: String? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        val label = number.toString().removeSuffix(".0")
        url = id
        name = title?.takeIf(String::isNotBlank) ?: "Capítulo $label"
        chapter_number = number.toFloat()
        date_upload = Instant.tryParse(releaseAt).takeIf { it != 0L } ?: DATE_FORMAT.tryParseDate(releaseDate)
        memo = buildJsonObject {
            put("slug", mangaSlug)
            put("number", label)
        }
    }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    }
}

@Serializable
class PagesDto(
    private val pages: List<String> = emptyList(),
) {
    fun toPageList() = pages.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
}
