package eu.kanade.tachiyomi.extension.ar.mangatuk

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class SeriesListDto(
    val data: List<SeriesDto>,
    val total: Int,
)

@Serializable
class SeriesDto(
    private val id: String,
    val slug: String,
    private val title: String,
    private val coverImage: String? = null,
    private val associatedNames: String? = null,
    private val description: String? = null,
    private val author: String? = null,
    private val status: String? = null,
    private val type: String? = null,
    private val genres: List<GenreDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = id
        title = this@SeriesDto.title
        thumbnail_url = coverImage
        memo = buildJsonObject { put("slug", slug) }
    }

    fun toSMangaDetails() = toSManga().apply {
        author = this@SeriesDto.author
        genre = (listOfNotNull(type) + genres.map { it.name }).joinToString()
        description = buildString {
            this@SeriesDto.description?.let { append(it) }
            associatedNames?.takeIf(String::isNotBlank)?.let {
                if (isNotEmpty()) append("\n\n")
                append("أسماء أخرى:\n", it)
            }
        }
        status = when (this@SeriesDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }
}

@Serializable
class GenreDto(val name: String)

@Serializable
class ChapterDto(
    private val id: String,
    private val number: String,
    private val title: String? = null,
    private val slug: String,
    private val publishedAt: String? = null,
    private val coinAccess: CoinAccessDto? = null,
) {
    val locked get() = coinAccess?.locked == true

    fun toSChapter(seriesSlug: String) = SChapter.create().apply {
        url = id
        name = buildString {
            append("الفصل ", number)
            title?.takeIf(String::isNotBlank)?.let { append(" - ", it) }
        }
        chapter_number = number.toFloatOrNull() ?: -1f
        date_upload = Instant.tryParse(publishedAt)
        memo = buildJsonObject {
            put("series", seriesSlug)
            put("slug", slug)
        }
    }
}

@Serializable
class CoinAccessDto(val locked: Boolean = false)

@Serializable
class PageDto(val imageUrl: String)
