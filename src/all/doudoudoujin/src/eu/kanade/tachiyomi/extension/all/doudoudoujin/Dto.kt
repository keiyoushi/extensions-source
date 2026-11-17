package eu.kanade.tachiyomi.extension.all.doudoudoujin

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.time.Instant

@Serializable
class SeriesListDto(val series: SeriesPageDto)

@Serializable
class SeriesPageDto(
    val items: List<SeriesDto>,
    val next: String? = null,
)

@Serializable
class SeriesDto(
    private val title: String,
    val slug: String,
    private val author: String? = null,
    private val description: String? = null,
    private val status: String? = null,
    private val thumbnailUrl: String? = null,
    private val genres: List<GenreDto> = emptyList(),
) {
    fun matches(query: String) = title.contains(query, ignoreCase = true) || author?.contains(query, ignoreCase = true) == true

    fun toSManga() = SManga.create().apply {
        url = "/series/$slug"
        title = this@SeriesDto.title
        author = this@SeriesDto.author
        description = MarkdownDescription.Builder().description(this@SeriesDto.description).build()
        genre = genres.joinToString { it.name }.ifEmpty { null }
        status = when (this@SeriesDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = thumbnailUrl
    }
}

@Serializable
class GenreDto(val name: String)

@Serializable
class SeriesDetailsDto(
    val series: SeriesDto,
    val mangas: List<MangaDto>,
)

@Serializable
class MangaDto(
    private val title: String,
    private val slug: String,
    private val language: String,
    private val chapterNumber: Float? = null,
    private val publishDate: String? = null,
) {
    // Each chapter comes in several languages, told apart by the scanlator field
    fun toSChapter() = SChapter.create().apply {
        url = "/mangas/$slug"
        name = title
        scanlator = Locale.forLanguageTag(language).let { it.getDisplayLanguage(it) }.replaceFirstChar { it.uppercase() }
        chapter_number = chapterNumber ?: -1f
        date_upload = Instant.tryParse(publishDate)
    }

    val number get() = chapterNumber ?: 0f
}

@Serializable
class ChapterSeriesDto(val manga: SeriesIdDto)

@Serializable
class SeriesIdDto(val seriesId: String)

@Serializable
class MangaPagesDto(val manga: PagesDto)

@Serializable
class PagesDto(val pages: List<PageDto>)

@Serializable
class PageDto(
    val pageNumber: Int,
    val imageUrl: String,
)
