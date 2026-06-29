package eu.kanade.tachiyomi.extension.ja.ciaoplus

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat

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
    @SerialName("title_id") private val titleId: Int,
    @SerialName("title_name") private val titleName: String,
    @SerialName("thumbnail_image_url") private val thumbnailImageUrl: String?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        val paddedId = titleId.toString().padStart(5, '0')
        url = "/comics/title/$paddedId"
        title = titleName
        thumbnail_url = thumbnailImageUrl
    }
}

@Serializable
class LatestTitleListResponse(
    @SerialName("update_episode_titles") val updateEpisodeTitles: Map<String, List<LatestTitleDetail>>,
)

@Serializable
class LatestTitleDetail(
    @SerialName("title_id") val titleId: Int,
    @SerialName("title_name") private val titleName: String,
    @SerialName("thumbnail_image") private val thumbnailImageUrl: String?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        val paddedId = titleId.toString().padStart(5, '0')
        url = "/comics/title/$paddedId"
        title = titleName
        thumbnail_url = thumbnailImageUrl
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
    fun toSChapter(mangaTitle: String, dateFormat: SimpleDateFormat): SChapter = SChapter.create().apply {
        val paddedId = titleId.toString().padStart(5, '0')
        url = "/comics/title/$paddedId/episode/$episodeId"

        val originalChapterName = episodeName.trim()
        val chapterName = if (originalChapterName.startsWith(mangaTitle.trim())) {
            // If entry title is in chapter name, that part of the chapter name is missing, so index is added here to the name
            "【第${index}話】 $originalChapterName"
        } else {
            originalChapterName
        }

        // It is possible to read paid chapters even though you have to purchase them on the website, so leaving this here in case they change it
        /*
        name = if (point > 0 && badge != 3 && rentalFinishTime == null) {
            "🔒 $chapterName"
        } else {
            chapterName
        }
         */

        name = chapterName
        chapter_number = index.toFloat()
        date_upload = dateFormat.tryParse(startTime)
    }
}

@Serializable
class DetailResponse(
    @SerialName("title_list") val webTitle: List<WebTitle>,
)

@Serializable
class WebTitle(
    @SerialName("title_name") val titleName: String,
    @SerialName("author_text") val authorText: String,
    @SerialName("introduction_text") val introductionText: String,
    @SerialName("genre_id_list") val genreIdList: List<Int>,
    @SerialName("episode_id_list") val episodeIdList: List<Int>,
)

@Serializable
class ViewerApiResponse(
    @SerialName("page_list") val pageList: List<String>,
    @SerialName("scramble_seed") val scrambleSeed: Long,
    @SerialName("scramble_ver") val scrambleVer: Int,
)

@Serializable
class GenreListResponse(
    @SerialName("genre_list") val genreList: List<GenreDetail>,
)

@Serializable
class GenreDetail(
    @SerialName("genre_name") val genreName: String,
)
