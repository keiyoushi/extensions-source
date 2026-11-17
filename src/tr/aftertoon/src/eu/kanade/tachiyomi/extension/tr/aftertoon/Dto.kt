package eu.kanade.tachiyomi.extension.tr.aftertoon

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
class CatalogDto(
    val allSeries: List<CatalogSeriesDto>,
    val genres: List<GenreDto>,
)

@Serializable
class CatalogSeriesDto(
    val slug: String,
    val title: String,
    private val coverUrl: String? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    val rating: Double? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/manga/$slug"
        title = this@CatalogSeriesDto.title
        thumbnail_url = coverUrl
    }
}

@Serializable
class PopularSeriesDto(
    private val slug: String,
    private val title: String,
    private val coverUrl: String? = null,
    val viewCount: Long,
) {
    fun toSManga() = SManga.create().apply {
        url = "/manga/$slug"
        title = this@PopularSeriesDto.title
        thumbnail_url = coverUrl
    }
}

@Serializable
class GenreDto(
    val name: String,
    val slug: String,
)

@Serializable
class SeriesDto(
    val slug: String,
    private val title: String,
    private val coverUrl: String? = null,
    private val rating: Double? = null,
    val chapters: List<ChapterDto>,
    private val altTitle: String? = null,
    private val description: String? = null,
    private val genres: List<GenreDto>,
) {
    fun toSManga(author: String?, status: Int) = SManga.create().apply {
        url = "/manga/$slug"
        title = this@SeriesDto.title
        thumbnail_url = coverUrl
        this.author = author
        this.status = status
        genre = genres.joinToString { it.name }.ifEmpty { null }
        description = MarkdownDescription.Builder()
            .rating(rating)
            .description(this@SeriesDto.description)
            .altTitles(listOfNotNull(altTitle))
            .build()
    }
}

@Serializable
class ChapterDto(
    private val slug: String,
    private val title: String,
    private val timeAgo: String? = null,
) {
    fun toSChapter(seriesSlug: String) = SChapter.create().apply {
        url = "/manga/$seriesSlug/$slug"
        name = title
        date_upload = DATE_FORMAT.tryParseDate(timeAgo, ZONE)
    }

    companion object {
        // Despite the name, it is the release date
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        private val ZONE = ZoneId.of("Europe/Istanbul")
    }
}

@Serializable
class ChapterPagesDto(
    private val pages: List<PageDto>,
) {
    fun imageUrls() = pages.sortedBy { it.pageNumber }.map { it.imageUrl }
}

@Serializable
class PageDto(
    val pageNumber: Int,
    val imageUrl: String,
)

// The series page only names the author in its JSON-LD
@Serializable
class JsonLdScriptDto(
    @SerialName("__html") private val html: String,
) {
    fun author() = html.parseAs<JsonLdDto>().graph.firstNotNullOfOrNull { it.author?.name }
}

@Serializable
class JsonLdDto(
    @SerialName("@graph") val graph: List<JsonLdNodeDto>,
)

@Serializable
class JsonLdNodeDto(
    val author: JsonLdAuthorDto? = null,
)

@Serializable
class JsonLdAuthorDto(
    val name: String,
)
