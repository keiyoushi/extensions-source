package eu.kanade.tachiyomi.extension.en.desirescans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Serializable
class SeriesListDto(
    val data: List<SeriesDto>,
    val meta: MetaDto,
)

@Serializable
class MetaDto(
    val hasMore: Boolean,
)

@Serializable
class SeriesDto(
    private val urlSlug: String,
    private val title: String,
    private val coverImage: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = urlSlug
        title = this@SeriesDto.title
        thumbnail_url = coverImage?.let { baseUrl.toHttpUrl().resolve(it)?.toString() }
    }
}

@Serializable
class SeriesPageDto(
    val series: SeriesDetailsDto,
    val chapters: List<ChapterDto>,
    val totalPages: Int,
)

@Serializable
class SeriesDetailsDto(
    private val title: String,
    private val altTitle: String? = null,
    private val originalTitle: String? = null,
    private val aliases: List<String> = emptyList(),
    private val description: String? = null,
    private val coverImage: String? = null,
    private val status: String? = null,
    private val type: String? = null,
    private val origin: String? = null,
    private val genres: List<NameDto> = emptyList(),
    private val tags: List<NameDto> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = this@SeriesDetailsDto.title
        thumbnail_url = coverImage?.let { baseUrl.toHttpUrl().resolve(it)?.toString() }
        genre = genres.joinToString { it.name }.ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(this@SeriesDetailsDto.description)
            .field("Type", type?.lowercase()?.replaceFirstChar(Char::uppercase))
            .field("Origin", origin?.lowercase()?.replaceFirstChar(Char::uppercase))
            .field("Tags", tags.map { it.name })
            .altTitles(listOfNotNull(altTitle, originalTitle) + aliases)
            .build()
        status = when (this@SeriesDetailsDto.status) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            "DROPPED" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class NameDto(
    val name: String,
)

@Serializable
class ChapterDto(
    private val number: Float,
    private val title: String? = null,
    private val hasAccess: Boolean = true,
    private val publishedAt: String? = null,
) {
    fun toSChapter(seriesSlug: String) = SChapter.create().apply {
        val chapterNumber = number.toString().removeSuffix(".0")
        url = "/series/comic/$seriesSlug/chapter/$chapterNumber"
        val chapterName = title?.replace(SPACES, " ")?.takeIf { it.isNotBlank() } ?: "Chapter $chapterNumber"
        // Locked chapters are sold for coins on the site, and some are freed later
        name = if (hasAccess) chapterName else "🔒 $chapterName"
        chapter_number = number
        date_upload = Instant.tryParse(publishedAt)
    }
}

@Serializable
class ChapterPagesDto(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    val pageNumber: Int,
    val imageUrl: String,
    val isRedacted: Boolean = false,
)

private val SPACES = Regex("""\s+""")
