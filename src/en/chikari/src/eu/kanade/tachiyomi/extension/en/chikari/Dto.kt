package eu.kanade.tachiyomi.extension.en.chikari

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class SeriesListDto(
    val items: List<SeriesDto>,
    val total: Int,
)

@Serializable
class SeriesDto(
    private val slug: String,
    private val title: String,
    @SerialName("cover_url") private val coverUrl: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = "/series/$slug"
        title = this@SeriesDto.title
        thumbnail_url = coverUrl
    }
}

@Serializable
class SeriesDetailsDto(
    private val slug: String,
    private val title: String,
    private val type: String?,
    private val status: String?,
    private val description: String?,
    private val year: Int?,
    @SerialName("cover_url") private val coverUrl: String?,
    private val rating: Double?,
    @SerialName("rating_count") private val ratingCount: Int?,
    private val genres: List<GenreDto>,
    private val authors: List<AuthorDto>,
    @SerialName("alt_titles") private val altTitles: List<String>,
    @SerialName("anilist_id") private val anilistId: Int?,
    @SerialName("mal_id") private val malId: Int?,
    @SerialName("mangaupdates_id") private val mangaUpdatesId: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = "/series/$slug"
        title = this@SeriesDetailsDto.title
        thumbnail_url = coverUrl
        author = authors.filter { it.role != "artist" }.joinToString { it.name }.ifEmpty { null }
        artist = authors.filter { it.role == "artist" }.joinToString { it.name }.ifEmpty { null }
        genre = (listOfNotNull(type?.replaceFirstChar { it.uppercase() }) + genres.map { it.name }).joinToString().ifEmpty { null }
        status = when (this@SeriesDetailsDto.status) {
            "releasing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .rating(rating, votes = ratingCount)
            .info(year?.toString())
            .markdown(this@SeriesDetailsDto.description)
            .altTitles(altTitles)
            .links(
                listOf(
                    "AniList" to anilistId?.let { "https://anilist.co/manga/$it" },
                    "MyAnimeList" to malId?.let { "https://myanimelist.net/manga/$it" },
                    "MangaUpdates" to mangaUpdatesId?.let { "https://www.mangaupdates.com/series/$it" },
                ),
            )
            .build()
    }
}

@Serializable
class GenreDto(val name: String)

@Serializable
class AuthorDto(
    val name: String,
    val role: String,
)

@Serializable
class ChapterListDto(
    val items: List<ChapterDto>,
    val total: Int,
)

@Serializable
class ChapterDto(
    private val number: Double?,
    private val title: String?,
    @SerialName("created_at") private val createdAt: String?,
    private val scans: List<ScanDto>,
) {
    // A chapter some groups translated is listed once per group, as the site's reader offers them
    fun toSChapters(seriesUrl: String): List<SChapter> {
        val token = number?.toString()?.removeSuffix(".0") ?: "oneshot"
        val chapter = SChapter.create().apply {
            url = "$seriesUrl/$token"
            name = buildString {
                append(if (number == null) "Oneshot" else "Chapter $token")
                // Titles often repeat the number ("Chapter 1 Prologue"), which the site leaves out too
                title?.replace(TITLE_PREFIX_REGEX, "")?.takeIf { it.isNotBlank() }?.let { append(": ", it.trim()) }
            }
            number?.let { chapter_number = it.toFloat() }
            date_upload = Instant.tryParse(createdAt)
        }
        if (scans.isEmpty()) return listOf(chapter)
        return scans.map { scan ->
            SChapter.create().apply {
                url = "${chapter.url}?scan=${scan.slug}"
                name = chapter.name
                chapter_number = chapter.chapter_number
                date_upload = chapter.date_upload
                scanlator = scan.name
            }
        }
    }

    companion object {
        private val TITLE_PREFIX_REGEX = Regex("""^\s*(?:(?:chapter|ch\.?)\s*[\d.]+\s*[\p{Pd}:.]?\s*|[\d.]+\s*:\s*)""", RegexOption.IGNORE_CASE)
    }
}

@Serializable
class ScanDto(
    val slug: String,
    val name: String,
)

@Serializable
class PagesDto(val pages: List<String>)
