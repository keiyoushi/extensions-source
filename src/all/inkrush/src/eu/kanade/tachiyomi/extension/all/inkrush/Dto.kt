package eu.kanade.tachiyomi.extension.all.inkrush

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class CatalogDto(
    val comics: List<ComicDto>,
    val hasNextPage: Boolean = false,
)

@Serializable
class ComicDto(
    private val id: String,
    private val title: String,
    private val cover: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = id
        title = this@ComicDto.title
        thumbnail_url = cover?.let { baseUrl + it }
    }
}

@Serializable
class DetailDto(
    private val id: String,
    private val title: String,
    private val altTitles: List<String>? = null,
    private val author: String? = null,
    private val description: String? = null,
    private val cover: String? = null,
    private val format: String? = null,
    private val status: String? = null,
    private val genre: List<String> = emptyList(),
    private val themes: List<String> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = id
        title = this@DetailDto.title
        thumbnail_url = cover?.let { baseUrl + it }
        author = this@DetailDto.author?.takeIf { it.isNotBlank() }
        description = buildString {
            this@DetailDto.description?.takeIf { it.isNotBlank() }?.let(::append)
            if (!altTitles.isNullOrEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Alternative titles:\n")
                altTitles.joinTo(this, "\n") { "- $it" }
            }
        }
        genre = (listOfNotNull(format) + this@DetailDto.genre + themes).distinct().joinToString()
        status = when (this@DetailDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChapterDto(
    private val id: String,
    private val chapterNumber: Float,
    private val name: String,
    private val releaseDate: String? = null,
) {
    fun toSChapter(mangaId: String) = SChapter.create().apply {
        url = "$mangaId/$id"
        name = this@ChapterDto.name
        chapter_number = chapterNumber
        date_upload = Instant.tryParse(releaseDate)
    }
}

@Serializable
class PagesDto(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    val src: String,
)
