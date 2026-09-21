package eu.kanade.tachiyomi.extension.ja.unext

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

// Variables
@Suppress("unused")
@Serializable
class PopularVariables(
    private val targetCode: String,
    private val page: Int,
    private val pageSize: Int,
)

@Suppress("unused")
@Serializable
class LatestVariables(
    private val tagCode: String,
    private val page: Int,
    private val pageSize: Int,
)

@Suppress("unused")
@Serializable
class SearchVariables(
    private val query: String,
    private val page: Int,
    private val pageSize: Int,
    private val filterSaleType: String?,
    private val sortOrder: String,
)

@Suppress("unused")
@Serializable
class DetailsVariables(
    private val bookSakuhinCode: String,
    private val viewBookCode: String,
    private val bookListPageSize: Int,
    private val bookListChapterPageSize: Int,
)

@Suppress("unused")
@Serializable
class ChapterListVariables(
    private val bookSakuhinCode: String,
    private val booksPage: Int,
    private val booksPageSize: Int,
)

@Suppress("unused")
@Serializable
class PageListVariables(
    private val bookFileCode: String,
)

@Suppress("unused")
@Serializable
class ChallengeRequest(
    private val version: Int,
    @SerialName("play_token") private val playToken: String,
    private val nonce: String,
    private val kek: String,
    private val profile: String,
)

@Suppress("unused")
@Serializable
class LicenseRequest(
    private val challenge: String,
    private val signature: String,
)

// Responses
@Serializable
class PageInfo(
    private val page: Int,
    private val pages: Int,
) {
    fun hasNextPage() = page < pages
}

@Serializable
class PopularResponse(
    val bookRanking: BookRanking,
)

@Serializable
class BookRanking(
    val books: List<BookRankingSakuhin>,
    val pageInfo: PageInfo,
)

@Serializable
class BookRankingSakuhin(
    val bookSakuhin: BookSakuhin,
)

@Serializable
class LatestResponse(
    @SerialName("webfront_newBooks") val newBooks: BookList,
)

@Serializable
class SearchResponse(
    @SerialName("webfront_bookFreewordSearch") val search: BookList,
)

@Serializable
class BookList(
    val books: List<BookSakuhin>,
    val pageInfo: PageInfo,
)

@Serializable
class DetailsResponse(
    val bookTitle: BookSakuhin,
)

@Serializable
class ChapterListResponse(
    @SerialName("bookTitle_books")
    val bookTitleBooks: BookTitleBooks,
)

@Serializable
class BookTitleBooks(
    val books: List<Book>,
)

@Serializable
class BookSakuhin(
    private val sakuhinCode: String,
    private val name: String,
    private val book: Book,
    private val detail: Detail?,
    private val isCompleted: Boolean?,
    private val subgenreTagList: List<SubgenreTag>?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = name
        url = sakuhinCode
        thumbnail_url = book.thumbnail?.standard?.let { "https://$it" }
        description = detail?.introduction
        status = if (isCompleted == true) SManga.COMPLETED else SManga.ONGOING
        genre = subgenreTagList?.joinToString { it.name }
        author = book.credits?.mapNotNull { credit ->
            credit.penName?.let { name ->
                if (!credit.bookAuthorType.isNullOrEmpty()) {
                    "${credit.bookAuthorType}: $name"
                } else {
                    name
                }
            }
        }?.joinToString()
    }
}

@Serializable
class Detail(
    val introduction: String?,
)

@Serializable
class SubgenreTag(
    val name: String,
)

@Serializable
class Book(
    private val code: String,
    private val name: String,
    val thumbnail: Thumbnail?,
    private val publicStartDateTime: String?,
    private val isFree: Boolean?,
    private val isPurchased: Boolean?,
    private val bookNo: Int?,
    private val rightsExpirationDatetime: String?,
    val credits: List<Credit>?,
    private val bookContent: BookContent?,
) {
    val isLocked: Boolean
        get() = isFree != true && isPurchased != true && rightsExpirationDatetime == null

    fun toSChapter(sakuhinCode: String): SChapter = SChapter.create().apply {
        url = code
        name = (if (isLocked) "🔒 " else "") + this@Book.name
        date_upload = Instant.tryParse(publicStartDateTime)
        chapter_number = bookNo?.toFloat() ?: -1f
        memo = buildJsonObject {
            put("sakuhinCode", sakuhinCode)
            bookContent?.mainBookFile?.let {
                put("bookFileCode", it.code)
            }
        }
    }
}

@Serializable
class Thumbnail(
    val standard: String?,
)

@Serializable
class Credit(
    val penName: String?,
    val bookAuthorType: String?,
)

@Serializable
class BookContent(
    val mainBookFile: BookFile?,
)

@Serializable
class BookFile(
    val code: String,
)

@Serializable
class UserResponse(
    val unextUser: UnextUser,
)

@Serializable
class UnextUser(
    val id: String,
)

@Serializable
class PlaylistResponse(
    private val data: PlaylistData?,
    private val errors: List<ApiError>?,
) {
    val playlist get() = data?.playlist
    val errorCode get() = errors?.firstOrNull()?.extensions?.code
}

@Serializable
class PlaylistData(
    @SerialName("webfront_bookPlaylistUrl") val playlist: Playlist?,
)

@Serializable
class ApiError(
    val extensions: ApiErrorExtensions?,
)

@Serializable
class ApiErrorExtensions(
    val code: String?,
)

@Serializable
class Playlist(
    val playToken: String,
    val licenseUrl: String,
    private val playlistBaseUrl: String,
    private val playlistUrl: UBookList,
) {
    val zipUrl get() = "$playlistBaseUrl/${playlistUrl.ubooks.first().content}"
}

@Serializable
class UBookList(
    val ubooks: List<UBook>,
)

@Serializable
class UBook(
    val content: String,
)

@Serializable
class LicenseResponse(
    val license: License,
)

@Serializable
class License(
    val data: String,
    val iv: String,
)

@Serializable
class UBookIndex(
    val pages: Map<String, UBookPage>,
    val spine: List<UBookSpine>,
)

@Serializable
class UBookPage(
    val image: UBookImage,
)

@Serializable
class UBookImage(
    val src: String,
)

@Serializable
class UBookSpine(
    val pageId: String,
)

@Serializable
class UBookDrm(
    val encryptedFileList: Map<String, DrmFile>,
)

@Serializable
class DrmFile(
    val iv: String,
    val keyId: String,
    val originalFileSize: Long,
)

@Serializable
class ImageRequestData(
    val localHeaderOffset: Long,
    val compressedSize: Long,
    val method: Int,
    val key: String,
    val iv: String,
    val originalFileSize: Long,
)
