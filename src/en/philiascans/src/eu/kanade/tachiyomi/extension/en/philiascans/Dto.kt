package eu.kanade.tachiyomi.extension.en.philiascans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class SeriesResponse(
    val items: List<Item>,
    private val page: Int,
    private val totalPages: Int,
) {
    fun hasNextPage() = page < totalPages
}

@Serializable
class Item(
    private val slug: String,
    private val title: String,
    private val coverImageUrl: String?,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = slug
        title = this@Item.title
        thumbnail_url = if (coverImageUrl?.startsWith("http") == true) coverImageUrl else "$baseUrl/$coverImageUrl"
    }
}

@Serializable
class DetailsResponse(
    private val title: String,
    private val alternativeTitles: List<String>?,
    private val synopsis: String?,
    private val coverImageUrl: String?,
    private val status: String?,
    private val genres: List<Info>?,
    private val authors: List<Info>?,
    private val artists: List<Info>?,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = this@DetailsResponse.title
        description = buildString {
            synopsis?.let { append(it) }
            alternativeTitles?.let {
                append("\n\nAlternative Titles:\n")
                append(alternativeTitles.joinToString("\n") { "- $it" })
            }
        }
        author = authors?.joinToString { it.name }
        artist = artists?.joinToString { it.name }
        genre = genres?.joinToString { it.name }
        status = when (this@DetailsResponse.status) {
            "ON_GOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = if (coverImageUrl?.startsWith("http") == true) coverImageUrl else "$baseUrl/$coverImageUrl"
    }
}

@Serializable
class Info(
    val name: String,
)

@Serializable
class ChapterResponse(
    val items: List<ChapterItem>,
)

@Serializable
class ChapterItem(
    private val number: String,
    private val title: String?,
    private val slug: String,
    private val publishedAt: String?,
    private val coinPrice: Int?,
    private val purchased: Boolean?,
) {
    val isLocked: Boolean
        get() = purchased == false && coinPrice != 0

    fun toSChapter(mangaSlug: String): SChapter = SChapter.create().apply {
        url = "$mangaSlug/$slug"
        val lock = if (isLocked) "🔒 " else ""
        name = lock + (title?.takeIf { it.isNotBlank() } ?: "Chapter $number")
        date_upload = dateFormat.tryParse(publishedAt)
        chapter_number = number.toFloat()
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Serializable
class TokenResponse(
    val token: String,
)

@Serializable
class ViewerResponse(
    val chapter: Chapter,
    val hasAccess: Boolean,
)

@Serializable
class Chapter(
    val id: Int,
    val scrambled: Boolean,
    val pages: List<Page>,
)

@Serializable
class Page(
    val position: Int,
    val url: String,
    val mime: String,
)

@Serializable
class PageKeys(
    val chapterKeyB64: String,
    val gridSize: Int,
)

@Serializable
class OpenResponse(
    val sessionId: String,
    val payloadA: String,
)

@Serializable
class DrmResponse(
    val payloadB: String,
)
