package eu.kanade.tachiyomi.extension.es.skymangas

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Locale
import kotlin.time.Instant

@Serializable
class ApiResponse<T>(val data: T)

@Serializable
class PagedResponse<T>(
    val data: List<T>,
    val meta: PageMeta,
)

@Serializable
class PageMeta(
    val page: Int,
    val totalPages: Int,
)

@Serializable
class MangaItem(
    private val id: String,
    private val title: String,
    private val slug: String,
    private val coverUrl: String?,
    private val statusId: Int? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaItem.title
        thumbnail_url = coverUrl?.let(::imageUrl)
        statusId?.let { status = parseStatus(it) }
        memo = buildJsonObject { put(MEMO_ID, id) }
    }
}

@Serializable
class MangaDetails(
    val id: String,
    private val title: String,
    private val slug: String,
    private val titleAlt: String?,
    private val titleOriginal: String?,
    private val synopsis: String?,
    private val coverUrl: String?,
    private val author: String?,
    private val artist: String?,
    private val statusId: Int,
    private val originationName: String?,
    private val demographicId: Int?,
    private val genres: List<Tag>,
    private val tags: List<Tag>,
    private val ratingAvg: Double,
    private val ratingCount: Int,
    private val yearRelease: Int?,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaDetails.title
        author = this@MangaDetails.author?.trim()?.ifEmpty { null }
        artist = this@MangaDetails.artist?.trim()?.ifEmpty { null }
        thumbnail_url = coverUrl?.let(::imageUrl)
        status = parseStatus(statusId)
        genre = (listOfNotNull(originationName, DEMOGRAPHICS[demographicId]) + genres.map { it.name } + tags.map { it.name })
            .distinct()
            .joinToString()
            .ifEmpty { null }
        description = buildString {
            val info = listOfNotNull(
                if (ratingCount > 0) "★ ${String.format(Locale.ROOT, "%.1f", ratingAvg)} ($ratingCount)" else null,
                yearRelease?.toString(),
            )
            if (info.isNotEmpty()) append(info.joinToString(" · "), "\n\n")
            synopsis?.trim()?.takeIf { it.isNotEmpty() }?.let { append(it, "\n\n") }
            val altTitles = (listOfNotNull(titleOriginal) + titleAlt?.split('\n').orEmpty())
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != title }
                .distinct()
            if (altTitles.isNotEmpty()) {
                append("**Títulos alternativos:**\n")
                altTitles.forEach { append("- ", it, "\n") }
            }
        }.trim().ifEmpty { null }
        memo = buildJsonObject { put(MEMO_ID, id) }
    }
}

@Serializable
class Tag(
    val id: Int,
    val name: String,
)

@Serializable
class ChapterItem(
    private val chapterNumber: Double,
    private val title: String?,
    private val publishedAt: String?,
    private val groupNames: String?,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        val number = chapterNumber.toString().removeSuffix(".0")
        url = "$mangaSlug/$number"
        name = buildString {
            append("Capítulo ", number)
            title?.trim()?.takeIf { it.isNotEmpty() }?.let { append(": ", it) }
        }
        chapter_number = chapterNumber.toFloat()
        scanlator = groupNames?.trim()?.ifEmpty { null }
        date_upload = Instant.tryParse(publishedAt)
    }
}

@Serializable
class ChapterPages(val pages: List<PageImage>)

@Serializable
class PageImage(
    val pageNumber: Int,
    val imageUrl: String,
)

@Serializable
class FilterData(
    val genres: List<Tag>,
    val tags: List<Tag>,
    val statuses: List<Tag>,
    val originations: List<Tag>,
    val demographics: List<Tag>,
    val translationStatuses: List<Tag>,
)

// Covers and page images are paths on the API host
fun imageUrl(path: String): String = API_ROOT.resolve(path)!!.toString()

private fun parseStatus(statusId: Int) = when (statusId) {
    1 -> SManga.ONGOING
    2 -> SManga.COMPLETED
    3 -> SManga.ON_HIATUS
    4 -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}

private val DEMOGRAPHICS = mapOf(
    1 to "Shounen",
    2 to "Shoujo",
    3 to "Seinen",
    4 to "Josei",
    5 to "Kodomo",
)

val API_ROOT = "https://api.skymangas.com".toHttpUrl()
const val MEMO_ID = "id"
