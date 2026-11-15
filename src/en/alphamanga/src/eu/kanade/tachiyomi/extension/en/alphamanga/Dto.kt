package eu.kanade.tachiyomi.extension.en.alphamanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
class SearchResponse(
    val data: List<MangaData>,
    @SerialName("has_more") val hasMore: Boolean,
)

@Serializable
class MangaData(
    @SerialName("manga_sele_id") private val mangaSeleId: Int,
    private val title: String,
    @SerialName("banner_image_url") private val bannerImageUrl: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = mangaSeleId.toString()
        title = this@MangaData.title
        thumbnail_url = bannerImageUrl
    }
}

@Serializable
class ChapterResponse(
    val episodes: List<Episode>,
)

@Serializable
class Episode(
    @SerialName("story_no") private val storyNo: Int?,
    @SerialName("episode_no") private val episodeNo: Int,
    private val title: String,
    @SerialName("update_date") private val updateDate: String?,
    private val status: String?,
    @SerialName("is_purchased") private val isPurchased: Boolean?,
    @SerialName("is_on_rental") private val isOnRental: Boolean?,
) {
    val isLocked: Boolean
        get() = status != "free" && isPurchased == false && isOnRental == false

    fun toSChapter(titleId: String) = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        url = episodeNo.toString()
        name = lock + title
        chapter_number = storyNo?.toFloat() ?: -1f
        date_upload = dateFormat.tryParseDateTime(updateDate)
        memo = buildJsonObject {
            put("titleId", titleId)
        }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.ENGLISH)
