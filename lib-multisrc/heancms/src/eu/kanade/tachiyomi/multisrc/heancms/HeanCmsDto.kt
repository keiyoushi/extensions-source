package eu.kanade.tachiyomi.multisrc.heancms

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.text.SimpleDateFormat

@Serializable
class HeanCmsTokenPayloadDto(
    val token: String? = null,
    private val expiresAt: String? = null,
) {
    fun isExpired(dateFormat: SimpleDateFormat): Boolean {
        val expiredTime = try {
            // Reduce one day to prevent timezone issues
            expiresAt?.let { dateFormat.parse(it)?.time?.minus(1000 * 60 * 60 * 24) } ?: 0L
        } catch (_: Exception) {
            0L
        }

        return System.currentTimeMillis() > expiredTime
    }
}

@Serializable
class HeanCmsErrorsDto(
    val errors: List<HeanCmsErrorMessageDto>? = emptyList(),
)

@Serializable
class HeanCmsErrorMessageDto(
    val message: String,
)

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
        cdnUrl: String,
        coverPath: String,
        mangaSubDirectory: String,
    ): SManga = SManga.create().apply {
        val descriptionBody = this@HeanCmsSeriesDto.description?.let(Jsoup::parseBodyFragment)

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
            ?.toAbsoluteThumbnailUrl(cdnUrl, coverPath)
        status = this@HeanCmsSeriesDto.status?.toStatus() ?: SManga.UNKNOWN
        url = "/$mangaSubDirectory/$slug#$id"
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
    ): SChapter = SChapter.create().apply {
        name = this@HeanCmsChapterDto.name.trim()

        if (price != 0) {
            name += " \uD83D\uDD12"
        }

        date_upload = try {
            dateFormat.parse(createdAt)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }

        url = "/$mangaSubDirectory/$seriesSlug/$slug#$id"
    }
}

@Serializable
class HeanCmsChapterMetaDto(
    @SerialName("current_page") private val currentPage: Int,
    @SerialName("last_page") private val lastPage: Int,
) {
    fun hasNextPage() = currentPage < lastPage
}

@Serializable
class HeanCmsPagePayloadDto(
    val chapter: HeanCmsPageDto,
    private val paywall: Boolean = false,
    val data: List<String>? = emptyList(),
) {
    fun isPaywalled() = paywall
}

@Serializable
class HeanCmsPageDto(
    @SerialName("chapter_data") val chapterData: HeanCmsPageDataDto?,
)

@Serializable
class HeanCmsPageDataDto(
    val images: List<String>? = emptyList(),
)

@Serializable
class HeanCmsGenreDto(
    val id: Int,
    val name: String,
)

private fun String.toAbsoluteThumbnailUrl(cdnUrl: String, coverPath: String): String {
    return if (startsWith("https://") || startsWith("http://")) this else "$cdnUrl/$coverPath$this"
}

fun String.toStatus(): Int = when (this) {
    "Ongoing" -> SManga.ONGOING
    "Hiatus" -> SManga.ON_HIATUS
    "Dropped" -> SManga.CANCELLED
    "Completed", "Finished" -> SManga.COMPLETED
    else -> SManga.UNKNOWN
}
