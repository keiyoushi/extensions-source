package eu.kanade.tachiyomi.extension.en.xomanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.format.DateTimeFormatter
import kotlin.time.Instant

@Serializable
class IndexResponse(
    val latest: List<Manga>,
)

@Serializable
class Manga(
    private val title: String,
    private val image: String?,
    private val link: String,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = (baseUrl + link).toHttpUrl().queryParameter("id").toString()
        title = this@Manga.title
        thumbnail_url = image
    }

    fun matchesQuery(query: String): Boolean = query.isEmpty() || title.lowercase().contains(query)

    fun isExclusive(exclusiveTitles: Set<String>): Boolean {
        val normalised = title.lowercase().trim().replace(Regex("\\s+"), " ")
        return exclusiveTitles.any { normalised.contains(it) }
    }
}

@Serializable
class DetailsResponse(
    private val title: String,
    private val description: String?,
    private val cover: String?,
    private val tags: List<String>?,
    private val status: String?,
    @SerialName("chapters_list") val chaptersList: List<Chapters>,
) {
    fun toSManga() = SManga.create().apply {
        title = this@DetailsResponse.title
        description = this@DetailsResponse.description
        genre = tags?.joinToString()
        status = when (this@DetailsResponse.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = cover
    }
}

@Serializable
class Chapters(
    private val chapter: Float,
    private val link: String,
    private val date: String,
    @SerialName("is_vip") private val vip: Boolean? = false,
) {
    fun toSChapter(baseUrl: String, hideVip: Boolean): SChapter? {
        if (hideVip && vip == true) return null
        val prefix = if (vip == true) "\uD83D\uDD12 " else ""
        return SChapter.create().apply {
            val urlLink = (baseUrl + link).toHttpUrl()
            val slug = urlLink.queryParameter("id")
            val chapterNum = urlLink.queryParameter("ch")
            val chapterStr = if (chapter % 1f == 0f) chapter.toInt().toString() else chapter.toString()
            url = "$slug#$chapterNum"
            name = "${prefix}Chapter $chapterStr"
            date_upload = if (date.endsWith("Z")) {
                Instant.tryParse(date)
            } else {
                dateFormat.tryParseDate(date)
            }
            chapter_number = chapter
        }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

@Serializable
class ImageResponse(
    val images: List<String>,
)
