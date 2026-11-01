package eu.kanade.tachiyomi.multisrc.imperiomanhwa

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class InertiaPage<T>(
    val component: String,
    val props: T,
)

@Serializable
class ListingProps(
    val series: List<MangaDto>,
    val pagination: PaginationDto,
)

@Serializable
class PaginationDto(val next: String?)

@Serializable
class MangaDto(
    private val url: String,
    private val title: String,
    private val cover: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = this@MangaDto.url
        title = this@MangaDto.title
        thumbnail_url = cover
    }
}

@Serializable
class SeriesProps(
    val series: SeriesDto,
    val chapters: List<ChapterDto>,
    @SerialName("chapters_pagination") val chaptersPagination: ChaptersPaginationDto,
    val paths: PathsDto,
    @SerialName("tags_label") val tagsLabel: String?,
)

@Serializable
class PathsDto(val chapters: String)

@Serializable
class SeriesDto(
    val url: String,
    private val title: String,
    private val type: String?,
    private val status: String?,
    @SerialName("cover_canonical") private val cover: String?,
    @SerialName("alt_titles") private val altTitles: List<String>,
    private val synopsis: String?,
    private val genres: List<NamedDto>,
    private val tags: List<NamedDto>,
    private val authors: List<NamedDto>,
    private val artists: List<NamedDto>,
) {
    fun applyTo(manga: SManga, altNamesLabel: String, withTags: Boolean) = manga.apply {
        title = this@SeriesDto.title
        thumbnail_url = cover
        author = authors.joinToString { it.name }.ifEmpty { null }
        artist = artists.joinToString { it.name }.ifEmpty { null }
        description = buildString {
            synopsis?.let(::append)
            if (altTitles.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(altNamesLabel, ": ", altTitles.joinToString())
            }
        }.ifEmpty { null }
        genre = (listOfNotNull(type?.replaceFirstChar(Char::uppercase)) + genres.map { it.name } + tags.takeIf { withTags }.orEmpty().map { it.name })
            .distinctBy(String::lowercase).joinToString().ifEmpty { null }
        status = when (this@SeriesDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class NamedDto(val slug: String, val name: String)

@Serializable
class ChaptersPaginationDto(val last: Int)

@Serializable
class ChapterListDto(val items: List<ChapterDto>)

@Serializable
class ChapterDto(
    val slug: String,
    private val label: String,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("published_label") val publishedLabel: String? = null,
    val locked: Boolean,
) {
    fun toSChapter(seriesUrl: String, date: Long) = SChapter.create().apply {
        url = "$seriesUrl$slug/"
        name = if (locked) "🔒 $label" else label
        date_upload = date
    }
}

@Serializable
class ReaderProps(
    val chapter: ReaderChapterDto,
    val series: ReaderSeriesDto,
    val pages: List<PageDto>,
    val dims: List<JsonElement>,
    val reader: JsonElement,
)

@Serializable
class ReaderChapterDto(val id: Int)

@Serializable
class ReaderSeriesDto(val slug: String)

@Serializable
class PageDto(
    val n: Int,
    private val src: String?,
    private val srcset: String,
) {
    /** Full-size image, else the widest srcset entry */
    val imageUrl get() = src ?: srcset.split(", ").last().substringBefore(' ')
}

@Serializable
class ReaderInput(
    val entry: String,
    val reader: JsonElement,
    val chapterId: Int,
    val seriesSlug: String,
    var token: String? = null,
)

@Serializable
class ReaderResult(
    val pages: List<PageDto>? = null,
    val error: String? = null,
    val sitekey: String? = null,
    val action: String? = null,
    val cdata: String? = null,
)

@Serializable
class HomeProps(
    val genres: List<NamedDto>,
    val content: ContentDto,
)

@Serializable
class ContentDto(val sections: List<SectionDto> = emptyList())

@Serializable
class SectionDto(val code: String, val label: String)

@Serializable
class FilterData(
    val genres: List<NamedDto>,
    val sections: List<SectionDto>,
)
