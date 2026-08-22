package eu.kanade.tachiyomi.extension.en.witchscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.string
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class BrowseDto(
    val initialSeries: List<MangaDto>,
    val initialHasMore: Boolean = false,
)

@Serializable
class GenreListDto(
    val genres: List<GenreDto> = emptyList(),
)

@Serializable
class DetailDto(
    val series: MangaDto,
    val chapters: List<ChapterDto> = emptyList(),
    val totalPages: Int = 1,
)

@Serializable
class ChapterDetailDto(
    val chapter: ChapterPagesDto,
)

@Serializable
class ChapterPagesDto(
    val pages: List<PageDto> = emptyList(),
)

@Serializable
class PageDto(
    val imageUrl: String? = null,
    val kind: String = "CONTENT",
)

@Serializable
class MangaDto(
    val id: String,
    val title: String,
    @SerialName("coverImage")
    val coverUrl: String? = null,
    val slug: String = "",
    val status: String = "",
    val description: String? = null,
    val genres: List<GenreDto> = emptyList(),
    val team: TeamDto? = null,
) {
    fun toSManga(baseUrl: String): SManga = toSManga(baseUrl, SManga.create())

    fun toSManga(baseUrl: String, manga: SManga): SManga = manga.apply {
        title = this@MangaDto.title
        thumbnail_url = coverUrl?.let { it.toAbsoluteUrl(baseUrl) }
        url = id
        memo = buildJsonObject {
            put("slug", slug)
        }
        status = this@MangaDto.status.toSMangaStatus()
        author = team?.name
        genre = genres.joinToString { it.displayName }
        description = this@MangaDto.description
    }
}

@Serializable
class GenreDto(
    val name: String = "",
    val slug: String = "",
    val genre: GenreSlugDto? = null,
) {
    val displayName: String
        get() = (if (name.isNotBlank()) name else genre?.slug.orEmpty()).stripEmoji()

    val genreSlug: String
        get() = slug.ifBlank { genre?.slug.orEmpty() }
}

@Serializable
class GenreSlugDto(
    val slug: String = "",
)

@Serializable
class TeamDto(
    val name: String? = null,
)

@Serializable
class ChapterDto(
    val id: String,
    val number: Int,
    val title: String? = null,
    val publishedAt: String? = null,
    @SerialName("isLocked")
    val isLocked: Boolean = false,
) {
    fun toSChapter(manga: SManga): SChapter = SChapter.create().apply {
        name = if (title.isNullOrBlank() || title == number.toString()) "Chapter $number" else title
        if (isLocked) name = "\uD83D\uDD12 $name"
        date_upload = publishedAt?.let { Instant.tryParse(it) } ?: 0L
        chapter_number = number.toFloat()
        val mangaSlug = manga.memo["slug"]?.string.orEmpty()
        url = "comic/$mangaSlug/chapter/$number"
        memo = buildJsonObject {
            put("isLocked", isLocked)
        }
    }
}

fun String.toSMangaStatus(): Int = when (this) {
    "ONGOING" -> SManga.ONGOING
    "COMPLETED" -> SManga.COMPLETED
    "HIATUS" -> SManga.ON_HIATUS
    "CANCELLED" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}

fun String.stripEmoji(): String = replace(Regex("[^\\p{ASCII}\\p{L}0-9\\- ]+")) { "" }.trim()

fun String.toAbsoluteUrl(baseUrl: String): String = if (startsWith("http")) this else "$baseUrl$this"
