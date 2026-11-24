package eu.kanade.tachiyomi.extension.vi.nettruyenzz

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@Serializable
class ChapterListDto(
    val chapters: List<ChapterDto>,
    @SerialName("has_more") val hasMore: Boolean,
)

@Serializable
class ChapterDto(
    private val name: String?,
    private val slug: String,
    @SerialName("chapter_index") private val chapterIndex: String,
    @SerialName("updated_at_human") private val updatedAtHuman: String?,
) {
    fun toSChapter(seriesSlug: String) = SChapter.create().apply {
        url = "/$seriesSlug/$slug"
        name = this@ChapterDto.name?.ifEmpty { null } ?: "Chapter ${chapterIndex.removeSuffix(".00")}"
        date_upload = parseRelativeDate(updatedAtHuman)
    }
}

// The site only gives relative dates, such as "13 giờ trước"
internal fun parseRelativeDate(text: String?): Long {
    val (amount, unit) = text?.let { RELATIVE_DATE_REGEX.find(it) }?.destructured ?: return 0L
    return ZonedDateTime.now().minus(amount.toLong(), DATE_UNITS.getValue(unit)).toInstant().toEpochMilli()
}

private val RELATIVE_DATE_REGEX = Regex("""(\d+)\s*(giây|phút|giờ|ngày|tuần|tháng|năm)""")

private val DATE_UNITS = mapOf(
    "giây" to ChronoUnit.SECONDS,
    "phút" to ChronoUnit.MINUTES,
    "giờ" to ChronoUnit.HOURS,
    "ngày" to ChronoUnit.DAYS,
    "tuần" to ChronoUnit.WEEKS,
    "tháng" to ChronoUnit.MONTHS,
    "năm" to ChronoUnit.YEARS,
)
