package eu.kanade.tachiyomi.extension.vi.moetruyensuicao

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlin.time.Instant

// ============================== Response Envelope ================================

@Serializable
class ApiResponse<T>(val data: T? = null)

@Serializable
class ListApiResponse<T>(val data: List<T>, val meta: MetaData? = null)

@Serializable
class MetaData(val pagination: Pagination? = null)

@Serializable
class Pagination(val totalPages: Int)

// ============================== Manga ===========================================

@Serializable
class MangaItem(
    val id: Int,
    val title: String,
    val description: String? = null,
    val author: String? = null,
    val status: String? = null,
    val cover: String? = null,
    val coverUrl: String? = null,
    val genres: List<GenreItem>? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = id.toString()
        this.title = this@MangaItem.title
        thumbnail_url = coverUrl ?: cover
        author = this@MangaItem.author
        description = this@MangaItem.description
        genre = this@MangaItem.genres?.joinToString { it.name }
        status = parseStatus(this@MangaItem.status)
    }
}

// ============================== Genre ===========================================

@Serializable
class GenreItem(val id: Int, val name: String)

// ============================== Chapter =========================================

@Serializable
class ChapterListData(
    val chapters: List<ChapterItem>,
)

@Serializable
class ChapterItem(
    val id: Int,
    val number: Double? = null,
    val numberText: String? = null,
    val title: String? = null,
    val date: String? = null,
    val groupName: String? = null,
    val access: String? = null,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        url = "/v2/chapters/$id"
        name = number?.toString()?.removeSuffix(".0")
            ?: numberText
            ?: title
            ?: throw IllegalArgumentException("Chapter name missing")
        chapter_number = number?.toFloat() ?: 0F
        date_upload = date?.let { Instant.parseOrNull(it)?.toEpochMilliseconds() } ?: 0L
        scanlator = groupName
    }
}

// ============================== Chapter Reader ==================================

@Serializable
class ChapterReaderData(
    val chapter: ReaderChapterInfo,
    val pageUrls: List<String>,
)

@Serializable
class ReaderChapterInfo(val id: Int)

// ============================== Page Access (IMGX) =============================

@Serializable
class PageAccessRequest(val pageIndexes: List<Int>)

@Serializable
class PageAccessData(
    val pages: List<PageAccessEntry>,
)

@Serializable
class PageAccessEntry(
    val pageIndex: Int,
    val storageKey: String,
    val downloadUrl: String,
    val grant: ImgxGrant? = null,
)

@Serializable
class ImgxGrant(
    val version: Int? = null,
    val algorithm: String? = null,
    val imageId: String? = null,
    val issuedAt: Long? = null,
    val expiresAt: Long? = null,
    val nonce: String? = null,
    val keyNonce: String? = null,
    val signature: String? = null,
    val wrappedDecodeKey: String? = null,
    val wrappedContentKey: String? = null,
    val decodeKey: String? = null,
)

private fun parseStatus(status: String?): Int = when (status) {
    "ongoing" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    "hiatus" -> SManga.ON_HIATUS
    "cancelled" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}
