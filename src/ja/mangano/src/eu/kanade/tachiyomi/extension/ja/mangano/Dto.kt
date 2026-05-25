package eu.kanade.tachiyomi.extension.ja.mangano

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// Variables
@Serializable
object EmptyVariables

@Suppress("unused")
@Serializable
class LatestVariables(
    private val after: String?,
)

@Suppress("unused")
@Serializable
class SearchVariables(
    private val keyword: String,
    private val after: String?,
)

@Suppress("unused")
@Serializable
class TagFilterVariables(
    private val title: String,
    private val first: Int,
    private val after: String?,
)

@Suppress("unused")
@Serializable
class IdVariables(
    private val id: String,
)

// Responses
@Serializable
class PopularResponse(
    val ranking: Ranking,
)

@Serializable
class Ranking(
    val monthly2: Monthly2,
)

@Serializable
class Monthly2(
    val edges: List<Edge>,
)

@Serializable
class Edge(
    val node: Node,
)

@Serializable
class Node(
    private val id: String,
    private val title: String,
    private val description: String?,
    private val isCompleted: Boolean?,
    private val coverImage: CoverImage?,
    private val user: User?,
    private val tags: List<Tag>?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = id
        title = this@Node.title
        thumbnail_url = coverImage?.url?.toHttpUrl()?.pathSegments?.last()
        description = this@Node.description
        status = if (isCompleted == true) SManga.COMPLETED else SManga.ONGOING
        author = user?.displayName
        genre = tags?.joinToString { it.title }
    }
}

@Serializable
class CoverImage(
    val url: String?,
)

@Serializable
class SeriesResponse(
    @JsonNames("search") val newWorks2: NewWorks2,
)

@Serializable
class TagResponse(
    val tag: TagWorks,
)

@Serializable
class TagWorks(
    val works: NewWorks2,
)

@Serializable
class NewWorks2(
    val edges: List<Edge>?,
    val pageInfo: PageInfo,
)

@Serializable
class PageInfo(
    val endCursor: String?,
    val hasNextPage: Boolean,
)

@Serializable
class User(
    val displayName: String?,
)

@Serializable
class Tag(
    val title: String,
)

@Serializable
class ChapterResponse(
    val node: ChapterNode,
)

@Serializable
class ChapterNode(
    val episodes: Episodes,
)

@Serializable
class Episodes(
    val edges: List<ChapterEdge>,
)

@Serializable
class ChapterEdge(
    val node: ChapterNodeX,
)

@Serializable
class ChapterNodeX(
    private val id: String,
    private val title: String,
    private val number: Int?,
    private val salesInfo: SalesInfo?,
    private val purchasedByViewer: Boolean?,
    private val canViewerSkipPaywall: Boolean?,
    private val publishedAt: String?,
) {
    private val isPaid get() = purchasedByViewer != true && canViewerSkipPaywall != true

    val isLocked: Boolean
        get() = isPaid && salesInfo?.pagesChargedFrom == 0

    val isPreview: Boolean
        get() = isPaid && (salesInfo?.pagesChargedFrom) != 0

    fun toSChapter(): SChapter = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        val preview = if (isPreview) "🔒 (Preview) " else ""
        url = id
        name = lock + preview + title
        chapter_number = number?.toFloat() ?: -1f
        date_upload = dateFormat.tryParse(publishedAt)
    }
}

@Serializable
class SalesInfo(
    val pagesChargedFrom: Int,
)

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Serializable
class ViewerResponse(
    val node: ViewerNode,
)

@Serializable
class ViewerNode(
    val allPagesConnection: AllPagesConnection,
)

@Serializable
class AllPagesConnection(
    val edges: List<ViewerEdge>,
)

@Serializable
class ViewerEdge(
    val node: ViewerNodeX,
)

@Serializable
class ViewerNodeX(
    val image: Image,
)

@Serializable
class Image(
    val url: String,
)

@Suppress("unused")
@Serializable
class LoginRequestBody(
    private val email: String,
    private val password: String,
    private val returnSecureToken: Boolean,
)

@Suppress("unused")
@Serializable
class RefreshRequestBody(
    @SerialName("grant_type") private val grantType: String,
    @SerialName("refresh_token") private val refreshToken: String,
)

@Suppress("unused")
@Serializable
class SecureTokenRequestBody(
    private val returnSecureToken: Boolean,
)

@Serializable
class LoginResponse(
    @JsonNames("id_token") val idToken: String,
    @JsonNames("refresh_token") val refreshToken: String,
)
