package eu.kanade.tachiyomi.extension.tr.okutoon

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
class SeriesListResponse(
    val data: List<SeriesDto>,
    private val meta: ListMetaDto,
) {
    val hasNextPage get() = meta.page < meta.totalPages
}

@Serializable
class ListMetaDto(
    val page: Int,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class SeriesDto(
    private val slug: String,
    private val title: String,
    @SerialName("cover_url") private val coverUrl: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/$slug"
        title = this@SeriesDto.title
        thumbnail_url = coverUrl
    }
}

@Serializable
class SeriesResponse(val data: SeriesDetailsDto)

@Serializable
class SeriesDetailsDto(
    private val slug: String,
    private val title: String,
    @SerialName("cover_url") private val coverUrl: String? = null,
    @SerialName("alt_title") private val altTitle: String? = null,
    private val author: String? = null,
    private val type: String? = null,
    private val status: String? = null,
    @SerialName("description_html") private val descriptionHtml: String? = null,
    @SerialName("rating_avg") private val ratingAvg: Double? = null,
    @SerialName("rating_count") private val ratingCount: Int = 0,
    private val categories: List<CategoryDto> = emptyList(),
    @SerialName("format_tags") private val formatTags: List<FormatTagDto> = emptyList(),
    @SerialName("is_ai_translated") private val isAiTranslated: Boolean = false,
    @SerialName("source_credit") private val sourceCredit: SourceCreditDto? = null,
    private val chapters: List<ChapterDto>,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/$slug"
        title = this@SeriesDetailsDto.title
        thumbnail_url = coverUrl
        author = this@SeriesDetailsDto.author?.takeIf { it != "Bilinmiyor" }
        description = MarkdownDescription.Builder()
            .rating(ratingAvg.takeIf { ratingCount > 0 }, max = 5, votes = ratingCount)
            .info("Yapay zeka çevirisi".takeIf { isAiTranslated })
            .description(descriptionHtml?.let { Jsoup.parseBodyFragment(it, baseUrl).body() })
            .altTitles(altTitle?.split(" · ").orEmpty(), label = "Alternatif Adlar")
            .links(listOfNotNull(sourceCredit?.let { it.name to it.url }), label = "Kaynak")
            .build()
        genre = buildList {
            type?.let { add(it.replaceFirstChar(Char::uppercase)) }
            formatTags.mapTo(this) { it.label }
            categories.mapTo(this) { it.name }
        }.joinToString()
        status = when (this@SeriesDetailsDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    fun toSChapterList() = chapters.map { it.toSChapter(slug, isAiTranslated, sourceCredit?.name) }
}

@Serializable
class CategoriesResponse(val data: List<CategoryDto>)

@Serializable
class CategoryDto(
    val name: String,
    val slug: String,
)

@Serializable
class FormatTagDto(val label: String)

@Serializable
class SourceCreditDto(
    val name: String,
    val url: String? = null,
)

@Serializable
class ChapterDto(
    private val slug: String,
    private val title: String,
    @SerialName("sort_number") private val sortNumber: Float = -1f,
    @SerialName("created_at") private val createdAt: String? = null,
    @SerialName("is_ai_translated") private val isAiTranslated: Boolean = false,
) {
    fun toSChapter(seriesSlug: String, seriesIsAi: Boolean, group: String?) = SChapter.create().apply {
        url = "/$seriesSlug/$slug"
        name = if (seriesIsAi || isAiTranslated) "$title AI" else title
        chapter_number = sortNumber
        date_upload = DATE_FORMAT.tryParseDateTime(createdAt, TIME_ZONE)
        scanlator = group
    }
}

@Serializable
class ChapterResponse(val data: ChapterData)

@Serializable
class ChapterData(val pages: List<PageDto>)

@Serializable
class PageDto(
    @SerialName("image_url") val imageUrl: String,
)

private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
private val TIME_ZONE = ZoneId.of("Europe/Istanbul")
