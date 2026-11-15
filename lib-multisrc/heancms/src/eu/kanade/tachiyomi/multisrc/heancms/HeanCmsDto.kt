package eu.kanade.tachiyomi.multisrc.heancms

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.time.Instant

@Serializable
class HeanCmsTokenPayloadDto(
    val token: String? = null,
    private val expiresAt: String? = null,
) {
    fun isExpired(): Boolean {
        // Reduce one day to prevent timezone issues
        val expiredTime = expiresAt
            ?.let { Instant.parseOrNull(it)?.toEpochMilliseconds()?.minus(1000 * 60 * 60 * 24) }
            ?: 0L

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
    val title: String,
    private val tags: List<HeanCmsTagDto>? = emptyList(),
    @SerialName("alternative_names") private val alternativeNames: String? = null,
    private val rating: Float? = null,
    @SerialName("ratings_count") private val ratingsCount: Int? = null,
    @SerialName("release_schedule") private val releaseSchedule: Map<String, Boolean>? = null,
    @SerialName("release_year") private val releaseYear: String? = null,
    private val badge: String? = null,
) {

    // Badge text is free-form ("DOUJINSHI", "Doujinhsi", "DOUJIN")
    fun format(): String {
        val badge = badge?.trim()?.lowercase().orEmpty()
        return when {
            badge.startsWith("douj") -> "Doujinshi"
            badge == "manga" -> "Manga"
            else -> "Manhwa" // auto webtoon mode
        }
    }

    fun toSManga(
        cdnUrl: String,
        coverPath: String,
        mangaSubDirectory: String,
        format: String,
    ): SManga = SManga.create().apply {
        title = this@HeanCmsSeriesDto.title
        author = this@HeanCmsSeriesDto.author?.toCreditOrNull()
        artist = studio?.toCreditOrNull()
        description = buildDescription()
        genre = tags.orEmpty()
            .map(HeanCmsTagDto::name)
            .sorted()
            .plus(format)
            .joinToString()
        thumbnail_url = thumbnail.ifEmpty { null }
            ?.toAbsoluteThumbnailUrl(cdnUrl, coverPath)
        status = this@HeanCmsSeriesDto.status?.toStatus() ?: SManga.UNKNOWN
        url = "/$mangaSubDirectory/$slug#$id"
        memo = buildJsonObject { put(FORMAT_MEMO, format) }
    }

    private fun buildDescription(): String = buildString {
        val descriptionBody = description?.let(Jsoup::parseBodyFragment)
        descriptionBody?.select("p")
            ?.joinToString("\n\n") { it.text() }
            ?.ifEmpty { descriptionBody.text().replace("\n", "\n\n") }
            ?.takeUnless { it == "-" }
            ?.let(::append)

        val info = listOfNotNull(
            rating?.let { rating ->
                "Rating: %.2f".format(Locale.ROOT, rating) + (ratingsCount?.let { " ($it ratings)" } ?: "")
            },
            releaseSchedule
                ?.takeIf { status == "Ongoing" }
                ?.filterValues { it }
                ?.keys
                ?.mapNotNull(WEEKDAYS::get)
                ?.sortedBy(DayOfWeek::getValue)
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString(prefix = "Updates: ") { it.getDisplayName(TextStyle.FULL, Locale.ENGLISH) },
            releaseYear?.takeIf { it.isNotBlank() }?.let { "Released: $it" },
        )
        if (info.isNotEmpty()) {
            if (isNotEmpty()) append("\n\n")
            info.joinTo(this, "\n")
        }

        val altNames = alternativeNames?.split("|")
            ?.map(String::trim)
            ?.filter { it.isNotEmpty() && it != title }
        if (!altNames.isNullOrEmpty()) {
            if (isNotEmpty()) append("\n\n")
            append("Alternative names:\n")
            altNames.joinTo(this, "\n") { "- $it" }
        }
    }
}

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
    @SerialName("chapter_title") private val title: String? = null,
    @SerialName("chapter_slug") private val slug: String,
    @SerialName("created_at") private val createdAt: String? = null,
    val price: Int? = null,
) {
    fun toSChapter(
        seriesSlug: String,
        mangaSubDirectory: String,
    ): SChapter = SChapter.create().apply {
        name = this@HeanCmsChapterDto.name.trim()

        if (title != null) {
            name += " - ${title.trim()}"
        }

        if (price != 0) {
            name += " \uD83D\uDD12"
        }

        date_upload = createdAt?.let { Instant.parseOrNull(it)?.toEpochMilliseconds() } ?: 0L

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

const val FORMAT_MEMO = "format"

private val WEEKDAYS = DayOfWeek.entries.associateBy { it.name.take(3).lowercase() }

private fun String.toCreditOrNull(): String? = trim().takeUnless { it.isEmpty() || it == "-" }

private fun String.toAbsoluteThumbnailUrl(cdnUrl: String, coverPath: String): String = if (startsWith("https://") || startsWith("http://")) this else "$cdnUrl/$coverPath$this"

fun String.toStatus(): Int = when (this) {
    "Ongoing" -> SManga.ONGOING
    "Hiatus" -> SManga.ON_HIATUS
    "Dropped" -> SManga.CANCELLED
    "Completed", "Finished" -> SManga.COMPLETED
    else -> SManga.UNKNOWN
}
