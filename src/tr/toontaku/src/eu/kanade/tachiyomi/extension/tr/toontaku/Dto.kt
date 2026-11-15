package eu.kanade.tachiyomi.extension.tr.toontaku

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class ApiResponse<T>(val data: T)

@Serializable
class SeriesListDto(
    val series: List<SeriesDto>,
    val page: Int,
    val totalPages: Int,
)

@Serializable
class SeriesDto(
    private val slug: String,
    private val title: String,
    private val coverImageUrl: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@SeriesDto.title
        thumbnail_url = coverImageUrl
    }
}

@Serializable
class SeriesDetailsResponse(val series: SeriesDetailsDto)

@Serializable
class SeriesDetailsDto(
    private val id: String,
    private val slug: String,
    private val title: String,
    private val coverImageUrl: String? = null,
    private val description: String? = null,
    private val type: String,
    private val status: String,
    private val alternativeTitles: List<String> = emptyList(),
    private val genres: List<GenreDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        memo = buildJsonObject { put("id", id) }
        title = this@SeriesDetailsDto.title
        thumbnail_url = coverImageUrl
        genre = buildList {
            add(type.lowercase().replaceFirstChar(Char::uppercase))
            genres.mapTo(this) { it.name }
        }.joinToString()
        description = buildString {
            this@SeriesDetailsDto.description?.let {
                append(Jsoup.parseBodyFragment(it).wholeText().replace(PARAGRAPH_GAP, "\n\n").trim())
            }
            if (alternativeTitles.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Alternatif isimler: ")
                alternativeTitles.joinTo(this)
            }
        }.ifBlank { null }
        status = when (this@SeriesDetailsDto.status) {
            "DEVAM_EDIYOR" -> SManga.ONGOING
            "TAMAMLANDI", "SONLANDI" -> SManga.COMPLETED
            "DURAKLADI" -> SManga.ON_HIATUS
            "BIRAKILDI" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class LatestChaptersDto(
    val items: List<LatestChapterDto>,
    val nextCursor: String? = null,
    val isEnd: Boolean,
)

@Serializable
class LatestChapterDto(
    private val seriesSlug: String,
    private val seriesTitle: String,
    private val coverUrl: String? = null,
    val contentKind: String,
) {
    fun toSManga() = SManga.create().apply {
        url = seriesSlug
        title = seriesTitle
        thumbnail_url = coverUrl
    }
}

@Serializable
class GenreDto(val name: String)

@Serializable
class ChapterListDto(val chapters: List<ChapterListItemDto>)

@Serializable
class ChapterListItemDto(
    private val id: String,
    private val chapterNumber: Float,
    private val title: String? = null,
    private val canRead: Boolean,
    private val publishedAt: String,
) {
    fun toSChapter(seriesSlug: String) = SChapter.create().apply {
        val number = chapterNumber.toString().removeSuffix(".0")

        url = id
        memo = buildJsonObject {
            put("slug", seriesSlug)
            put("number", number)
            put("locked", !canRead)
        }
        name = buildString {
            if (!canRead) append("🔒 ")
            append("Bölüm ")
            append(number)
            // most titles are just "Chapter N" (sometimes with a date appended), which adds nothing
            if (title != null && !title.startsWith("Chapter ", ignoreCase = true)) {
                append(" - ")
                append(title)
            }
        }
        chapter_number = chapterNumber
        date_upload = Instant.tryParse(publishedAt)
    }
}

@Serializable
class ChapterDto(val imageUrls: List<String>)

@Serializable
class FiltersDto(val genres: List<FilterGenreDto>)

@Serializable
class FilterGenreDto(
    val slug: String,
    val name: String,
)

private val PARAGRAPH_GAP = Regex("""\s*\n\s*\n\s*""")
