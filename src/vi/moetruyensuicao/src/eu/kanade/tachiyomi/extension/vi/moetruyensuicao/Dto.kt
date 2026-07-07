package eu.kanade.tachiyomi.extension.vi.moetruyensuicao

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// ============================== Response Envelope ================================

@Serializable
class ApiResponse<T>(val success: Boolean, val data: T? = null)

@Serializable
class ListApiResponse<T>(val success: Boolean, val data: List<T> = emptyList(), val meta: MetaData? = null)

@Serializable
class MetaData(val pagination: Pagination? = null)

@Serializable
class Pagination(val total: Int, val totalPages: Int)

// ============================== Manga ===========================================

@Serializable
class MangaItem(
    val id: Int,
    val slug: String,
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

@Serializable
class GenreListItem(val id: Int, val name: String, val count: Int? = null)

// ============================== Chapter =========================================

@Serializable
class ChapterListData(
    val manga: MangaBrief,
    val chapters: List<ChapterItem> = emptyList(),
)

@Serializable
class MangaBrief(val id: Int, val slug: String, val title: String)

@Serializable
class ChapterItem(
    val id: Int,
    val number: Double? = null,
    val numberText: String? = null,
    val title: String? = null,
    val date: String? = null,
    val pages: Int? = null,
    val groupName: String? = null,
    val access: String? = null,
    val viewCount: Int = 0,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        url = "/v2/chapters/$id"
        name = buildString {
            val numText = number?.let { it.toString().removeSuffix(".0") } ?: numberText
            if (!numText.isNullOrEmpty()) append("$numText")
            if (isEmpty()) append("${number ?: 0}")
        }
        chapter_number = number?.toFloat() ?: 0F
        date_upload = dateFormat.tryParse(date)
        scanlator = groupName
    }
}

// ============================== Chapter Reader ==================================

@Serializable
class ChapterReaderData(
    val manga: MangaBrief,
    val chapter: ReaderChapterInfo,
    val pageUrls: List<String> = emptyList(),
)

@Serializable
class ReaderChapterInfo(
    val id: Int,
    val number: Double? = null,
    val numberText: String? = null,
    val title: String? = null,
    val isOneshot: Boolean = false,
)

// ============================== Page Access (IMGX) =============================

@Serializable
class PageAccessRequest(val pageIndexes: List<Int>)

@Serializable
class PageAccessData(
    val chapterId: Int,
    val ttlMs: Int,
    val maxWindow: Int,
    val pages: List<PageAccessEntry> = emptyList(),
)

@Serializable
class PageAccessEntry(
    val pageIndex: Int,
    val pageNumber: Int,
    val storageKey: String,
    val downloadUrl: String,
    val grant: ImgxGrant? = null,
)

@Serializable
class ImgxGrant(
    val version: Int? = null,
    val algorithm: String? = null,
    val contentAlgorithm: String? = null,
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

// ============================== Utilities =======================================

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

private fun parseStatus(status: String?): Int = when (status) {
    "ongoing" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    "hiatus" -> SManga.ON_HIATUS
    "cancelled" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}
