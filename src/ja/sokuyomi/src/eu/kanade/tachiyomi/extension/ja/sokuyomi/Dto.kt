package eu.kanade.tachiyomi.extension.ja.sokuyomi

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// Variables
@Suppress("unused")
@Serializable
class PopularVariables(
    private val perPage: Int,
    private val pageNumber: Int,
    private val field: String,
    private val isAdult: Boolean?,
)

@Suppress("unused")
@Serializable
class SearchVariables(
    private val name: String,
    private val authorName: String,
    private val tagName: String,
    private val perPage: Int,
    private val pageNumber: Int,
    private val field: String,
    private val isAdult: Boolean?,
)

@Suppress("unused")
@Serializable
class TagFilterVariables(
    @SerialName("tag_slug") private val tagSlug: String,
    private val perPage: Int,
    private val pageNumber: Int,
)

@Suppress("unused")
@Serializable
class DetailsVariables(
    private val titleSlug: String,
)

@Suppress("unused")
@Serializable
class ChapterListVariables(
    private val titleSlug: String,
    private val perPage: Int,
    private val pageNumber: Int,
    private val sort: String,
)

@Suppress("unused")
@Serializable
class ViewerVariables(
    private val volumeSlug: String,
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
                append(altTitles.joinToString("\n"))
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
        status = if (isFinished == false) SManga.COMPLETED else SManga.ONGOING
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
class ChapterResponse(
    val listVolume: ListVolume,
)

@Serializable
class ListVolume(
    val edges: List<ChapterEdge>,
)

@Serializable
class ChapterEdge(
    val node: ChapterNode,
)

@Serializable
class ChapterNode(
    @SerialName("volume_number") private val volumeNumber: Float?,
    private val name: String,
    @SerialName("consumption_coin") private val consumptionCoin: Int?,
    @SerialName("opend_at") private val opendAt: String?,
    private val slug: String,
    @SerialName("is_purchase") private val isPurchase: Boolean?,
    @SerialName("is_available_for_sale") private val isAvailableForSale: Boolean?,
    @SerialName("volume_consumption_coin") private val volumeConsumptionCoin: VolumeConsumptionCoin?,
) {
    val isLocked: Boolean
        get() = consumptionCoin != 0 && isPurchase != true && (volumeConsumptionCoin?.consumptionCoin != 0 || isAvailableForSale != true)

    fun toSChapter() = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        url = slug
        name = lock + this@ChapterNode.name
        date_upload = dateFormat.tryParse(opendAt)
        chapter_number = volumeNumber ?: -1f
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("Asia/Tokyo")
}

@Serializable
class VolumeConsumptionCoin(
    @SerialName("consumption_coin") val consumptionCoin: Int,
)

@Serializable
class ViewerResponse(
    val getVolumeViewer: GetVolumeViewer,
)

@Serializable
class GetVolumeViewer(
    @SerialName("volume_pages") val volumePages: List<VolumePage>,
)

@Serializable
class VolumePage(
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
)
