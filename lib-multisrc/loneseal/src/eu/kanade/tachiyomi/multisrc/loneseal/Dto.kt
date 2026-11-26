package eu.kanade.tachiyomi.multisrc.loneseal

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class SearchResponseDto(
    val data: List<MangaDto>,
    @SerialName("total_pages") private val totalPages: Int,
) {
    fun toMangasPage(page: Int, urlLayout: UrlLayout) = MangasPage(
        data.map { it.toSManga(urlLayout) },
        page < totalPages,
    )
}

@Serializable
class MangaDto(
    private val title: String,
    val slug: String,
    @SerialName("poster_image_url") private val posterImageUrl: String?,
) {
    fun toSManga(urlLayout: UrlLayout) = SManga.create().apply {
        url = urlLayout.mangaUrl(slug)
        title = this@MangaDto.title.trim()
        thumbnail_url = posterImageUrl
    }
}

@Serializable
class GenreDto(
    val name: String,
    val slug: String,
)

@Serializable
class SeriesDetailDto(
    private val title: String,
    val slug: String,
    val synopsis: String?,
    @SerialName("poster_image_url") private val posterImageUrl: String?,
    @SerialName("comic_status") private val comicStatus: String?,
    @SerialName("author_name") private val authorName: String?,
    @SerialName("artist_name") private val artistName: String?,
    @SerialName("primary_genre") private val primaryGenre: String?,
    @SerialName("comic_subtype") private val comicSubtype: String?,
    @SerialName("alternative_titles") private val alternativeTitles: JsonElement?,
    @SerialName("rating_average") private val ratingAverage: String?,
    @SerialName("rating_count") private val ratingCount: Int?,
    @SerialName("age_rating") private val ageRating: String?,
    @SerialName("release_year") private val releaseYear: Int?,
    @SerialName("publisher_name") private val publisherName: String?,
    val genres: List<GenreDto>,
    val units: List<ChapterDto>,
) {
    fun toSManga(urlLayout: UrlLayout) = SManga.create().apply {
        url = urlLayout.mangaUrl(slug)
        title = this@SeriesDetailDto.title.trim()
        thumbnail_url = posterImageUrl
        author = authorName
        artist = artistName
        description = MarkdownDescription.Builder()
            .rating(ratingAverage?.toDoubleOrNull()?.takeIf { it > 0 && (ratingCount ?: 0) > 0 }, votes = ratingCount)
            .info(comicSubtype?.lowercase()?.replaceFirstChar { it.uppercase() }, ageRating, releaseYear?.toString())
            .field("Publisher", publisherName)
            .separator()
            .description(synopsis?.let { Jsoup.parseBodyFragment(it).body() }, keepNewlines = true)
            .altTitles(alternativeTitles.toTitles())
            .build()
        genre = buildList {
            if (primaryGenre != null) add(primaryGenre)
            addAll(genres.map { it.name })
        }.distinct().joinToString().ifEmpty { null }
        status = comicStatus.parseStatus()
    }
}

@Serializable
class ChapterDto(
    private val slug: String,
    private val number: String,
    private val title: String?,
    @SerialName("created_at") private val createdAt: String?,
    @SerialName("is_locked") private val isLocked: Boolean = false,
) {
    fun toSChapter(seriesSlug: String, urlLayout: UrlLayout, includeChapterTitle: Boolean) = SChapter.create().apply {
        url = urlLayout.chapterUrl(seriesSlug, slug)
        name = buildString {
            if (isLocked) append("🔒 ")
            append("Chapter ${formatChapterNumber(number)}")
            if (includeChapterTitle && title != null) append(" - $title")
        }
        chapter_number = number.toFloatOrNull() ?: -1f
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class ChapterPagesResponseDto(
    val chapter: ChapterPagesDto = ChapterPagesDto(),
)

@Serializable
class ChapterPagesDto(
    @SerialName("login_required") val loginRequired: Boolean = false,
    @SerialName("password_required") val passwordRequired: Boolean = false,
    @SerialName("is_locked") val isLocked: Boolean = false,
    @SerialName("rate_limited") val rateLimited: Boolean = false,
    @SerialName("is_scrambled") val isScrambled: Boolean = false,
    val pages: List<PageDto> = emptyList(),
)

@Serializable
class PageDto(
    @SerialName("image_url") val imageUrl: String,
    @SerialName("scramble_data") val scrambleData: String?,
)

@Serializable
class HomeSectionsDto(
    @SerialName("latest_comic_updates") val latestComicUpdates: List<LatestComicUpdateDto> = emptyList(),
)

@Serializable
class LatestComicUpdateDto(
    @SerialName("series_title") private val seriesTitle: String,
    @SerialName("series_slug") private val seriesSlug: String,
    @SerialName("poster_image_url") private val posterImageUrl: String?,
) {
    fun toSManga(urlLayout: UrlLayout) = SManga.create().apply {
        url = urlLayout.mangaUrl(seriesSlug)
        title = seriesTitle.trim()
        thumbnail_url = posterImageUrl
    }
}

private fun formatChapterNumber(number: String): String = number.removeSuffix(".00")

private fun String?.parseStatus() = when (this?.lowercase()) {
    "ongoing" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    "hiatus" -> SManga.ON_HIATUS
    else -> SManga.UNKNOWN
}

// Some sites send the list as a JSON-encoded string
private fun JsonElement?.toTitles(): List<String> = when {
    this is JsonArray -> parseAs<List<String>>()
    this is JsonPrimitive && isString -> content.parseAs<List<String>>()
    else -> emptyList()
}
