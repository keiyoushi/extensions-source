package eu.kanade.tachiyomi.extension.en.schalescans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class SeriesDto(
    val id: String,
    private val title: String,
    private val slug: String,
    @SerialName("cover_url") private val coverUrl: String? = null,
    @SerialName("is_r18") private val isR18: Boolean,
    @SerialName("created_at") private val createdAt: Instant,
    private val chapters: List<LatestChapterDto>,
    @SerialName("content_rating") private val contentRating: String? = null,
    @SerialName("series_genres") private val seriesGenres: List<SeriesGenreDto> = emptyList(),
) {
    val hasChapters get() = chapters.isNotEmpty()

    val isAdult get() = contentRating in ADULT_RATINGS

    val genres get() = seriesGenres.mapTo(HashSet()) { it.genres.name }

    val latestAt get() = chapters.firstOrNull()?.date ?: createdAt

    fun toSManga() = SManga.create().apply {
        url = id
        title = this@SeriesDto.title
        thumbnail_url = coverUrl
        memo = buildJsonObject {
            put(MEMO_SLUG, slug)
            put(MEMO_R18, isR18)
        }
    }
}

@Serializable
class LatestChapterDto(
    @SerialName("published_at") private val publishedAt: Instant? = null,
    @SerialName("created_at") private val createdAt: Instant,
) {
    val date get() = publishedAt ?: createdAt
}

@Serializable
class SeriesDetailsDto(
    private val id: String,
    private val title: String,
    private val slug: String,
    @SerialName("cover_url") private val coverUrl: String? = null,
    private val description: String? = null,
    private val status: String? = null,
    @SerialName("is_r18") private val isR18: Boolean,
    @SerialName("alt_titles") private val altTitles: List<String>? = null,
    private val authors: List<AuthorDto>? = null,
    @SerialName("series_genres") private val seriesGenres: List<SeriesGenreDto>,
    private val chapters: List<ChapterDto>,
    @SerialName("mangadex_id") private val mangadexId: String? = null,
    @SerialName("anilist_id") private val anilistId: Long? = null,
    @SerialName("mal_id") private val malId: Long? = null,
    @SerialName("store_links") private val storeLinks: List<LinkDto>? = null,
    @SerialName("read_elsewhere") private val readElsewhere: List<ReadElsewhereDto>? = null,
) {
    private fun links() = buildList {
        mangadexId?.let { add("[MangaDex](https://mangadex.org/title/$it)") }
        anilistId?.let { add("[AniList](https://anilist.co/manga/$it)") }
        malId?.let { add("[MyAnimeList](https://myanimelist.net/manga/$it)") }
        storeLinks?.forEach { add("[${it.label}](${it.url})") }
        readElsewhere?.forEach { add(it.toString()) }
    }

    fun toSManga() = SManga.create().apply {
        url = id
        title = this@SeriesDetailsDto.title
        thumbnail_url = coverUrl
        author = authors?.filter { it.role == "author" }?.joinToString { it.name }?.ifEmpty { null }
        artist = authors?.filter { it.role == "artist" }?.joinToString { it.name }?.ifEmpty { null }
        genre = seriesGenres.joinToString { it.genres.name.toTitleCase() }
        status = when (this@SeriesDetailsDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed", "oneshot" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "dropped" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = buildString {
            this@SeriesDetailsDto.description?.let { append(it.trim()) }
            if (!altTitles.isNullOrEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Alternative titles:\n")
                altTitles.joinTo(this, "\n") { "- $it" }
            }
            val links = links()
            if (links.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Links:\n")
                links.joinTo(this, "\n") { "- $it" }
            }
        }
        memo = buildJsonObject {
            put(MEMO_SLUG, slug)
            put(MEMO_R18, isR18)
        }
    }

    fun toSChapterList() = chapters.map { it.toSChapter(slug, isR18) }
}

@Serializable
class AuthorDto(
    val name: String,
    val role: String,
)

@Serializable
class LinkDto(
    val label: String,
    val url: String,
)

@Serializable
class ReadElsewhereDto(
    private val label: String,
    private val url: String,
    private val from: Float? = null,
    private val to: Float? = null,
) {
    override fun toString() = buildString {
        append("[$label]($url)")
        if (from != null && to != null) {
            append(" (Ch. ${from.toString().removeSuffix(".0")}–${to.toString().removeSuffix(".0")})")
        }
    }
}

@Serializable
class SeriesGenreDto(
    val genres: GenreDto,
)

@Serializable
class GenreDto(
    val name: String,
)

@Serializable
class ChapterDto(
    private val id: String,
    @SerialName("chapter_number") private val chapterNumber: Float,
    @SerialName("volume_number") private val volumeNumber: Float? = null,
    private val title: String? = null,
    @SerialName("published_at") private val publishedAt: Instant? = null,
    @SerialName("created_at") private val createdAt: Instant,
) {
    fun toSChapter(seriesSlug: String, isR18: Boolean) = SChapter.create().apply {
        val number = chapterNumber.toString().removeSuffix(".0")
        url = id
        name = buildString {
            volumeNumber?.let { append("Vol. ${it.toString().removeSuffix(".0")} ") }
            append("Ch. $number")
            title?.let { append(": $it") }
        }
        chapter_number = chapterNumber
        date_upload = (publishedAt ?: createdAt).toEpochMilliseconds()
        memo = buildJsonObject {
            put(MEMO_SLUG, seriesSlug)
            put(MEMO_NUMBER, number)
            put(MEMO_R18, isR18)
        }
    }
}

@Serializable
class ChapterPagesDto(
    val series: SeriesFlagDto,
    @SerialName("chapter_pages") val pages: List<PageDto>,
)

@Serializable
class SeriesFlagDto(
    @SerialName("is_r18") val isR18: Boolean,
)

@Serializable
class PageDto(
    @SerialName("object_key") val objectKey: String,
)

// R18 site tags are stored lowercase
fun String.toTitleCase() = split(' ').joinToString(" ") { it.replaceFirstChar(Char::titlecase) }

private val ADULT_RATINGS = setOf("erotica", "pornographic")

const val MEMO_R18 = "r18"
const val MEMO_SLUG = "slug"
const val MEMO_NUMBER = "number"
