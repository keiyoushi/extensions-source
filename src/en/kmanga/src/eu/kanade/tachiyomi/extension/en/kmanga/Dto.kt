package eu.kanade.tachiyomi.extension.en.kmanga

import kotlinx.serialization.Serializable

@Serializable
class RankingApiResponse(
    val ranking_title_list: List<RankingTitleId>,
)

@Serializable
class RankingTitleId(
    val id: Int,
)

@Serializable
class TitleListResponse(
    val title_list: List<TitleDetail>,
)

@Serializable
class TitleDetail(
    val title_id: Int,
    val title_name: String,
    val thumbnail_rect_image_url: String? = null,
    val banner_image_url: String? = null,
)

@Serializable
class BirthdayCookie(val value: String, val expires: Long)

@Serializable
class EpisodeListResponse(val episode_list: List<Episode>)

@Serializable
class Episode(
    val episode_id: Int,
    val episode_name: String,
    val start_time: String,
    val point: Int,
    val title_id: Int,
    val badge: Int,
    val rental_finish_time: String? = null,
)

@Serializable
class ViewerApiResponse(
    val page_list: List<String>,
    val scramble_seed: Long,
)
