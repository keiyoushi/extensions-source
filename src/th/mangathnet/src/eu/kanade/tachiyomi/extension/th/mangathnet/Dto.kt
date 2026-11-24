package eu.kanade.tachiyomi.extension.th.mangathnet

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val CDN_URL = "https://cdn.manga-th.net"

// Times are in UTC, without an offset
private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

// The JSON-LD gives some covers as a resized copy: /_img/v2/<size>/<path>.jpg
private val RESIZED_IMAGE_REGEX = Regex("""/_img/v2/[^/]+(/.+)\.jpg$""")

@Serializable
class LibraryEntryDto(
    private val slug: String,
    val contentType: String,
    val title: String,
    private val searchText: String,
    private val coverUrl: String? = null,
    private val status: String,
    val tags: List<String>,
    val totalViews: Long,
    val bookmarkCount: Long,
    private val ratingAverage: Double? = null,
    private val ratingCount: Int,
    private val updatedAt: String,
    private val latestPublishedAt: String? = null,
) {
    // Same date format everywhere, so the text sorts like the dates
    val updated get() = latestPublishedAt ?: updatedAt

    val rating get() = if (ratingCount > 0) ratingAverage ?: 0.0 else -1.0

    fun matches(text: String, status: String?, tags: List<String>) = searchText.contains(text) &&
        (status == null || this.status == status) &&
        tags.all { tag -> this.tags.any { it.lowercase() == tag } }

    fun toSManga() = SManga.create().apply {
        url = "/$contentType/$slug"
        title = this@LibraryEntryDto.title
        thumbnail_url = coverUrl?.let { CDN_URL + it.removePrefix("/api/assets") }
    }
}

@Serializable
class SeriesDto(
    private val chapters: List<ChapterDto>,
    private val synopsis: String? = null,
    private val tags: List<String>,
    private val translator: String? = null,
    private val coinPrices: Map<String, JsonElement> = emptyMap(),
) {
    fun toSManga(path: String, info: JsonLdSeriesDto, artist: String?) = SManga.create().apply {
        url = path
        title = info.name!!
        thumbnail_url = info.image?.replace(RESIZED_IMAGE_REGEX, "$1")
        author = info.author?.name
        this.artist = artist
        genre = (listOf(path.split('/')[1].replaceFirstChar { it.uppercase() }) + tags)
            .distinctBy { it.lowercase() }
            .joinToString()
        status = when (info.creativeWorkStatus) {
            "ongoing" -> SManga.ONGOING
            "complete" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(synopsis)
            .field("Translator", translator)
            .altTitles(info.alternateName)
            .build()
    }

    // The site marks the chapters sold for coins with their price
    fun toSChapters() = chapters.map { it.toSChapter(it.id in coinPrices) }
}

@Serializable
class ChapterDto(
    val id: String,
    private val chapterNumber: Float,
    private val title: String? = null,
    private val publishedAt: String? = null,
) {
    fun toSChapter(locked: Boolean) = SChapter.create().apply {
        url = "/chapter/$id"
        val label = title?.takeIf { it.isNotEmpty() } ?: "Chapter ${chapterNumber.toString().removeSuffix(".0")}"
        name = if (locked) "🔒 $label" else label
        chapter_number = chapterNumber
        date_upload = DATE_FORMAT.tryParseDateTime(publishedAt, ZoneOffset.UTC)
    }
}

@Serializable
class ChapterDataDto(
    private val content: ContentDto,
    private val pages: List<PageDto>,
) {
    val contentType get() = content.contentType

    val path get() = "/${content.contentType}/${content.slug}"

    fun toPages() = pages.mapNotNull { it.url }.mapIndexed { index, url -> Page(index, imageUrl = url) }
}

@Serializable
class ContentDto(
    val contentType: String,
    val slug: String,
)

@Serializable
class PageDto(
    private val imageUrl: String? = null,
    private val translatedImageUrl: String? = null,
) {
    // The site shows the translated page, or the original when there is none
    val url get() = translatedImageUrl ?: imageUrl
}

@Serializable
class JsonLdDto(
    @SerialName("@graph") private val graph: List<JsonLdSeriesDto>,
) {
    val series get() = graph.first { it.type == "ComicSeries" }
}

@Serializable
class JsonLdSeriesDto(
    @SerialName("@type") val type: String,
    val name: String? = null,
    val alternateName: List<String> = emptyList(),
    val author: JsonLdPersonDto? = null,
    val image: String? = null,
    val creativeWorkStatus: String? = null,
)

@Serializable
class JsonLdPersonDto(
    val name: String,
)
