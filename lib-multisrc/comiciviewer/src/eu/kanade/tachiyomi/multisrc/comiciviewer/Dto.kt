package eu.kanade.tachiyomi.multisrc.comiciviewer

import eu.kanade.tachiyomi.multisrc.comiciviewer.ComiciViewerAlt.Companion.LOGIN_SUFFIX
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable

@Serializable
class ViewerResponse(
    val result: List<PageDto>,
    val totalPages: Int,
)

@Serializable
class PageDto(
    val imageUrl: String,
    val scramble: String,
    val sort: Int,
)

@Serializable
class TilePos(
    val x: Int,
    val y: Int,
)

@Serializable
class ApiResponse(
    val series: SeriesData,
)

@Serializable
class SeriesData(
    val summary: SeriesSummary,
    private val episodes: List<Episode> = emptyList(),
) {
    fun toSChapter(accessMap: Map<String, EpisodeAccess>, showLocked: Boolean, showCampaignLocked: Boolean): List<SChapter> {
        return this.episodes.mapNotNull {
            val accessInfo = accessMap[it.id]
            val hasAccess = accessInfo?.hasAccess
            val isCampaign = accessInfo?.isCampaign
            val isLocked = !hasAccess!!
            val isCampaignLocked = isLocked && isCampaign!!

            if (isCampaignLocked && !showCampaignLocked) {
                return@mapNotNull null
            }
            if (isLocked && !isCampaignLocked && !showLocked) {
                return@mapNotNull null
            }

            SChapter.create().apply {
                name = it.title
                date_upload = it.datePublished * 1000L
                when {
                    isCampaignLocked -> {
                        name = "➡\uFE0F $name"
                        url = "/episodes/${it.id}#$LOGIN_SUFFIX"
                    }

                    isLocked -> {
                        name = "🔒 $name"
                        url = "/episodes/${it.id}"
                    }

                    else -> {
                        url = "/episodes/${it.id}"
                    }
                }
            }
        }
    }
}

@Serializable
class SeriesSummary(
    private val name: String,
    private val description: String?,
    private val author: List<Author>?,
    private val images: List<SeriesImage>?,
    private val tag: List<Tag>?,
    private val isCompleted: Boolean,
) {
    fun toSManga(seriesHash: String): SManga = SManga.create().apply {
        url = "/series/$seriesHash"
        title = name
        author = this@SeriesSummary.author?.joinToString { it.name }
        artist = author
        description = this@SeriesSummary.description?.parseAs<List<DescriptionNode>>()
            ?.joinToString("\n") { node -> node.children.joinToString { it.text } }
            ?.ifEmpty { this@SeriesSummary.description }
        genre = tag?.joinToString { it.name }
        thumbnail_url = images?.joinToString { it.url }
        status = if (isCompleted) SManga.COMPLETED else SManga.ONGOING
    }
}

@Serializable
class Author(
    val name: String,
)

@Serializable
class SeriesImage(
    val url: String,
)

@Serializable
class Tag(
    val name: String,
)

@Serializable
class Episode(
    val id: String,
    val title: String,
    val datePublished: Long,
)

@Serializable
class DescriptionNode(
    val children: List<DescriptionChild>,
)

@Serializable
class DescriptionChild(
    val text: String,
)

@Serializable
class AccessApiResponse(
    val seriesAccess: SeriesAccess,
)

@Serializable
class SeriesAccess(
    val episodeAccesses: List<EpisodeAccess>,
)

@Serializable
class EpisodeAccess(
    val episodeId: String,
    val hasAccess: Boolean,
    val isCampaign: Boolean,
)

@Serializable
class SearchApiResponse(
    val searchResult: SearchResult,
)

@Serializable
class SearchResult(
    val series: SeriesResult,
)

@Serializable
class SeriesResult(
    val total: Int,
    val series: List<SearchSeries>,
)

@Serializable
class SearchSeries(
    private val id: String,
    private val name: String,
    private val images: List<SeriesImage>?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = "/series/$id"
        title = name
        thumbnail_url = images?.joinToString { it.url }
    }
}

@Serializable
class UserInfoApiResponse(
    val user: UserData?,
)

@Serializable
class UserData(
    val id: String,
)

@Serializable
class EpisodeDetailsApiResponse(
    val episode: EpisodeDetails,
)

@Serializable
class EpisodeDetails(
    val content: List<EpisodeContent>,
)

@Serializable
class EpisodeContent(
    val type: String,
    val viewerId: String,
)
