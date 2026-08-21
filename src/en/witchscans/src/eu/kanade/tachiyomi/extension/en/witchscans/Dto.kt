package eu.kanade.tachiyomi.extension.en.witchscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.string
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

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
    val tags: List<GenreDto> = emptyList(),
    val team: TeamDto? = null,
) {
    fun toSManga(baseUrl: String): SManga = toSManga(baseUrl, SManga.create())

    fun toSManga(baseUrl: String, manga: SManga): SManga = manga.apply {
        title = this@MangaDto.title
        thumbnail_url = coverUrl?.let { it.toAbsoluteUrl(baseUrl) }
        url = "comic/$slug"
        memo = buildJsonObject {
            put("type", "comic")
            put("slug", slug)
        }
        status = this@MangaDto.status.toSMangaStatus()
        author = team?.name
        genre = genres.joinToString { it.displayEmoji }
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

    val displayEmoji: String
        get() {
            val clean = displayName
            val emoji = GENRE_EMOJIS[clean.lowercase()] ?: GENRE_EMOJIS[genreSlug.lowercase()] ?: ""
            return if (emoji.isNotEmpty()) "$clean $emoji" else clean
        }

    val genreSlug: String
        get() = slug.ifBlank { genre?.slug.orEmpty() }

    companion object {
        private val GENRE_EMOJIS = mapOf(
            "action" to "\u2694\uFE0F",
            "adventure" to "\uD83C\uDF0D",
            "comedy" to "\uD83D\uDE02",
            "drama" to "\uD83C\uDFAD",
            "fantasy" to "\u2728",
            "horror" to "\uD83D\uDC80",
            "romance" to "\uD83D\uDC96",
            "sci-fi" to "\uD83D\uDE80",
            "slice of life" to "\uD83D\uDE0C",
            "supernatural" to "\uD83D\uDC7B",
            "thriller" to "\uD83D\uDE31",
            "mystery" to "\uD83D\uDD0D",
            "sports" to "\u26BD",
            "tragedy" to "\uD83D\uDE22",
            "psychological" to "\uD83E\uDDE0",
            "seinen" to "\uD83D\uDCAA",
            "josei" to "\uD83D\uDC69",
            "shoujo" to "\uD83C\uDF38",
            "shounen" to "\uD83D\uDD25",
            "manhwa" to "\uD83C\uDFF4",
            "manhua" to "\uD83C\uDFEF",
            "manga" to "\uD83D\uDCD6",
            "ecchi" to "\uD83D\uDE0F",
            "harem" to "\uD83D\uDC65",
            "isekai" to "\uD83D\uDD2E",
            "martial arts" to "\uD83E\uDD4B",
            "music" to "\uD83C\uDFB5",
            "school life" to "\uD83C\uDFEB",
            "workplace" to "\uD83C\uDFE2",
            "historical" to "\uD83C\uDFFB",
            "medical" to "\uD83C\uDFE5",
            "cooking" to "\uD83C\uDF73",
        )
    }
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
        date_upload = publishedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrDefault(0L) } ?: 0L
        chapter_number = number.toFloat()
        url = id
        memo = buildJsonObject {
            put("type", manga.memo["type"]?.string ?: "comic")
            put("slug", manga.memo["slug"]?.string ?: "")
            put("number", number.toString())
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
