package eu.kanade.tachiyomi.extension.en.renascans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class MangaDto(
    private val title: String,
    private val slug: String,
    private val cover: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = slug
        title = this@MangaDto.title
        thumbnail_url = cover?.toCoverUrl(baseUrl)
    }
}

@Serializable
class MangaDetailsDto(
    private val title: String,
    private val slug: String,
    private val cover: String? = null,
    private val description: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val status: String? = null,
    private val type: String? = null,
    private val genres: List<String> = emptyList(),
    @SerialName("alt_titles") private val altTitles: String? = null,
    @SerialName("release_year") private val releaseYear: Int? = null,
    val chapters: List<ChapterDto>,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = slug
        title = this@MangaDetailsDto.title
        thumbnail_url = cover?.toCoverUrl(baseUrl)
        author = this@MangaDetailsDto.author?.takeIf { it.isNotBlank() }
        artist = this@MangaDetailsDto.artist?.takeIf { it.isNotBlank() }
        genre = (listOfNotNull(type?.replaceFirstChar(Char::titlecase)) + genres).takeIf { it.isNotEmpty() }?.joinToString()
        status = when (this@MangaDetailsDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled", "dropped" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .field("Released", releaseYear?.toString())
            .description(this@MangaDetailsDto.description?.let { Jsoup.parseBodyFragment(it).body() })
            .altTitles(listOfNotNull(altTitles))
            .build()
    }
}

@Serializable
class ChapterDto(
    private val id: Int,
    private val title: String? = null,
    @SerialName("chapter_number") val chapterNumber: Float,
    @SerialName("is_locked") val isLocked: Boolean,
    @SerialName("created_at") private val createdAt: String? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        val number = chapterNumber.toString().removeSuffix(".0")
        url = "/manga/$mangaSlug/chapter-$number"
        name = buildString {
            if (isLocked) append("🔒 ")
            append("Chapter $number")
            title?.takeIf { it.isNotBlank() && it != "Chapter $number" }?.let { append(": $it") }
        }
        chapter_number = chapterNumber
        date_upload = Instant.tryParse(createdAt)
        memo = buildJsonObject { put(MEMO_CHAPTER_ID, id) }
    }
}

@Serializable
class PagesDto(
    val content: List<String> = emptyList(),
)

@Serializable
class GenreDto(
    val title: String,
)

// Site paths are used as they are when absolute, otherwise under /uploads/
fun String.toUploadPath() = if (startsWith("http") || startsWith("/")) this else "/uploads/$this"

// Covers come as an images.weserv.nl proxy url around the site's storage host
private fun String.toCoverUrl(baseUrl: String): String? {
    val url = baseUrl.toHttpUrl().resolve(toUploadPath()) ?: return null
    if (url.host != "images.weserv.nl") return url.toString()
    val origin = url.queryParameter("url") ?: return url.toString()
    return if (origin.startsWith("http")) origin else "https://$origin"
}

const val MEMO_CHAPTER_ID = "chapterId"
