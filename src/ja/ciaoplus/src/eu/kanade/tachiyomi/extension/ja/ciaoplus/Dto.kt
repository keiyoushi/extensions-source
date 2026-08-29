package eu.kanade.tachiyomi.extension.ja.ciaoplus

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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
    @SerialName("title_name") private val titleName: String,
    @SerialName("thumbnail_image_url") private val thumbnailImageUrl: String?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        val paddedId = titleId.paddedTitleId()
        url = "/comics/title/$paddedId"
        title = titleName
        thumbnail_url = thumbnailImageUrl
    }
}

@Serializable
class LatestResponse(
    @SerialName("today_weekday_index") val todayWeekdayIndex: Int,
    @SerialName("weekly_list") val weeklyList: List<Weekly>,
    @SerialName("title_list") val titleList: List<TitleDetail>,
)

@Serializable
class Weekly(
    @SerialName("title_id_list") val titleIdList: List<Int>,
    @SerialName("weekday_index") val weekdayIndex: Int,
)

@Serializable
class DetailResponse(
    @SerialName("title_list") val webTitle: List<WebTitle>,
)

@Serializable
class WebTitle(
    @SerialName("title_name") val titleName: String,
    @SerialName("author_text") private val authorText: String?,
    @SerialName("introduction_text") private val introductionText: String?,
    @SerialName("genre_id_list") val genreIdList: List<Int>?,
    @SerialName("episode_id_list") val episodeIdList: List<Int>,
    @SerialName("new_episode_update_cycle_text") val newEpisodeUpdateCycleText: String?,
) {
    fun toSManga(genre: String?): SManga = SManga.create().apply {
        title = titleName
        author = authorText
        description = buildString {
            introductionText?.let { append(it) }
            newEpisodeUpdateCycleText?.let { append("\n\n$it") }
        }
        this.genre = genre
    }
}

@Serializable
class GenreListResponse(
    @SerialName("genre_list") val genreList: List<GenreDetail>?,
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
    private val index: Int,
    @SerialName("start_time") private val startTime: String?,
    @SerialName("title_id") private val titleId: Int,
) {
    fun toSChapter(mangaTitle: String): SChapter = SChapter.create().apply {
        val paddedId = titleId.paddedTitleId()
        url = "/comics/title/$paddedId/episode/$episodeId"

        val originalChapterName = episodeName.trim()
        name = if (originalChapterName.startsWith(mangaTitle.trim())) {
            // If entry title is in chapter name, that part of the chapter name is missing, so index is added here to the name
            "【第${index}話】 $originalChapterName"
        } else {
            originalChapterName
        }

        chapter_number = index.toFloat()
        date_upload = dateFormat.tryParse(startTime)
    }
}

private fun Int.paddedTitleId(): String = toString().padStart(5, '0')
private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("Asia/Tokyo")
}

@Serializable
class ViewerApiResponse(
    @SerialName("page_list") val pageList: List<String>,
    @SerialName("scramble_seed") val scrambleSeed: Long,
    @SerialName("scramble_ver") val scrambleVer: Int,
)
