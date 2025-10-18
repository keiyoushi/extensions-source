package eu.kanade.tachiyomi.extension.en.kmanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class RankingApiResponse(
    @SerialName("ranking_title_list") val rankingTitleList: List<RankingTitleId>,
)

@Serializable
class RankingTitleId(
    val id: Int,
)

@Serializable
class TitleListResponse(
    @SerialName("title_list") val titleList: List<TitleDetail>,
)

@Serializable
class TitleDetail(
    @SerialName("title_id") val titleId: Int,
    @SerialName("title_name") val titleName: String,
    @SerialName("thumbnail_image_url") val thumbnailImageUrl: String? = null,
    @SerialName("banner_image_url") val bannerImageUrl: String? = null,
)

@Serializable
class LatestTitleListResponse(
    @SerialName("title_list") val titleList: List<LatestTitleDetail>,
)

@Serializable
class LatestTitleDetail(
    @SerialName("title_id") val titleId: Int,
    @SerialName("title_name") val titleName: String,
    @SerialName("thumbnail_rect_image_url") val thumbnailImageUrl: String? = null,
)

@Serializable
class BirthdayCookie(val value: String, val expires: Long)

@Serializable
class EpisodeListResponse(
    @SerialName("episode_list") val episodeList: List<Episode>,
)

@Serializable
class Episode(
    @SerialName("episode_id") val episodeId: Int,
    @SerialName("episode_name") val episodeName: String,
    @SerialName("start_time") val startTime: String,
    val point: Int,
    @SerialName("title_id") val titleId: Int,
    val badge: Int,
    @SerialName("rental_finish_time") val rentalFinishTime: String? = null,
)

@Serializable
class ViewerApiResponse(
    @SerialName("page_list") val pageList: List<String>,
    @SerialName("scramble_seed") val scrambleSeed: Long,
)

@Serializable
class SearchApiResponse(
    @SerialName("title_list") val titleList: List<SearchTitleDetail>,
)

@Serializable
class SearchTitleDetail(
    @SerialName("title_id") val titleId: Int,
    @SerialName("title_name") val titleName: String,
    @SerialName("thumbnail_image_url") val thumbnailImageUrl: String? = null,
)
