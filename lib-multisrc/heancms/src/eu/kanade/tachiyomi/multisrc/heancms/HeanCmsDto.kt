package eu.kanade.tachiyomi.multisrc.heancms

import eu.kanade.tachiyomi.multisrc.heancms.HeanCms.SlugStrategy
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.text.SimpleDateFormat

@Serializable
class HeanCmsQuerySearchDto(
    val data: List<HeanCmsSeriesDto> = emptyList(),
    val meta: HeanCmsQuerySearchMetaDto? = null,
)

@Serializable
class HeanCmsQuerySearchMetaDto(
    @SerialName("current_page") private val currentPage: Int,
    @SerialName("last_page") private val lastPage: Int,
) {
    fun hasNextPage() = currentPage < lastPage
}

@Serializable
class HeanCmsSearchDto(
    @SerialName("series_slug") var slug: String,
    @SerialName("series_type") val type: String,
    private val title: String,
    private val thumbnail: String? = null,
) {

    fun toSManga(
        apiUrl: String,
        coverPath: String,
        mangaSubDirectory: String,
        slugMap: Map<String, HeanCms.HeanCmsTitle>,
        slugStrategy: SlugStrategy,
    ): SManga = SManga.create().apply {
        val slugOnly = slug.toPermSlugIfNeeded(slugStrategy)
        val thumbnailFileName = slugMap[slugOnly]?.thumbnailFileName
        title = this@HeanCmsSearchDto.title
        thumbnail_url = thumbnail?.toAbsoluteThumbnailUrl(apiUrl, coverPath)
            ?: thumbnailFileName?.toAbsoluteThumbnailUrl(apiUrl, coverPath)
        url = "/$mangaSubDirectory/$slugOnly"
    }
}

@Serializable
class HeanCmsSeriesDto(
    val id: Int,
    @SerialName("series_slug") val slug: String,
    private val author: String? = null,
    private val description: String? = null,
    private val studio: String? = null,
    private val status: String? = null,
    private val thumbnail: String,
    private val title: String,
    private val tags: List<HeanCmsTagDto>? = emptyList(),
    val seasons: List<HeanCmsSeasonsDto>? = emptyList(),
) {

    fun toSManga(
        apiUrl: String,
        coverPath: String,
        mangaSubDirectory: String,
        slugStrategy: SlugStrategy,
    ): SManga = SManga.create().apply {
        val descriptionBody = this@HeanCmsSeriesDto.description?.let(Jsoup::parseBodyFragment)
        val slugOnly = slug.toPermSlugIfNeeded(slugStrategy)

        title = this@HeanCmsSeriesDto.title
        author = this@HeanCmsSeriesDto.author?.trim()
        artist = this@HeanCmsSeriesDto.studio?.trim()
        description = descriptionBody?.select("p")
            ?.joinToString("\n\n") { it.text() }
            ?.ifEmpty { descriptionBody.text().replace("\n", "\n\n") }
        genre = tags.orEmpty()
            .sortedBy(HeanCmsTagDto::name)
            .joinToString { it.name }
        thumbnail_url = thumbnail.ifEmpty { null }
            ?.toAbsoluteThumbnailUrl(apiUrl, coverPath)
        status = this@HeanCmsSeriesDto.status?.toStatus() ?: SManga.UNKNOWN
        url = if (slugStrategy != SlugStrategy.NONE) {
            "/$mangaSubDirectory/$slugOnly#$id"
        } else {
            "/$mangaSubDirectory/$slug"
        }
    }
}

@Serializable
class HeanCmsSeasonsDto(
    val chapters: List<HeanCmsChapterDto>? = emptyList(),
)

@Serializable
class HeanCmsTagDto(val name: String)

@Serializable
class HeanCmsChapterPayloadDto(
    val data: List<HeanCmsChapterDto>,
    val meta: HeanCmsChapterMetaDto,
)

@Serializable
class HeanCmsChapterDto(
    private val id: Int,
    @SerialName("chapter_name") private val name: String,
    @SerialName("chapter_slug") private val slug: String,
    @SerialName("created_at") private val createdAt: String,
    val price: Int? = null,
) {
    fun toSChapter(
        seriesSlug: String,
        mangaSubDirectory: String,
        dateFormat: SimpleDateFormat,
        slugStrategy: SlugStrategy,
    ): SChapter = SChapter.create().apply {
        val seriesSlugOnly = seriesSlug.toPermSlugIfNeeded(slugStrategy)
        name = this@HeanCmsChapterDto.name.trim()

        if (price != 0) {
            name += " \uD83D\uDD12"
        }

        date_upload = try {
            dateFormat.parse(createdAt)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }

        val paidStatus = if (price != 0 && price != null) "-paid" else ""

        url = "/$mangaSubDirectory/$seriesSlugOnly/$slug#$id$paidStatus"
    }
}

@Serializable
class HeanCmsChapterMetaDto(
    @SerialName("current_page") private val currentPage: Int,
    @SerialName("last_page") private val lastPage: Int,
) {
    fun hasNextPage() = currentPage < lastPage
}

private fun String.toAbsoluteThumbnailUrl(apiUrl: String, coverPath: String): String {
    return if (startsWith("https://")) this else "$apiUrl/$coverPath$this"
}

private fun String.toPermSlugIfNeeded(slugStrategy: SlugStrategy): String {
    return if (slugStrategy != SlugStrategy.NONE) {
        this.replace(HeanCms.TIMESTAMP_REGEX, "")
    } else {
        this
    }
}

fun String.toStatus(): Int = when (this) {
    "Ongoing" -> SManga.ONGOING
    "Hiatus" -> SManga.ON_HIATUS
    "Dropped" -> SManga.CANCELLED
    "Completed", "Finished" -> SManga.COMPLETED
    else -> SManga.UNKNOWN
}
