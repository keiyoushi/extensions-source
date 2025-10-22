package eu.kanade.tachiyomi.extension.ja.takecomic

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable

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
        return this.episodes.mapNotNull { episode ->
            val accessInfo = accessMap[episode.id]
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
                name = episode.title
                date_upload = episode.datePublished * 1000L
                when {
                    isCampaignLocked -> {
                        name = "➡\uFE0F $name"
                        url = "/episodes/${episode.id}#${TakeComic.LOGIN_SUFFIX}"
                    }
                    isLocked -> {
                        name = "🔒 $name"
                        url = "/episodes/${episode.id}"
                    }
                    else -> {
                        url = "/episodes/${episode.id}"
                    }
                }
            }
        }
    }
}

@Serializable
class SeriesSummary(
    private val name: String,
    private val description: String,
    private val author: List<Author>,
    private val images: List<SeriesImage>,
    private val tag: List<Tag>,
) {
    fun toSManga(seriesHash: String): SManga = SManga.create().apply {
        url = "/series/$seriesHash"
        title = this@SeriesSummary.name
        author = this@SeriesSummary.author.joinToString { it.name }
        artist = author
        description = try {
            this@SeriesSummary.description.parseAs<List<DescriptionNode>>()
                .joinToString("\n") { node -> node.children.joinToString { it.text } }
        } catch (e: Exception) {
            this@SeriesSummary.description
        }
        genre = this@SeriesSummary.tag.joinToString { it.name }
        thumbnail_url = this@SeriesSummary.images.joinToString { it.url }
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
    private val images: List<SeriesImage>,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = "/series/$id"
        title = name
        thumbnail_url = images.joinToString { it.url }
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
    val content: List<EpisodeContent> = emptyList(),
)

@Serializable
class EpisodeContent(
    val type: String,
    val viewerId: String? = null,
)
