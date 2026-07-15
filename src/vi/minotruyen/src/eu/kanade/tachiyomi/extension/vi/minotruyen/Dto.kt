package eu.kanade.tachiyomi.extension.vi.minotruyen

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private const val IBYTE_AD_MARKER = "-ad-"
private const val IBYTE_LP_MARKER = "-lp-"
private const val IBYTE_THUMBNAIL_SUFFIX = "~tplv-375lmtcpo0-resize:200:200.webp"

internal fun resolveThumbnailUrl(url: String?, baseUrl: String): String? {
    val normalized = url
        ?.takeIf { it.isNotBlank() }
        ?.let {
            if (it.startsWith("//")) {
                "https:$it"
            } else if (it.startsWith("/")) {
                "$baseUrl$it"
            } else {
                it
            }
        }
        ?: return null

    val parsed = normalized.toHttpUrlOrNull() ?: return normalized
    if (!parsed.host.contains("ibyteimg.com", ignoreCase = true)) return normalized
    if (parsed.encodedPath.contains("~tplv-", ignoreCase = true)) return normalized
    if (!parsed.encodedPath.startsWith("/obj/")) return normalized

    val host = parsed.host.replace(IBYTE_AD_MARKER, IBYTE_LP_MARKER)
    val objectPath = parsed.encodedPath.removePrefix("/obj/")
    return "https://$host/$objectPath$IBYTE_THUMBNAIL_SUFFIX"
}

private fun parseStatus(status: Int?): Int = when (status) {
    1 -> SManga.ONGOING
    2 -> SManga.COMPLETED
    else -> SManga.UNKNOWN
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

private fun parseDate(dateStr: String?): Long = dateStr?.takeIf { it.isNotBlank() }?.let(dateFormat::tryParse) ?: 0L

@Serializable
class BooksResponse(
    private val books: List<Book>,
    val countBook: Int? = null,
) {
    fun toSMangaList(baseUrl: String): List<SManga> = books.map { it.toSManga(baseUrl) }
}

@Serializable
class SideHomeResponse(
    private val topBooksView: List<TopBook>,
) {
    fun toSMangaList(baseUrl: String): List<SManga> = topBooksView.map { it.toSManga(baseUrl) }
}

@Serializable
class BookDetailResponse(
    private val book: BookDetail,
) {
    fun toSManga(baseUrl: String): SManga = book.toSManga(baseUrl)
}

@Serializable
class ChaptersResponse(
    private val chapters: List<Chapter>,
) {
    fun toSChapterList(): List<SChapter> = chapters.map { it.toSChapter() }
}

@Serializable
class Book(
    private val bookId: Int,
    private val title: String,
    private val status: Int? = null,
    private val covers: List<Cover> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/books/$bookId"
        title = this@Book.title.trim()
        thumbnail_url = resolveThumbnailUrl(covers.firstOrNull()?.url, baseUrl)
        status = parseStatus(this@Book.status)
    }
}

@Serializable
class TopBook(
    private val bookId: Int,
    private val title: String,
    private val status: Int? = null,
    private val covers: List<Cover> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/books/$bookId"
        title = this@TopBook.title.trim()
        thumbnail_url = resolveThumbnailUrl(covers.firstOrNull()?.url, baseUrl)
        status = parseStatus(this@TopBook.status)
    }
}

@Serializable
class BookDetail(
    private val bookId: Int,
    private val title: String,
    private val status: Int? = null,
    private val description: String? = null,
    private val author: String? = null,
    private val covers: List<Cover> = emptyList(),
    private val tags: List<TagWrapper> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/books/$bookId"
        title = this@BookDetail.title.trim()
        thumbnail_url = resolveThumbnailUrl(covers.firstOrNull()?.url, baseUrl)
        author = this@BookDetail.author
        description = this@BookDetail.description
        genre = tags.joinToString { it.tag.name }
        status = parseStatus(this@BookDetail.status)
    }
}

@Serializable
class Chapter(
    private val bookId: Int,
    private val num: String,
    private val chapterNumber: Double,
    private val createdAt: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        val chapterNum = chapterNumber.toString().removeSuffix(".0")
        url = "/books/$bookId/$chapterNum"
        name = num
        date_upload = parseDate(createdAt)
    }
}

@Serializable
class Cover(
    val url: String,
)

@Serializable
class TagWrapper(
    val tag: Tag,
)

@Serializable
class Tag(
    val name: String,
)

@Serializable
class ChapterServer(
    val content: List<ChapterPage>,
)

@Serializable
class ChapterPage(
    val imageUrl: String,
    @SerialName("drm_data")
    val drmData: String? = null,
)
