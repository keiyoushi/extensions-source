package eu.kanade.tachiyomi.extension.th.readrealm

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.time.ZonedDateTime
import kotlin.time.Instant

@Serializable
class Book(
    @SerialName("book_ID") val id: String,
    @SerialName("book_subject") private val subject: String,
    @SerialName("book_cover") private val cover: StoredFile? = null,
    @SerialName("book_status") private val ended: Boolean,
    @SerialName("book_category") private val category: Category? = null,
    @SerialName("book_tag") private val tags: List<String> = emptyList(),
    @SerialName("book_tags") private val listTags: List<String> = emptyList(),
    @SerialName("book_writer") private val writer: Writer? = null,
    @SerialName("book_details") private val details: String? = null,
    @SerialName("book_synopsis") private val synopsis: String? = null,
    @SerialName("book_rating") private val rating: String? = null,
    @SerialName("book_chapter_count") val chapterCount: Int,
    @SerialName("book_chapter_last_update") val lastUpdate: String? = null,
) {
    val mainCategory get() = category?.main.orEmpty()

    fun toSManga() = SManga.create().apply {
        url = id
        title = subject
        thumbnail_url = cover?.let { "${ReadRealm.IMAGE_URL}/${it.fileName}" }
        author = writer?.profileName
        status = if (ended) SManga.COMPLETED else SManga.ONGOING
        genre = buildList {
            listOfNotNull(category?.main, category?.secondary)
                .mapNotNullTo(this) { it.toIntOrNull()?.let(CATEGORIES::getOrNull)?.first }
            (tags + listTags).mapTo(this) { it.removePrefix("#") }
        }.distinct().joinToString().ifEmpty { null }
        val detailsHtml = details?.let { Jsoup.parseBodyFragment(it).body() }
        description = MarkdownDescription.Builder()
            .info(RATINGS[rating])
            .description(synopsis?.takeIf { detailsHtml?.text()?.contains(it.trim()) != true })
            .description(detailsHtml)
            .build()
    }
}

@Serializable
class Profile(
    @SerialName("book_data") val bookData: ProfileBooks,
)

@Serializable
class ProfileBooks(
    @SerialName("comic_arr") val comics: List<Book>,
)

@Serializable
class StoredFile(
    val fileName: String,
)

@Serializable
class Category(
    @SerialName("category_main") val main: String,
    @SerialName("category_secondary") val secondary: String,
)

@Serializable
class Writer(
    @SerialName("profile_name") val profileName: String,
)

@Serializable
class ChapterSection(
    val data: List<ChapterRow>,
)

@Serializable
class ChapterRow(
    @SerialName("book_chapter_ID") private val id: String,
    @SerialName("book_chapter_title") private val title: String,
    @SerialName("book_chapter_price") private val price: String,
    @SerialName("book_chapter_publish") private val publish: String? = null,
) {
    val isLocked get() = price.toDouble() > 0

    fun toSChapter(lastUpdate: String?) = SChapter.create().apply {
        url = id
        name = if (isLocked) "🔒 $title" else title
        date_upload = if (lastUpdate != null) Instant.tryParse(lastUpdate) else parseRelativeDate(publish)
    }
}

@Serializable
class ChapterData(
    @SerialName("content_details") val contentDetails: ContentDetails,
    @SerialName("chapter_content") val chapterContent: List<StoredFile>? = null,
)

@Serializable
class ContentDetails(
    @SerialName("book_ID") val bookId: String,
)

private val RATINGS = mapOf("PG" to "ทั่วไป (PG)", "NC" to "18+ ขึ้นไป (NC)")

private val RELATIVE_DATE_REGEX = Regex("""(\d+)\s*(วินาที|นาที|ชั่วโมง|วัน|สัปดาห์|เดือน|ปี)ที่แล้ว""")

private fun parseRelativeDate(text: String?): Long {
    val match = text?.let(RELATIVE_DATE_REGEX::find) ?: return 0L
    val amount = match.groupValues[1].toLong()
    val now = ZonedDateTime.now()
    val date = when (match.groupValues[2]) {
        "วินาที" -> now.minusSeconds(amount)
        "นาที" -> now.minusMinutes(amount)
        "ชั่วโมง" -> now.minusHours(amount)
        "วัน" -> now.minusDays(amount)
        "สัปดาห์" -> now.minusWeeks(amount)
        "เดือน" -> now.minusMonths(amount)
        else -> now.minusYears(amount)
    }
    return date.toInstant().toEpochMilli()
}
