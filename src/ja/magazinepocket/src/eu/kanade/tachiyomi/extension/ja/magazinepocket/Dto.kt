package eu.kanade.tachiyomi.extension.ja.magazinepocket

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
class RankingApiResponse(
    @JsonNames("title_list", "ranking_title_list")
    val rankingTitleList: List<RankingTitleId>,
)

@Serializable
class RankingTitleId(
    @JsonNames("title_id")
    val id: Int,
)

@Serializable
class TitleListResponse(
    @JsonNames("title_list", "search_title_list")
    val titleList: List<TitleDetail>,
)

@Serializable
class TitleDetail(
    @SerialName("title_id") private val titleId: Int,
    @SerialName("title_name") private val titleName: String,
    @SerialName("thumbnail_image_url") private val thumbnailImageUrl: String?,
    @SerialName("banner_image_url") private val bannerImageUrl: String?,
    @SerialName("thumbnail_rect_image_url") private val thumbnailRectImageUrl: String?,
    @SerialName("episode_free_updated") val episodeFreeUpdated: String?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = titleId.toString().padStart(5, '0')
        title = titleName
        thumbnail_url = thumbnailImageUrl ?: bannerImageUrl ?: thumbnailRectImageUrl
    }
}

@Serializable
class DetailResponse(
    @SerialName("title_list") val titleList: List<WebTitle>,
)

@Serializable
class WebTitle(
    @SerialName("title_id") private val titleId: Int,
    @SerialName("title_name") private val titleName: String,
    @SerialName("author_text") private val authorText: String?,
    @SerialName("introduction_text") private val introductionText: String?,
    @SerialName("genre_id_list") val genreIdList: List<Int>?,
    @SerialName("episode_id_list") val episodeIdList: List<Int>?,
    @SerialName("thumbnail_image_url") private val thumbnailImageUrl: String?,
    @SerialName("thumbnail_rect_image_url") private val thumbnailRectImageUrl: String?,
    @SerialName("banner_image_url") private val bannerImageUrl: String?,
) {
    fun toSManga(genres: String?): SManga = SManga.create().apply {
        url = titleId.toString().padStart(5, '0')
        title = titleName
        author = authorText
        description = introductionText
        genre = genres
        thumbnail_url = thumbnailImageUrl ?: bannerImageUrl ?: thumbnailRectImageUrl
    }
}

@Serializable
class GenreListResponse(
    @SerialName("genre_list") val genreList: List<GenreDetail>,
)

@Serializable
class GenreDetail(
    @SerialName("genre_name") val genreName: String,
)

@Serializable
class EpisodeListResponse(
    @SerialName("episode_list") val episodeList: List<Episode>,
)

@Serializable
class Episode(
    @SerialName("episode_id") private val episodeId: Int,
    @SerialName("episode_name") private val episodeName: String,
    private val index: Int?,
    @SerialName("start_time") private val startTime: String?,
    private val point: Int?,
    @SerialName("title_id") private val titleId: Int,
    private val badge: Int?,
    @SerialName("rental_finish_time") private val rentalFinishTime: String?,
) {
    val isLocked: Boolean
        get() = (point ?: 0) > 0 && badge != 3 && rentalFinishTime == null

    fun toSChapter(): SChapter = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        url = episodeId.toString()
        name = lock + episodeName
        chapter_number = index?.toFloat() ?: -1f
        date_upload = dateFormat.tryParseDateTime(startTime)
        memo = buildJsonObject {
            put("titleId", titleId.toString().padStart(5, '0'))
        }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Tokyo"))

@Serializable
class ViewerApiResponse(
    @SerialName("page_list") val pageList: List<String>,
    @SerialName("scramble_seed") val scrambleSeed: String,
    @SerialName("title_id") val titleId: Int,
    @SerialName("episode_id") val episodeId: Int,
)
