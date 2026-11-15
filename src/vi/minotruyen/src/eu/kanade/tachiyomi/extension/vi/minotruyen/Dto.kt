package eu.kanade.tachiyomi.extension.vi.minotruyen

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.time.Instant

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

private fun parseDate(dateStr: String?): Long = dateStr?.let(Instant::parseOrNull)?.toEpochMilliseconds() ?: 0L

@Serializable
class BooksResponse(
    private val meta: ResponseMeta,
    private val data: BooksData,
) {
    fun toSMangaList(baseUrl: String): List<SManga> = data.books.map { it.toSManga(baseUrl) }

    fun hasNextPage(page: Int): Boolean = page < meta.pageCount
}

@Serializable
class ResponseMeta(val pageCount: Int)

@Serializable
class BooksData(val books: List<Book>)

@Serializable
class BookDetailResponse(
    private val data: BookDetailData,
) {
    fun toSManga(baseUrl: String): SManga = data.book.toSManga(baseUrl)
}

@Serializable
class ChaptersResponse(
    private val data: ChaptersData,
) {
    fun toSChapterList(bookId: String): List<SChapter> = data.chapters.map { it.toSChapter(bookId) }
}

@Serializable
class Book(
    private val bookId: Int,
    private val info: BookInfo,
    private val cover: Cover? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/books/$bookId"
        title = info.title.trim()
        thumbnail_url = resolveThumbnailUrl(cover?.imageUrl, baseUrl)
    }
}

@Serializable
class BookDetailData(val book: BookDetail)

@Serializable
class BookDetail(
    private val bookId: Int,
    private val info: BookInfo,
    private val description: String? = null,
    private val cover: Cover? = null,
    private val authors: List<Author> = emptyList(),
    private val tags: List<Tag> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/books/$bookId"
        title = info.title.trim()
        thumbnail_url = resolveThumbnailUrl(cover?.imageUrl, baseUrl)
        author = authors.mapNotNull { it.author?.name }.joinToString()
        description = this@BookDetail.description
        genre = tags.joinToString { it.name }
    }
}

@Serializable
class Chapter(
    private val title: String? = null,
    private val chapterId: Int,
    private val chapterNumber: String,
    private val createdAt: String? = null,
) {
    fun toSChapter(bookId: String) = SChapter.create().apply {
        url = "/books/$bookId/$chapterId"
        name = title?.takeIf { it.isNotBlank() } ?: "Chapter $chapterNumber"
        chapter_number = chapterNumber.toFloatOrNull() ?: -1F
        date_upload = parseDate(createdAt)
    }
}

@Serializable
class ChaptersData(val chapters: List<Chapter>)

@Serializable
class BookInfo(val title: String)

@Serializable
class Cover(val imageUrl: String)

@Serializable
class Author(val author: AuthorInfo? = null)

@Serializable
class AuthorInfo(val name: String? = null)

@Serializable
class Tag(val name: String)

@Serializable
class TagsResponse(
    val data: TagsData,
)

@Serializable
class TagsData(val tags: List<TagOption>)

@Serializable
class TagOption(
    val name: String,
    val tagId: Int,
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

@Serializable
class ReaderChapter(
    val chapterId: Int,
    val images: List<ReaderImage>,
)

@Serializable
class ReaderImage(
    val order: Int,
    val servers: List<ReaderPage>,
)

@Serializable
class ReaderPage(
    val imageUrl: String,
    val drmData: String? = null,
)
