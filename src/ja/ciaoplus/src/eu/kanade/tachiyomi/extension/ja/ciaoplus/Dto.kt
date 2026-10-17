package eu.kanade.tachiyomi.extension.ja.ciaoplus

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    @SerialName("episode_free_updated") val episodeFreeUpdated: String?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = titleId.toString().padStart(5, '0')
        title = titleName
        thumbnail_url = thumbnailImageUrl
    }
}

@Serializable
class DetailResponse(
    @SerialName("title_list") val titleList: List<WebTitle>,
)

@Serializable
class WebTitle(
    @SerialName("title_id") private val titleId: Int,
    @SerialName("title_name") val titleName: String,
    @SerialName("author_text") private val authorText: String?,
    @SerialName("introduction_text") private val introductionText: String?,
    @SerialName("genre_id_list") val genreIdList: List<Int>?,
    @SerialName("episode_id_list") val episodeIdList: List<Int>?,
    @SerialName("new_episode_update_cycle_text") private val newEpisodeUpdateCycleText: String?,
    @SerialName("thumbnail_image_url") private val thumbnailImageUrl: String?,
) {
    fun toSManga(genres: String?): SManga = SManga.create().apply {
        url = titleId.toString().padStart(5, '0')
        title = titleName
        author = authorText
        description = buildString {
            introductionText?.let { append(it) }
            newEpisodeUpdateCycleText?.let { append("\n\n$it") }
        }
        genre = genres
        thumbnail_url = thumbnailImageUrl
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
    private val index: Int?,
    @SerialName("start_time") private val startTime: String?,
    @SerialName("title_id") private val titleId: Int,
) {
    fun toSChapter(mangaTitle: String): SChapter = SChapter.create().apply {
        url = episodeId.toString()
        val originalChapterName = episodeName.trim()
        name = if (originalChapterName.startsWith(mangaTitle.trim())) {
            // If entry title is in chapter name, that part of the chapter name is missing, so index is added here to the name
            "【第${index}話】 $originalChapterName"
        } else {
            originalChapterName
        }

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
    @SerialName("scramble_seed") val scrambleSeed: Long,
    @SerialName("scramble_ver") val scrambleVer: Int?,
)
