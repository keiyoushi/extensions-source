package eu.kanade.tachiyomi.extension.en.komix

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import kotlin.time.Instant

@Serializable
class BrowseDto(
    val initialResults: List<BrowseMangaDto>,
    val totalCount: Int,
    val page: Int,
    val pageSize: Int,
)

@Serializable
class BrowseMangaDto(
    private val title: String,
    private val slug: String,
    @SerialName("cover_image") private val coverImage: String? = null,
) {
    fun toSManga(baseUrl: HttpUrl) = SManga.create().apply {
        url = slug
        title = this@BrowseMangaDto.title.trim()
        thumbnail_url = coverImage?.let { baseUrl.resolve(it)?.toString() }
    }
}

@Serializable
class GenresDto(
    val allGenres: List<GenreDto>,
)

@Serializable
class GenreDto(
    val slug: String,
    val name: String,
)

@Serializable
class MangaPageDto(
    val manga: MangaDto,
    val chapters: List<ChapterDto>,
)

@Serializable
class LdJsonDto(
    @SerialName("@graph") val graph: List<LdNodeDto>? = null,
)

@Serializable
class LdNodeDto(
    @SerialName("@type") val type: String? = null,
    val description: String? = null,
)

@Serializable
class MangaDto(
    private val title: String,
    val slug: String,
    private val author: String? = null,
    private val artist: String? = null,
    private val status: String? = null,
    private val type: String? = null,
    @SerialName("cover_image") private val coverImage: String? = null,
    private val genres: List<GenreDto>? = null,
    @SerialName("alternative_titles") private val alternativeTitles: List<String>? = null,
) {
    fun toSManga(baseUrl: HttpUrl, synopsis: String?) = SManga.create().apply {
        url = slug
        title = this@MangaDto.title.trim()
        author = this@MangaDto.author
        artist = this@MangaDto.artist
        description = buildString {
            synopsis?.let { append(it.trim()) }
            if (!alternativeTitles.isNullOrEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Alternative titles:\n")
                alternativeTitles.forEach { append("- ", it.trim(), "\n") }
            }
        }.trim().ifEmpty { null }
        genre = (listOfNotNull(type?.replaceFirstChar(Char::uppercase)) + genres.orEmpty().map { it.name })
            .distinct()
            .ifEmpty { null }
            ?.joinToString()
        status = when (this@MangaDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled", "dropped" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = coverImage?.let { baseUrl.resolve(it)?.toString() }
    }
}

@Serializable
class ChapterDto(
    @SerialName("chapter_number") private val chapterNumber: Double,
    private val title: String,
    private val group: String? = null,
    @SerialName("created_at") private val createdAt: String? = null,
    @SerialName("release_date") private val releaseDate: String? = null,
) {
    fun toSChapter(baseUrl: HttpUrl, mangaSlug: String) = SChapter.create().apply {
        val chapterUrl = baseUrl.newBuilder()
            .addPathSegment("title")
            .addPathSegment(mangaSlug)
            .addPathSegment("chapter")
            .addPathSegment(chapterNumber.toString().removeSuffix(".0"))
            .apply { group?.let { addQueryParameter("group", it) } }
            .build()
        url = buildString {
            append(chapterUrl.encodedPath)
            chapterUrl.encodedQuery?.let { append('?', it) }
        }
        name = title
        scanlator = group?.takeUnless { it == "(none)" }
        date_upload = Instant.tryParse(releaseDate ?: createdAt)
    }
}

@Serializable
class ReaderPropsDto(
    val chapter: ReaderChapterDto,
)

@Serializable
class ReaderChapterDto(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    val enc: String,
)
