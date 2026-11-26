package eu.kanade.tachiyomi.extension.id.ikiru

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class IkiruResponseDto<T>(
    private val success: Boolean,
    private val message: String? = null,
    private val data: T? = null,
    val pagination: IkiruPaginationDto? = null,
) {
    // the API returns HTTP 200 with a failure envelope for bad input, so the flag must be checked explicitly
    fun requireData(): T {
        if (!success || data == null) {
            throw Exception(message ?: "Request failed")
        }
        return data
    }
}

@Serializable
class IkiruPaginationDto(
    val hasNextPage: Boolean = false,
)

@Serializable
class IkiruSeriesItemDto(
    private val title: String,
    private val slug: String,
    private val coverUrl: String? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        this.title = this@IkiruSeriesItemDto.title
        this.url = slug
        thumbnail_url = coverUrl
    }
}

@Serializable
class IkiruSeriesDetailDto(
    private val title: String,
    private val slug: String,
    private val altTitles: List<String> = emptyList(),
    private val status: String? = null,
    private val synopsis: String? = null,
    private val coverUrl: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val genreNames: List<String> = emptyList(),
    val chapters: List<IkiruChapterItemDto> = emptyList(),
) {
    fun toSManga(baseUrl: String): SManga {
        // the site repeats the main title inside altTitles
        val altTitles = altTitles.filterNot { it.trim().equals(title, ignoreCase = true) }

        return SManga.create().apply {
            this.title = this@IkiruSeriesDetailDto.title
            this.url = slug
            thumbnail_url = coverUrl
            description = MarkdownDescription.Builder()
                .description(synopsis?.let { Jsoup.parseBodyFragment(it, baseUrl).body() })
                .altTitles(altTitles)
                .build()
            this.status = when (this@IkiruSeriesDetailDto.status?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                "cancelled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
            this.author = this@IkiruSeriesDetailDto.author
            this.artist = this@IkiruSeriesDetailDto.artist
            genre = genreNames.joinToString().ifEmpty { null }
        }
    }
}

// the previous multisrc persisted manga urls as JSON blobs holding the slug
@Serializable
class IkiruLegacyMangaUrlDto(
    val slug: String,
)

@Serializable
class IkiruChapterItemDto(
    private val chapterNumber: Float,
    private val title: String? = null,
    private val publishedAt: String? = null,
) {
    // the key keeps the old site's path so existing library chapters stay matched
    fun toSChapter(mangaSlug: String): SChapter = SChapter.create().apply {
        val number = chapterNumber.toString().removeSuffix(".0")
        this.url = "/manga/$mangaSlug/chapter-$number"
        this.name = cleanChapterName(number)
        this.chapter_number = chapterNumber
        this.date_upload = Instant.tryParse(publishedAt)
    }

    private fun cleanChapterName(number: String): String {
        val match = chapterTitleRegex.findAll(title.orEmpty()).lastOrNull()
            ?: return "Chapter $number"
        val matched = match.value
        return if (matched.startsWith("chapter", ignoreCase = true)) {
            "Chapter${matched.substring(7)}"
        } else {
            matched
        }
    }

    companion object {
        private val chapterTitleRegex = Regex("""(Chapter\s*[\d.]+(?:\s*[:\-].*)?)""", RegexOption.IGNORE_CASE)
    }
}

@Serializable
class IkiruChapterPagesDto(
    val pages: List<IkiruPageDto> = emptyList(),
)

@Serializable
class IkiruPageDto(
    val pageNumber: Int,
    val imageUrl: String,
)

@Serializable
class IkiruGenreDto(
    val name: String,
    val slug: String,
)
