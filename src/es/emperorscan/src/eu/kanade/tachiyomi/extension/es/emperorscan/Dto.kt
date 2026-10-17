package eu.kanade.tachiyomi.extension.es.emperorscan

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

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
)

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
    private val authors: List<NamedDto>,
    private val artists: List<NamedDto>,
) {
    fun applyTo(manga: SManga) = manga.apply {
        title = this@SeriesDto.title
        thumbnail_url = cover
        author = authors.joinToString { it.name }.ifEmpty { null }
        artist = artists.joinToString { it.name }.ifEmpty { null }
        description = buildString {
            synopsis?.let(::append)
            if (altTitles.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Nombres alternativos: ", altTitles.joinToString())
            }
        }.ifEmpty { null }
        genre = (listOfNotNull(type?.replaceFirstChar(Char::uppercase)) + genres.map { it.name }).joinToString().ifEmpty { null }
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
    @SerialName("published_at") private val publishedAt: String? = null,
    val locked: Boolean,
) {
    fun toSChapter(seriesUrl: String) = SChapter.create().apply {
        url = "$seriesUrl$slug/"
        name = label
        date_upload = Instant.tryParse(publishedAt)
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
)

@Serializable
class ReaderResult(
    val pages: List<PageDto>? = null,
    val error: String? = null,
)

@Serializable
class HomeProps(val genres: List<NamedDto>)
