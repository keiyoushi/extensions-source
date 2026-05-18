package eu.kanade.tachiyomi.extension.ja.amebamanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class RankingResponse(
    private val totalCount: Int,
    private val offset: Int,
    val titleRankResponses: List<TitleResponse>,
) {
    fun hasNextPage() = offset < totalCount
}

@Serializable
class TitleResponse(
    private val titleId: Int,
    private val titleName: String,
    private val imageUrl: String?,
    private val maxBook: MaxBook?,
) {
    fun toSManga() = SManga.create().apply {
        url = titleId.toString()
        title = titleName
        thumbnail_url = imageUrl ?: maxBook?.imageUrl
    }
}

@Serializable
class LatestResponse(
    val totalCount: Int,
    @JsonNames("results") val books: List<TitleResponse>,
)

@Serializable
class MaxBook(
    val imageUrl: String?,
)

@Serializable
class DetailsResponse(
    private val name: String,
    private val imageUrl: String?,
    private val categories: List<Category>?,
    private val pub: Pub?,
    private val description: String?,
    private val authors: List<Author>?,
    private val completeFlg: Boolean?,
    private val metaList: List<Meta>?,
    private val eroticType: Int?,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        thumbnail_url = imageUrl
        author = authors?.joinToString { it.name }
        description = buildString {
            this@DetailsResponse.description?.let { append(it) }
            if (pub != null) {
                append("\n\nPublisher: ${pub.name}")
            }
            if (eroticType == 4) {
                append("\n\n18+")
            }
        }
        genre = buildList {
            categories?.mapTo(this) { it.name }
            metaList?.mapTo(this) { it.label }
        }.joinToString()
        status = if (completeFlg == true) SManga.COMPLETED else SManga.ONGOING
    }
}

@Serializable
class Category(
    val name: String,
)

@Serializable
class Pub(
    val name: String,
)

@Serializable
class Author(
    val name: String,
)

@Serializable
class Meta(
    val label: String,
)

@Serializable
class ChapterResponse(
    val books: List<Book>,
)

@Serializable
class Book(
    val id: Int,
    private val contentsName: String,
    private val vol: Int?,
    private val discount: Discount?,
    private val startDatetime: String?,
) {
    val isLocked: Boolean
        get() = discount?.type != "FREE"

    fun isLockedFor(ownedIds: Set<Int>?) = if (ownedIds != null) id !in ownedIds else isLocked

    fun toSChapter(ownedIds: Set<Int>? = null) = SChapter.create().apply {
        val lock = if (isLockedFor(ownedIds)) "🔒 " else ""
        url = id.toString()
        name = lock + contentsName
        date_upload = dateFormat.tryParse(startDatetime)
        chapter_number = vol?.toFloat() ?: -1f
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ", Locale.ROOT)

@Serializable
class Discount(
    val type: String,
)

@Serializable
class OwnedResponse(
    val userBooks: List<UserBook>,
)

@Serializable
class UserBook(
    val bookId: Int,
    private val possessionStatus: String,
) {
    val isOwned: Boolean
        get() = possessionStatus == "OWNED"
}

@Serializable
class ViewerResponse(
    val result: ViewerResult,
)

@Serializable
class ViewerResult(
    val guardianServer: String,
    val signedParams: String,
    val bookData: BookData,
    val keys: JsonElement?,
)

@Serializable
class BookData(
    @SerialName("s3_key") val s3Key: String,
    @SerialName("imaged_reflow") val imagedReflow: Boolean = false,
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
