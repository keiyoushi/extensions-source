package eu.kanade.tachiyomi.extension.ja.drecomics

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class RankingResponse(
    val items: List<RankingItem>,
    val pagination: Pagination,
)

@Serializable
class RankingItem(
    val series: Item,
)

@Serializable
class SeriesResponse(
    val items: List<Item>,
    val pagination: Pagination,
)

@Serializable
class Item(
    private val code: String,
    private val name: String,
    private val thumbnail: Thumbnail?,
) {
    fun toSManga() = SManga.create().apply {
        url = code
        title = name
        thumbnail_url = thumbnail?.cdnUrl
    }
}

@Serializable
class Pagination(
    @JsonNames("current_page") private val currentPage: Int,
    @JsonNames("total_pages") private val totalPages: Int,
) {
    fun hasNextPage() = currentPage < totalPages
}

@Serializable
class Thumbnail(
    @SerialName("cdn_url") val cdnUrl: String?,
)

@Serializable
class DetailsResponse(
    private val authors: List<Author>?,
    private val genres: List<Genre>?,
    @SerialName("is_adult") private val isAdult: Boolean?,
    private val name: String,
    @SerialName("next_update_schedule") private val nextUpdateSchedule: String?,
    private val status: String,
    private val summary: String?,
    private val thumbnail: Thumbnail?,
    @SerialName("update_interval") private val updateInterval: String?,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        author = authors?.joinToString { it.name }
        description = buildString {
            summary?.let { append(it) }
            if (updateInterval != null) {
                append("\n\n$updateInterval")
            }

            if (nextUpdateSchedule != null) {
                append("\n\n更新予定: $nextUpdateSchedule")
            }

            if (isAdult == true) {
                append("\n\n18+")
            }
        }
        genre = genres?.joinToString { it.name }
        status = when (this@DetailsResponse.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = thumbnail?.cdnUrl
    }
}

@Serializable
class Author(
    val name: String,
)

@Serializable
class Genre(
    val name: String,
)

@Serializable
class ChapterResponse(
    val items: List<ChapterItem>,
    val pagination: Pagination,
)

@Serializable
class ChapterItem(
    @SerialName("actual_price") private val actualPrice: Int?,
    private val code: String,
    @JsonNames("episode_number", "volume_number") private val episodeNumber: Int,
    @SerialName("is_purchased") private val isPurchased: Boolean,
    private val name: String,
    @SerialName("publish_at") private val publishAt: String?,
) {
    val isLocked: Boolean
        get() = !isPurchased && actualPrice != 0

    fun toSChapter() = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        url = code
        name = lock + this@ChapterItem.name
        chapter_number = episodeNumber.toFloat()
        date_upload = dateFormat.tryParse(publishAt)
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ", Locale.ROOT)

@Serializable
class ViewerResponse(
    val pages: List<Page>,
    @SerialName("session_key") val sessionKey: String,

)

@Serializable
class Page(
    @SerialName("image_url") val imageUrl: String,
    val iv: String,
    @SerialName("page_number") val pageNumber: Int,
)

@Serializable
class CsrfResponse(
    val csrfToken: String,
)

@Serializable
class NextAuthSignInResponse(
    val url: String?,
    val ok: Boolean?,
)

@Serializable
class SessionResponse(
    val accessToken: String?,
)
