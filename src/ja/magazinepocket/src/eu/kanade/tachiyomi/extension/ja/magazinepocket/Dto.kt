package eu.kanade.tachiyomi.extension.ja.magazinepocket

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.text.SimpleDateFormat

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
    @SerialName("thumbnail_image_url") private val thumbnailImageUrl: String? = null,
    @SerialName("banner_image_url") private val bannerImageUrl: String? = null,
    @SerialName("thumbnail_rect_image_url") private val thumbnailRectImageUrl: String? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        val paddedId = titleId.toString().padStart(5, '0')
        url = "/title/$paddedId"
        title = titleName
        thumbnail_url = thumbnailImageUrl ?: bannerImageUrl ?: thumbnailRectImageUrl
    }
}

@Serializable
class EpisodeListResponse(
    @SerialName("episode_list") val episodeList: List<Episode>,
)

@Serializable
class Episode(
    @SerialName("episode_id") private val episodeId: Int,
    @SerialName("episode_name") private val episodeName: String,
    private val index: Int,
    @SerialName("start_time") private val startTime: String,
    private val point: Int,
    @SerialName("title_id") private val titleId: Int,
    private val badge: Int,
    @SerialName("rental_finish_time") private val rentalFinishTime: String? = null,
) {
    fun toSChapter(dateFormat: SimpleDateFormat): SChapter = SChapter.create().apply {
        val paddedId = titleId.toString().padStart(5, '0')
        url = "/title/$paddedId/episode/$episodeId"
        name = if (point > 0 && badge != 3 && rentalFinishTime == null) {
            "🔒 $episodeName"
        } else {
            episodeName
        }
        chapter_number = index.toFloat()
        date_upload = dateFormat.tryParse(startTime)
    }
}

@Serializable
class DetailResponse(
    @SerialName("web_title") val webTitle: WebTitle,
)

@Serializable
class WebTitle(
    @SerialName("title_name") val titleName: String,
    @SerialName("author_text") val authorText: String,
    @SerialName("introduction_text") val introductionText: String,
    @SerialName("genre_id_list") val genreIdList: List<Int>,
    @SerialName("episode_id_list") val episodeIdList: List<Int>,
    @SerialName("thumbnail_image_url") val thumbnailImageUrl: String? = null,
    @SerialName("thumbnail_rect_image_url") val thumbnailRectImageUrl: String? = null,
    @SerialName("banner_image_url") val bannerImageUrl: String? = null,
)

@Serializable
class ViewerApiResponse(
    @SerialName("page_list") val pageList: List<String>,
    @SerialName("scramble_seed") val scrambleSeed: Long,
)

@Serializable
class GenreListResponse(
    @SerialName("genre_list") val genreList: List<GenreDetail>,
)

@Serializable
class GenreDetail(
    @SerialName("genre_name") val genreName: String,
)
