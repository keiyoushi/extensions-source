package eu.kanade.tachiyomi.extension.ja.unext

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Suppress("unused")
@Serializable
class Payload<T>(
    val operationName: String,
    val variables: T,
    val extensions: Extensions,
) {
    @Serializable
    class Extensions(
        val persistedQuery: PersistedQuery,
    ) {
        @Serializable
        class PersistedQuery(
            val version: Int,
            val sha256Hash: String,
        )
    }
}

// Variables
@Suppress("unused")
@Serializable
class PopularVariables(
    val targetCode: String,
    val page: Int,
    val pageSize: Int,
)

@Suppress("unused")
@Serializable
class LatestVariables(
    val tagCode: String,
    val page: Int,
    val pageSize: Int,
)

@Suppress("unused")
@Serializable
class SearchVariables(
    val query: String,
    val page: Int,
    val pageSize: Int,
    val filterSaleType: String?,
    val sortOrder: String,
)

@Suppress("unused")
@Serializable
class DetailsVariables(
    val bookSakuhinCode: String,
    val viewBookCode: String,
    val bookListPageSize: Int,
    val bookListChapterPageSize: Int,
)

@Suppress("unused")
@Serializable
class ChapterListVariables(
    val bookSakuhinCode: String,
    val booksPage: Int,
    val booksPageSize: Int,
)

@Suppress("unused")
@Serializable
class PageListVariables(
    val bookFileCode: String,
)

// Responses
@Serializable
class PageInfo(
    val page: Int,
    val pageSize: Int,
    val results: Int,
)

@Serializable
class PopularResponse(
    val data: Data,
) {
    @Serializable
    class Data(
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
}

@Serializable
class LatestResponse(
    val data: Data,
) {
    @Serializable
    class Data(
        @SerialName("webfront_newBooks")
        val newBooks: NewBooks,
    )

    @Serializable
    class NewBooks(
        val books: List<BookSakuhin>,
        val pageInfo: PageInfo,
    )
}

@Serializable
class SearchResponse(
    val data: Data,
) {
    @Serializable
    class Data(
        @SerialName("webfront_bookFreewordSearch")
        val search: SearchResult,
    )

    @Serializable
    class SearchResult(
        val books: List<BookSakuhin>,
        val pageInfo: PageInfo,
    )
}

@Serializable
class DetailsResponse(
    val data: Data,
) {
    @Serializable
    class Data(
        val bookTitle: BookSakuhin,
    )
}

@Serializable
class ChapterListResponse(
    val data: Data,
) {
    @Serializable
    class Data(
        @SerialName("bookTitle_books")
        val bookTitleBooks: BookTitleBooks,
    )

    @Serializable
    class BookTitleBooks(
        val books: List<Book>,
    )
}

@Serializable
class PlaylistResponse(
    val data: Data?,
) {
    @Serializable
    class Data(
        @SerialName("webfront_bookPlaylistUrl")
        val playlistUrl: PlaylistUrl,
    )

    @Serializable
    class PlaylistUrl(
        val playlistBaseUrl: String,
        val playlistUrl: UbookContainer,
    )

    @Serializable
    class UbookContainer(
        val ubooks: List<UBook>,
    )

    @Serializable
    class UBook(
        val content: String,
    )
}

@Serializable
class BookSakuhin(
    private val sakuhinCode: String,
    private val name: String,
    private val book: Book,
    private val detail: Detail?,
    private val isCompleted: Boolean?,
    private val subgenreTagList: List<SubgenreTag>?,
) {
    @Serializable
    class Detail(
        val introduction: String?,
    )

    fun toSManga(): SManga = SManga.create().apply {
        title = name
        url = "/book/title/$sakuhinCode"
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
class SubgenreTag(
    val name: String,
)

@Serializable
class Book(
    private val code: String,
    private val name: String,
    val thumbnail: Thumbnail?,
    private val publicStartDateTime: String?,
    val isFree: Boolean?,
    val isPurchased: Boolean?,
    val rightsExpirationDatetime: String?,
    val credits: List<Credit>?,
    val bookContent: BookContent?,
) {
    @Serializable
    class Thumbnail(
        val standard: String?,
    )

    @Serializable
    class BookContent(
        val mainBookFile: BookFile?,
    )

    @Serializable
    class BookFile(
        val code: String,
    )

    fun toSChapter(sakuhinCode: String): SChapter = SChapter.create().apply {
        val lock = if (isFree != true && isPurchased != true && rightsExpirationDatetime == null) "🔒 " else ""
        name = "$lock${this@Book.name}"
        val bookFileCode = bookContent?.mainBookFile?.code
        val fragment = if (bookFileCode != null) "#$bookFileCode" else ""
        url = "/book/view/$sakuhinCode/$code$fragment"
        date_upload = dateFormat.tryParse(publicStartDateTime)
    }

    @Serializable
    class Credit(
        val penName: String?,
        val bookAuthorType: String?,
    )
}

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
    val zipUrl: String,
    val zipStartOffset: Long,
    val localFileHeaderOffset: Long,
    val compressedSize: Int,
    val key: String,
    val iv: String,
    val originalFileSize: Long,
)

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
