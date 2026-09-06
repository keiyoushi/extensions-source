package eu.kanade.tachiyomi.extension.en.silentquill

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.ZonedDateTime

@Serializable
class ChapterResponse(
    @SerialName("chapter_no") val chapterNo: String,
    val id: Int,
    val slug: String,
    @SerialName("time_ago") val timeAgo: String,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        url = id.toString()
        name = "Chapter $chapterNo"
        chapter_number = chapterNo.toFloatOrNull() ?: -1f
        date_upload = timeAgo.toRelativeDate()
        memo = buildJsonObject {
            put("slug", slug)
        }
    }
}

private val RELATIVE_DATE_REGEX = Regex("""(\d+)\s+(minute|hour|day|week|month|year)s?""")

private fun String.toRelativeDate(): Long {
    val (amount, unit) = RELATIVE_DATE_REGEX.find(this)?.destructured ?: return 0L
    val now = ZonedDateTime.now()

    return when (unit) {
        "minute" -> now.minusMinutes(amount.toLong())
        "hour" -> now.minusHours(amount.toLong())
        "day" -> now.minusDays(amount.toLong())
        "week" -> now.minusWeeks(amount.toLong())
        "month" -> now.minusMonths(amount.toLong())
        else -> now.minusYears(amount.toLong())
    }.toInstant().toEpochMilli()
}

@Suppress("unused")
@Serializable
class ViewerResponseBody(
    @SerialName("chapter_id") val chapterId: Int,
)

@Serializable
class ViewerResponse(
    val paginas: List<Pagina>,
)

@Serializable
class Pagina(
    @SerialName("t") val pages: String,
)
