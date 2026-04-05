package eu.kanade.tachiyomi.extension.en.xomanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class IndexResponse(
    val slider: List<Manga>,
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
class ChapterResponse(
    @SerialName("chapters_list") val chaptersList: List<Chapters>,
)

@Serializable
class Chapters(
    private val chapter: Float,
    private val link: String,
    private val date: String,
) {
    fun toSChapter(baseUrl: String) = SChapter.create().apply {
        val urlLink = (baseUrl + link).toHttpUrl()
        val slug = urlLink.queryParameter("id")
        val chapterNum = urlLink.queryParameter("ch")
        val chapterStr = if (chapter % 1f == 0f) chapter.toInt().toString() else chapter.toString()
        url = "$slug#$chapterNum"
        name = "Chapter $chapterStr"
        date_upload = dateFormat.tryParse(date)
        chapter_number = chapter
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

@Serializable
class ImageResponse(
    val images: List<String>,
)
