package eu.kanade.tachiyomi.extension.all.rawkumame

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class SeriesDto(
    private val slug: String,
    private val title: String,
    private val altTitles: List<String>,
    private val cover: String?,
    private val synopsis: String?,
    private val author: String?,
    private val artist: String?,
    private val genres: List<String>,
    private val type: String?,
    private val status: String?,
    private val chapters: List<ChapterDto>,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = slug
        title = this@SeriesDto.title
        thumbnail_url = cover?.let { baseUrl + it }
    }

    fun toSMangaDetails(baseUrl: String) = toSManga(baseUrl).apply {
        author = this@SeriesDto.author
        artist = this@SeriesDto.artist
        genre = (genres + listOfNotNull(type?.replaceFirstChar { it.uppercase() })).joinToString()
        status = when (this@SeriesDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = buildString {
            synopsis?.let { append(it) }
            if (altTitles.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Alternative titles:\n")
                append(altTitles.joinToString("\n") { "• $it" })
            }
        }
    }

    fun toSChapters(lang: String) = chapters.filter { lang in it.langs }.map { it.toSChapter(slug) }
}

@Serializable
class ChapterDto(
    private val slug: String,
    private val number: Float,
    private val releasedAt: String?,
    private val translatedAt: String?,
    val langs: List<String>,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "$mangaSlug/$slug"
        name = "Chapter ${number.toString().removeSuffix(".0")}"
        chapter_number = number
        date_upload = Instant.tryParse(translatedAt ?: releasedAt)
    }
}

// fonts is required so the next.js lookup skips the series chapter entries, which also have pages
@Serializable
class ReaderChapterDto(
    val pages: List<ReaderPageDto>,
    val fonts: List<String>,
)

@Serializable
class ReaderPageDto(
    val url: String,
    val width: Float,
    val height: Float,
    val text: Map<String, String>,
)

@Serializable
class PageOverlay(
    val width: Float,
    val height: Float,
    val svg: String,
)
