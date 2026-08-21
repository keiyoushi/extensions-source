package eu.kanade.tachiyomi.extension.ru.remanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
class CatalogDto(
    val content: List<CatalogItemDto> = emptyList(),
    val props: CatalogPropsDto = CatalogPropsDto(),
)

@Serializable
class CatalogPropsDto(
    @SerialName("total_pages") val totalPages: Int = 0,
    val page: Int = 0,
)

@Serializable
class CatalogItemDto(
    private val dir: String,
    @SerialName("rus_name") private val rusName: String? = null,
    @SerialName("en_name") private val enName: String? = null,
    @SerialName("main_name") private val mainName: String? = null,
    private val img: CoverDto? = null,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        url = dir
        title = rusName ?: enName ?: mainName ?: dir
        thumbnail_url = img?.high?.takeIf { it.isNotEmpty() }?.let { "$baseUrl$it" }
    }
}

@Serializable
class CoverDto(
    val high: String? = null,
)

@Serializable
class TitleDetailDto(
    val content: TitleContentDto,
)

@Serializable
class TitleContentDto(
    @SerialName("rus_name") private val rusName: String? = null,
    @SerialName("en_name") private val enName: String? = null,
    @SerialName("main_name") private val mainName: String? = null,
    private val description: String? = null,
    private val status: StatusDto? = null,
    private val img: CoverDto? = null,
    private val genres: List<NameDto> = emptyList(),
    private val categories: List<NameDto> = emptyList(),
    private val creators: Map<String, List<CreatorDto>> = emptyMap(),
    val branches: List<BranchDto> = emptyList(),
) {
    fun toSManga(manga: SManga, baseUrl: String): SManga = manga.apply {
        val statusId = this@TitleContentDto.status?.id
        title = rusName ?: enName ?: mainName ?: this.title
        thumbnail_url = img?.high?.takeIf { it.isNotEmpty() }?.let { "$baseUrl$it" } ?: thumbnail_url
        description = description?.let { Jsoup.parseBodyFragment(it, baseUrl).text() }
        genre = (genres.mapNotNull { it.name } + categories.mapNotNull { it.name })
            .distinct()
            .joinToString()
        author = creators.values.flatten().mapNotNull { it.name }.distinct().joinToString()
        artist = author
        status = parseStatus(statusId)
    }
}

@Serializable
class StatusDto(
    val id: Int,
)

@Serializable
class NameDto(
    val name: String? = null,
)

@Serializable
class CreatorDto(
    val name: String? = null,
)

@Serializable
class BranchDto(
    val id: Int,
)

@Serializable
class ChaptersDto(
    val content: List<ChapterItemDto> = emptyList(),
)

@Serializable
class ChapterItemDto(
    private val id: Int,
    private val tome: Int? = null,
    private val chapter: String? = null,
    private val name: String? = null,
    @SerialName("is_paid") private val isPaid: Boolean = false,
    @SerialName("is_bought") private val isBought: Boolean = false,
    @SerialName("upload_date") private val uploadDate: String? = null,
    @SerialName("pub_date") private val pubDate: String? = null,
) {
    val isLocked: Boolean
        get() = isPaid && !isBought

    fun toSChapter(dir: String, baseUrl: String, showFreeDate: Boolean): SChapter = SChapter.create().apply {
        val title = buildString {
            if (tome != null) {
                append("Том ").append(tome)
                append(". ")
            }
            append("Глава ").append(chapter)
            this@ChapterItemDto.name?.takeIf { it.isNotEmpty() }?.let { append(" ").append(it) }
            if (showFreeDate && isLocked) {
                formatFreeDate(pubDate)?.let { append(" (free ").append(it).append(")") }
            }
        }
        this.name = if (isLocked) "$LOCK_ICON$title" else title
        url = "$baseUrl/titles/".toHttpUrl()
            .newBuilder()
            .addPathSegment(dir)
            .addQueryParameter("chapter", id.toString())
            .build()
            .let { it.encodedPath + it.encodedQuery?.let { q -> "?$q" }.orEmpty() }
        date_upload = parseUploadDate(uploadDate)
    }
}

@Serializable
class ChapterDetailDto(
    val content: ChapterDetailContentDto,
)

@Serializable
class ChapterDetailContentDto(
    val pages: List<List<PageDto>> = emptyList(),
)

@Serializable
class PageDto(
    val id: Int,
    val link: String,
)

private fun parseUploadDate(raw: String?): Long = raw?.let {
    runCatching {
        LocalDateTime.parse(it, uploadDateFormat).toInstant(ZoneOffset.UTC).toEpochMilli()
    }.getOrDefault(0L)
} ?: 0L

private fun parseStatus(id: Int?): Int = when (id) {
    1 -> SManga.COMPLETED
    2 -> SManga.ONGOING
    3, 4, 5 -> SManga.ON_HIATUS
    6 -> SManga.LICENSED
    else -> SManga.UNKNOWN
}

private fun formatFreeDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val date = runCatching {
        LocalDateTime.parse(raw, uploadDateFormat).toLocalDate()
    }.getOrNull() ?: return null
    return date.format(displayDateFormat)
}

private val uploadDateFormat = DateTimeFormatter.ISO_LOCAL_DATE_TIME

private val displayDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private const val LOCK_ICON = "\uD83D\uDD12 "
