package eu.kanade.tachiyomi.extension.all.swarm

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
class MangaListDto(
    val data: List<MangaDto>,
    val total: Int? = null,
)

@Serializable
class MangaDto(
    private val title: String,
    @SerialName("url") private val slug: String,
    private val cover: String? = null,
    private val covers: List<String>? = null,
    val nsfw: Boolean? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = slug
        title = this@MangaDto.title
        thumbnail_url = coverUrl(baseUrl, covers?.firstOrNull() ?: cover)
    }
}

@Serializable
class LatestDto(
    val data: List<LatestMangaDto>,
    val hasMore: Boolean,
)

@Serializable
class LatestMangaDto(
    private val title: String,
    @SerialName("url") private val slug: String,
    private val cover: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = slug
        title = this@LatestMangaDto.title
        thumbnail_url = coverUrl(baseUrl, cover)
    }
}

// The comic page's `initialData`
@Serializable
class ComicPageDto(
    val manga: ComicDto,
    val chapters: List<ChapterDto>,
)

@Serializable
class ComicDto(
    private val title: String,
    @SerialName("url") private val slug: String,
    private val cover: String? = null,
    private val covers: List<String>? = null,
    private val description: String? = null,
    private val genres: List<String>? = null,
    private val tags: List<String>? = null,
    private val author: String? = null,
    private val authors: List<String>? = null,
    private val artist: String? = null,
    private val artists: List<String>? = null,
    private val status: String? = null,
    private val type: String? = null,
    private val demographic: String? = null,
    private val year: Int? = null,
    private val publishers: List<String>? = null,
    private val anime: AnimeDto? = null,
    @SerialName("alt_titles") private val altTitles: List<String>? = null,
    private val urls: List<String>? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = slug
        title = this@ComicDto.title
        thumbnail_url = coverUrl(baseUrl, covers?.firstOrNull() ?: cover)
        author = authors?.takeIf { it.isNotEmpty() }?.joinToString() ?: this@ComicDto.author?.takeIf { it.isNotBlank() }
        artist = artists?.takeIf { it.isNotEmpty() }?.joinToString() ?: this@ComicDto.artist?.takeIf { it.isNotBlank() }
        val typeName = type?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
        // Also holds numbers and the type ("1", "MANHWA")
        val demographicName = demographic?.takeIf { it.lowercase() in DEMOGRAPHICS }?.replaceFirstChar { it.uppercase() }
        genre = (listOfNotNull(typeName, demographicName) + genres.orEmpty() + tags.orEmpty())
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy(String::lowercase)
            .joinToString()
            .ifEmpty { null }
        status = when (this@ComicDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled", "discontinued" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(this@ComicDto.description)
            .field("Year", year?.takeIf { it > 0 }?.toString())
            .field("Publishers", publishers.orEmpty())
            .field("Anime", anime?.let { listOfNotNull(it.start, it.end).joinToString(" to ") })
            .altTitles(altTitles.orEmpty().filter { it.trim() != title })
            .links(urls.orEmpty().mapNotNull(::parseLink).distinctBy { it.second })
            .build()
    }
}

@Serializable
class AnimeDto(val start: String? = null, val end: String? = null)

private val DEMOGRAPHICS = setOf("shounen", "shoujo", "seinen", "josei")

// A plain url, "[Name](url)", or "[{'id': 'mangaupdates', 'name': 'MangaUpdates', 'type': 'DATABASE'}](url)"
private fun parseLink(entry: String): Pair<String, String>? {
    val text = entry.trim()
    (DICT_LINK_REGEX.matchEntire(text) ?: MARKDOWN_LINK_REGEX.matchEntire(text))?.let { match ->
        return match.groupValues[1] to match.groupValues[2]
    }
    val url = text.toHttpUrlOrNull() ?: return null
    return url.host.removePrefix("www.") to text
}

private val DICT_LINK_REGEX = Regex("""\[\{.*'name': '([^']+)'.*\}\]\((https?://[^)\s]+)\)""")

private val MARKDOWN_LINK_REGEX = Regex("""\[([^\]\[]+)\]\((https?://[^)\s]+)\)""")

// The site's cover rule: its own storage host is served under /covers/, other hosts through its image proxy
private fun coverUrl(baseUrl: String, cover: String?): String? {
    val url = cover?.toHttpUrlOrNull() ?: return null
    return if (url.host == "cum.swarm.ws") {
        baseUrl.toHttpUrl().newBuilder().addPathSegment("covers").addEncodedPathSegments(url.encodedPath.removePrefix("/")).build().toString()
    } else {
        baseUrl.toHttpUrl().newBuilder().addPathSegments("api/image-proxy").addQueryParameter("url", cover).build().toString()
    }
}

@Serializable
class ChapterDto(
    val id: String,
    val lang: String,
    val number: String,
    val rank: String? = null,
    @SerialName("published_date") private val publishedDate: String? = null,
    @SerialName("source_name") private val sourceName: String? = null,
    private val source: String? = null,
    @SerialName("scan_group") private val scanGroup: String? = null,
) {
    // The site's Uploader column, "swarmws" (its own copies) when a row names no source
    val uploader get() = sourceName ?: source ?: scanGroup ?: "swarmws"

    // A numbered source ("official2", "mangadex3") is another copy from the same origin
    val group get() = uploader.trimEnd { it.isDigit() }

    val copyIndex get() = uploader.takeLastWhile { it.isDigit() }.toIntOrNull() ?: 1

    fun toSChapter(slug: String) = SChapter.create().apply {
        url = id
        name = "Chapter $number"
        scanlator = uploader
        // "0001-01-01" marks an unknown date
        date_upload = DATE_FORMAT.tryParseDate(publishedDate).coerceAtLeast(0L)
        memo = buildJsonObject {
            put("slug", slug)
            put("number", number)
            rank?.let { put("rank", it) }
        }
    }
}

private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd['T'HH:mm:ss.SSSX]", Locale.ROOT).withZone(ZoneOffset.UTC)

@Serializable
class PageRequestDto(private val chapterId: String)

@Serializable
class PagesPayloadDto(private val payload: String)

@Serializable
class PagesDto(val chapter: PagesChapterDto)

@Serializable
class PagesChapterDto(val data: List<String>)

@Serializable
class GenresDto(val genres: List<String>)

@Serializable
class TypesDto(val types: List<String>)

@Serializable
class FilterDataDto(val genres: List<String>, val types: List<String>)
