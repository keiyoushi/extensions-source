package eu.kanade.tachiyomi.extension.en.readcomicsonlinelol

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.time.Instant

private val releaseDateFormat = DateTimeFormatter.ISO_LOCAL_DATE

@Serializable
class CatalogueDto(val comics: List<ComicDto>)

@Serializable
class BrowseDto(
    val comics: List<ComicDto>,
    val hasMore: Boolean,
)

@Serializable
class ComicDto(
    private val slug: String,
    private val title: String,
    private val cover: String? = null,
    private val status: String? = null,
    private val views: Int = 0,
    private val lastUpdated: String? = null,
    private val genres: String? = null,
    private val publisher: String? = null,
    private val issueCount: Int = 0,
) {
    val url get() = "/comic/$slug"

    fun toSManga() = SManga.create().apply {
        url = this@ComicDto.url
        title = this@ComicDto.title
        thumbnail_url = cover
    }

    fun mangaStatus() = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // The site's search and catalogue filters, which it applies in the browser
    fun matches(query: String, publisherName: String?, statusValue: String?, genre: String?, weekStart: Long): Boolean {
        val statusMatches = when (statusValue) {
            null -> true
            "week" -> Instant.tryParse(lastUpdated) >= weekStart
            else -> status.orEmpty().contains(statusValue, ignoreCase = true)
        }
        return statusMatches &&
            title.searchable().contains(query.searchable()) &&
            (publisherName == null || publisher.orEmpty().contains(publisherName, ignoreCase = true)) &&
            (genre == null || genres.orEmpty().split(",").any { it.trim() == genre })
    }

    companion object {
        private val NOT_ALPHANUMERIC = Regex("[^a-z0-9]")

        private fun String.searchable() = lowercase().replace(NOT_ALPHANUMERIC, "")

        fun comparator(sort: String?): Comparator<ComicDto> = when (sort) {
            "updated" -> compareByDescending { Instant.tryParse(it.lastUpdated) }
            "az" -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            "issues" -> compareByDescending { it.issueCount }
            else -> compareByDescending { it.views }
        }
    }
}

@Serializable
class SeriesDto(
    private val name: String,
    private val image: String? = null,
    private val description: String? = null,
    @SerialName("author") private val writer: PersonDto? = null,
    private val illustrator: PersonDto? = null,
    private val publisher: PersonDto? = null,
    @SerialName("genre") private val genres: List<String> = emptyList(),
) {
    fun toSManga(mangaStatus: Int) = SManga.create().apply {
        title = name
        thumbnail_url = image
        author = writer?.name
        artist = illustrator?.name
        genre = genres.joinToString().ifEmpty { null }
        status = mangaStatus
        if (status == SManga.COMPLETED) update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        description = MarkdownDescription.Builder()
            .description(this@SeriesDto.description)
            .field("Publisher", publisher?.name)
            .build()
    }
}

@Serializable
class PersonDto(val name: String)

@Serializable
class IssuesDto(val issues: List<IssueDto>)

@Serializable
class IssueDto(
    private val title: String,
    private val slug: String,
    private val releaseDate: String? = null,
) {
    fun toSChapter(comicUrl: String) = SChapter.create().apply {
        url = "$comicUrl/$slug"
        name = title
        date_upload = releaseDateFormat.tryParseDate(releaseDate, ZoneOffset.UTC)
        slug.toFloatOrNull()?.let { chapter_number = it }
    }
}

@Serializable
class PagesDto(private val pages: List<PageDto>) {
    fun toPages() = pages.mapIndexed { i, page -> Page(i, imageUrl = page.url) }
}

@Serializable
class PageDto(val url: String)
