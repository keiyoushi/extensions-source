package eu.kanade.tachiyomi.extension.ja.jumptoon

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
class RankingResponse(
    val seriesRanking: SeriesRanking,
)

@Serializable
class SeriesRanking(
    val series: Series,
)

@Serializable
class Series(
    private val id: String,
    private val name: String,
    private val seriesThumbnailV2ImageUrl: String?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = id
        title = name
        thumbnail_url = seriesThumbnailV2ImageUrl
    }
}

@Serializable
class SeriesDetails(
    private val name: String,
    private val description: String?,
    private val seriesThumbnailV2ImageUrl: String?,
    private val seriesStatusType: String?,
    private val copyright: String?,
    private val authorList: List<SeriesAuthor>?,
) {
    fun toSManga(genres: List<String>?): SManga = SManga.create().apply {
        title = name
        description = buildList {
            this@SeriesDetails.description?.let(::add)
            copyright?.let(::add)
        }.joinToString("\n\n")
        thumbnail_url = seriesThumbnailV2ImageUrl
        author = authorList?.joinToString { it.author.name }
        genre = genres?.joinToString()
        val seriesStatus = seriesStatusType?.lowercase()
        status = if (seriesStatus == "completed" || seriesStatus == "one_shot") SManga.COMPLETED else SManga.ONGOING
    }
}

@Serializable
class SeriesAuthor(
    val author: Author,
)

@Serializable
class Author(
    val name: String,
)

@Serializable
class EpisodeListResponse(
    val episodes: EpisodeConnection,
    val totalPageCount: Int,
)

@Serializable
class EpisodeConnection(
    val edges: List<EpisodeEdge>,
)

@Serializable
class EpisodeEdge(
    val node: Episode,
)

@Serializable
class Episode(
    private val id: String,
    private val seriesId: String,
    private val number: String?,
    private val notation: String,
    private val title: String?,
    private val publishStartDatetime: String?,
    private val offerType: String?,
    private val userSeriesEpisode: UserSeriesEpisode?,
) {
    val isLocked: Boolean
        get() = offerType != "FREE" && userSeriesEpisode?.isUnlocked != true

    fun toSChapter(): SChapter = SChapter.create().apply {
        url = id
        val lock = if (isLocked) "🔒 " else ""
        name = if (title != null) "$lock$notation - $title" else "$lock$notation"
        chapter_number = number?.toFloat() ?: -1f
        date_upload = publishStartDatetime?.toLong() ?: 0L
        memo = buildJsonObject {
            put("seriesId", seriesId)
        }
    }
}

@Serializable
class UserSeriesEpisode(
    private val isPurchased: Boolean,
    private val rentalFinishedAt: String?,
) {
    val isUnlocked: Boolean
        get() = isPurchased || (rentalFinishedAt?.toLong() ?: 0L) > System.currentTimeMillis()
}

@Serializable
class EpisodeContent(
    val seriesId: String,
    val number: String,
    val scrambleAlgorithmType: String,
    val pageList: List<ContentPage>,
)

@Serializable
class ContentPage(
    val width: String,
    val imageUrl: String,
)
