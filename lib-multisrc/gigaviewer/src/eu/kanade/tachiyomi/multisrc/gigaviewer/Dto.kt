package eu.kanade.tachiyomi.multisrc.gigaviewer

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

// GraphQL
@Suppress("unused")
@Serializable
class SearchVariables(
    private val keyword: String,
)

@Suppress("unused")
@Serializable
class SeriesVariables(
    private val id: String,
)

@Serializable
class SearchResponse(
    val searchSeries: SeriesConnection,
)

@Serializable
class SeriesListNode(
    val series: SeriesConnection,
)

@Serializable
class SeriesConnection(
    val edges: List<SeriesEdge>,
)

@Serializable
class SeriesEdge(
    val node: SeriesListItem,
)

@Serializable
class SeriesListItem(
    val databaseId: String,
    private val title: String,
    private val thumbnailUri: String?,
    private val firstEpisode: Permalink?,
    private val firstVolume: Permalink?,
    private val latestEpisode: LatestEpisode?,
    private val latestVolume: LatestVolume?,
    val likeCount: Int,
) {
    val latestPublishedAt: Long
        get() = Instant.tryParse(latestEpisode?.publishedAt)

    val isVolumeOnly: Boolean
        get() = firstEpisode == null

    fun toSManga(): SManga? {
        val permalink = (firstEpisode ?: firstVolume)?.permalink ?: return null
        return SManga.create().apply {
            url = databaseId
            title = this@SeriesListItem.title
            thumbnail_url = thumbnailUri ?: latestVolume?.thumbnailUri
            memo = buildJsonObject {
                put("path", permalink.toHttpUrl().encodedPath)
            }
        }
    }
}

@Serializable
class Permalink(
    val permalink: String,
)

@Serializable
class LatestEpisode(
    val publishedAt: String?,
)

@Serializable
class LatestVolume(
    val thumbnailUri: String?,
)

@Serializable
class SeriesResponse(
    val series: SeriesDetails,
)

@Serializable
class SeriesDetails(
    private val databaseId: String,
    private val title: String,
    private val author: Author?,
    private val description: String?,
    private val thumbnailUri: String?,
    private val firstEpisode: Permalink?,
    private val firstVolume: Permalink?,
    private val latestVolume: LatestVolume?,
    val episodes: TotalCount,
    val volumes: TotalCount,
) {
    fun toSManga() = SManga.create().apply {
        url = databaseId
        title = this@SeriesDetails.title
        author = this@SeriesDetails.author?.name
        description = this@SeriesDetails.description
        thumbnail_url = thumbnailUri ?: latestVolume?.thumbnailUri
        memo = buildJsonObject {
            put("path", (firstEpisode ?: firstVolume)!!.permalink.toHttpUrl().encodedPath)
        }
    }
}

@Serializable
class Author(
    val name: String,
)

@Serializable
class TotalCount(
    val totalCount: Int,
)

// Chapters
@Serializable
class ReadableProduct(
    @SerialName("readable_product_id") private val readableProductId: String,
    private val title: String,
    @SerialName("display_open_at") private val displayOpenAt: String?,
    @SerialName("viewer_uri") private val viewerUri: String,
    @SerialName("purchase_info") val purchaseInfo: PurchaseInfo,
    val status: JsonObject?,
) {
    val isLocked: Boolean
        get() = !purchaseInfo.canRead && !purchaseInfo.unavailable

    fun toSChapter(isUnavailable: Boolean) = SChapter.create().apply {
        val type = viewerUri.toHttpUrl().pathSegments.first()
        val prefix = when {
            isUnavailable -> "🔒 "
            isLocked -> "💴 "
            else -> ""
        }
        val volumePrefix = if (type == "volume") "(Volume) " else ""
        url = readableProductId
        name = prefix + volumePrefix + title
        date_upload = Instant.tryParse(displayOpenAt)
        memo = buildJsonObject {
            put("type", type)
        }
    }
}

@Serializable
class PurchaseInfo(
    @SerialName("can_read") val canRead: Boolean,
    val unavailable: Boolean,
)

// Viewer
@Serializable
class ViewerDto(
    val readableProduct: ViewerReadableProduct,
)

@Serializable
class ViewerReadableProduct(
    val pageStructure: PageStructure?,
)

@Serializable
class PageStructure(
    val pages: List<PageDto>,
    val choJuGiga: String,
)

@Serializable
class PageDto(
    val src: String?,
    val type: String?,
)
