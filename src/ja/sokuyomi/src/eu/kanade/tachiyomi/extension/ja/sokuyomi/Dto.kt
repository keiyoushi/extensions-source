package eu.kanade.tachiyomi.extension.ja.sokuyomi

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

// Variables
@Suppress("unused")
@Serializable
class ListVariables(
    private val perPage: Int,
    private val pageNumber: Int,
    private val field: String,
    private val sort: String,
    private val keyword: String? = null,
    private val tagSlug: String? = null,
)

@Suppress("unused")
@Serializable
class DetailsVariables(
    private val titleSlug: String,
)

@Suppress("unused")
@Serializable
class ViewerVariables(
    private val slug: String,
)

@Suppress("unused")
@Serializable
class LoginVariables(
    @SerialName("mail_address") private val mailAddress: String,
    private val password: String,
)

@Suppress("unused")
@Serializable
class RefreshVariables(
    @SerialName("refresh_token") private val refreshToken: String,
)

// Responses
@Serializable
class SeriesResponse(
    val listTitle: ListTitle,
)

@Serializable
class ListTitle(
    val pageInfo: PageInfo,
    val edges: List<Edge>,
)

@Serializable
class PageInfo(
    private val totalPage: Int,
    private val currentPage: Int,
) {
    fun hasNextPage() = (currentPage + 1) < totalPage
}

@Serializable
class Edge(
    val node: Node,
)

@Serializable
class Node(
    private val name: String,
    private val slug: String,
    @SerialName("title_cover") private val titleCover: TitleCover?,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        url = slug
        title = name
        thumbnail_url = "$cdnUrl/${titleCover?.key}"
    }
}

@Serializable
class TitleCover(
    val key: String?,
    @SerialName("origin_key") val originKey: String?,
)

@Serializable
class DetailsResponse(
    val getTitle: GetTitle,
    val listVolume: ChapterList,
    val listChapter: ChapterList,
)

@Serializable
class GetTitle(
    private val name: String,
    @SerialName("name_hiragana") private val nameHiragana: String?,
    @SerialName("name_katakana") private val nameKatakana: String?,
    private val description: String?,
    @SerialName("is_adult") private val isAdult: Boolean?,
    @SerialName("is_finished") private val isFinished: Boolean?,
    private val label: Label?,
    private val genre: Genre?,
    @SerialName("title_cover") private val titleCover: TitleCover?,
    private val authors: List<Author>?,
    private val tags: List<Tag>?,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        title = name
        author = authors?.joinToString { it.name }
        description = buildString {
            this@GetTitle.description?.let { append(it) }

            val altTitles = listOfNotNull(
                nameHiragana?.takeIf { it.isNotEmpty() },
                nameKatakana?.takeIf { it.isNotEmpty() },
            )

            if (altTitles.isNotEmpty()) {
                append("\n\nAlternative Titles:\n")
                append(altTitles.joinToString("\n") { "- $it" })
            }

            label?.publisher?.name?.takeIf { it.isNotEmpty() }?.let {
                append("\n\nPublisher: $it")
            }

            if (isAdult == true) {
                append("\n\n18+")
            }
        }
        genre = buildList {
            this@GetTitle.genre?.name?.let { add(it) }
            tags?.mapTo(this) { it.name }
        }.joinToString()
        status = if (isFinished == true) SManga.COMPLETED else SManga.ONGOING
        thumbnail_url = "$cdnUrl/${titleCover?.originKey}"
    }
}

@Serializable
class Label(
    val publisher: Publisher?,
)

@Serializable
class Publisher(
    val name: String?,
)

@Serializable
class Author(
    val name: String,
)

@Serializable
class Genre(
    val name: String?,
)

@Serializable
class Tag(
    val name: String,
)

@Serializable
class ChapterList(
    val edges: List<ChapterEdge>,
)

@Serializable
class ChapterEdge(
    val node: ChapterNode,
)

@Serializable
class ChapterNode(
    @JsonNames("volume_number", "chapter_number") private val number: Float?,
    private val name: String,
    @SerialName("consumption_coin") private val consumptionCoin: Int?,
    @SerialName("opend_at") private val opendAt: String?,
    private val slug: String,
    @SerialName("is_purchase") private val isPurchase: Boolean?,
    @SerialName("is_available_for_sale") private val isAvailableForSale: Boolean?,
    @JsonNames("volume_consumption_coin", "chapter_consumption_coin") private val campaignCoin: ConsumptionCoin?,
) {
    val isLocked: Boolean
        get() = consumptionCoin != 0 && isPurchase != true && (campaignCoin?.consumptionCoin != 0 || isAvailableForSale != true)

    fun toSChapter(type: String) = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        url = slug
        name = lock + this@ChapterNode.name
        date_upload = Instant.tryParse(opendAt)
        chapter_number = number ?: -1f
        memo = buildJsonObject {
            put("type", type)
        }
    }
}

@Serializable
class ConsumptionCoin(
    @SerialName("consumption_coin") val consumptionCoin: Int,
)

@Serializable
class ViewerResponse(
    @JsonNames("getVolumeViewer", "getChapterViewer") val viewer: Viewer,
)

@Serializable
class Viewer(
    @JsonNames("volume_pages", "chapter_pages") val pages: List<ViewerPage>,
)

@Serializable
class ViewerPage(
    @SerialName("page_number") val pageNumber: Int,
    val key: String,
)

@Serializable
class LoginResponse(
    val signin: Signin,
)

@Serializable
class RefreshResponse(
    val token: Signin,
)

@Serializable
class Signin(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") val expiresAt: Long,
)
