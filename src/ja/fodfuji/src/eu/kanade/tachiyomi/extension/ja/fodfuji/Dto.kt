package eu.kanade.tachiyomi.extension.ja.fodfuji

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class RankingResponse(
    @SerialName("ranking_books") val rankingBooks: List<TitleResponse>,
)

@Serializable
class TitleResponse(
    @SerialName("book_id") private val bookId: String,
    @SerialName("book_name") private val bookName: String,
    @SerialName("episode_id") private val episodeId: String,
    private val thumbnail: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = "$bookId/$episodeId"
        title = bookName
        thumbnail_url = thumbnail
    }
}

@Serializable
class LatestResponse(
    @SerialName("current_page") private val currentPage: Int,
    private val total: Int,
    @SerialName("new_arrival_books") val newArrivalBooks: List<TitleResponse>,
) {
    fun hasNextPage() = currentPage < total
}

@Serializable
class SearchResponse(
    @SerialName("search_info") val searchInfo: SearchInfo,
    @SerialName("search_books") val searchBooks: List<TitleResponse>,
)

@Serializable
class SearchInfo(
    @SerialName("current_page") private val currentPage: Int,
    @SerialName("search_result_num") private val searchResultNum: Int,
) {
    fun hasNextPage() = currentPage < searchResultNum
}

@Serializable
class DetailsResponse(
    @SerialName("book_detail") val bookDetail: BookDetail,
    @SerialName("book_series") val bookSeries: List<BookSery>,
)

@Serializable
class BookDetail(
    @SerialName("book_name") private val bookName: String,
    @SerialName("book_review_long") private val bookReviewLong: String?,
    private val thumbnail: String?,
    private val authors: List<Author>?,
    private val publishers: List<Publisher>?,
    private val genres: List<Genre>?,
    @SerialName("sub_genres") private val subGenres: List<SubGenre>?,
) {
    fun toSManga() = SManga.create().apply {
        title = bookName
        thumbnail_url = thumbnail
        author = authors?.joinToString { it.name }
        description = buildString {
            bookReviewLong?.let { append(Jsoup.parseBodyFragment(it).text()) }
            if (publishers != null) {
                append("\n\nPublisher: ${publishers.joinToString { it.name }}")
            }
        }
        genre = buildList {
            genres?.mapTo(this) { it.name }
            subGenres?.mapTo(this) { it.name }
        }.joinToString()
    }
}

@Serializable
class BookSery(
    @SerialName("book_id") private val bookId: String,
    @SerialName("book_name") private val bookName: String,
    @SerialName("episode_id") private val episodeId: String,
    @SerialName("is_purchased") private val isPurchased: Boolean?,
    @SerialName("episode_price_start") private val episodePriceStart: String?,
    @SerialName("is_free") private val isFree: Boolean?,
    @SerialName("episode_count") private val episodeCount: Int?,
    @SerialName("is_sample") private val isSample: Boolean?,
) {
    val isLocked: Boolean
        get() = isFree != true && isPurchased != true

    fun toSChapter() = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        val preview = if (isSample == true && isLocked) "(Preview) " else ""
        val normalized = episodePriceStart?.removeRange(episodePriceStart.length - 3, episodePriceStart.length)
        url = "$bookId/$episodeId"
        name = lock + preview + bookName
        date_upload = dateFormat.tryParse(normalized)
        chapter_number = episodeCount?.toFloat() ?: -1f
    }
}

@Serializable
class Author(
    val name: String,
)

@Serializable
class Publisher(
    val name: String,
)

@Serializable
class Genre(
    val name: String,
)

@Serializable
class SubGenre(
    val name: String,
)

private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("Asia/Tokyo")
}

@Serializable
class ViewerResponse(
    @SerialName("GUARDIAN_SERVER") val guardianServer: String?,
    @SerialName("ADDITIONAL_QUERY_STRING") val additionalQueryString: String?,
    @SerialName("book_data") val bookData: BookData?,
    @SerialName("pages_data") val pagesData: PagesData?,
)

@Serializable
class BookData(
    @SerialName("s3_key") val s3Key: String,
    @SerialName("imaged_reflow") val imagedReflow: Boolean = false,
)

@Serializable
class PagesData(
    val keys: JsonElement?,
)

// for novels
@Serializable
class ReflowBook(
    val reflowData: ReflowData?,
)

@Serializable
class ReflowData(
    val profiles: List<ReflowProfile> = emptyList(),
)

@Serializable
class ReflowProfile(
    val id: String,
    val bookInfo: ReflowBookInfo,
)

@Serializable
class ReflowBookInfo(
    @SerialName("page_count") val pageCount: Int,
)
