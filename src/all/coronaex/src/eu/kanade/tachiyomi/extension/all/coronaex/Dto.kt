package eu.kanade.tachiyomi.extension.all.coronaex

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class TitleResponse(
    @SerialName("next_cursor") val nextCursor: String?,
    val resources: List<TitleResources>,
)

@Serializable
class TitleResources(
    @SerialName("cover_image_url") private val coverImageUrl: String,
    private val id: String,
    private val title: String,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = id
        title = this@TitleResources.title
        thumbnail_url = coverImageUrl
    }
}

@Serializable
class TitleDetails(
    private val authors: List<Author>?,
    private val copyright: String?,
    @SerialName("cover_image_url") private val coverImageUrl: String?,
    private val description: String?,
    private val genres: List<Genre>?,
    private val id: String,
    private val title: String,
    @JsonNames("title_alphanumeric", "title_yomigana") private val altTitle: String?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = id
        title = this@TitleDetails.title
        author = this@TitleDetails.authors?.joinToString { "${it.role}: " + it.name }
        description = buildString {
            append(this@TitleDetails.description)
            if (!copyright.isNullOrBlank()) {
                append("\n\n$copyright")
            }

            if (!altTitle.isNullOrBlank()) {
                append("\n\nAlternative Title: ", altTitle)
            }
        }

        genre = genres?.joinToString { it.name }
        thumbnail_url = coverImageUrl
    }
}

@Serializable
class Author(
    val name: String,
    val role: String,
)

@Serializable
class Genre(
    val name: String,
)

@Serializable
class ChapterDetails(
    val resources: List<ChapterResources>,
)

@Serializable
class ChapterResources(
    @SerialName("episode_order") private val episodeOrder: Int,
    @SerialName("episode_status") val episodeStatus: String?,
    private val id: String,
    @SerialName("published_at") private val publishedAt: String,
    private val title: String,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        url = id
        val isPaid = episodeStatus == "only_for_subscription"
        name = if (isPaid) {
            "\uD83D\uDCB3 $title"
        } else {
            title
        }

        chapter_number = episodeOrder.toFloat()
        date_upload = dateFormat.tryParse(publishedAt)
    }
}

@Serializable
class ViewerResponse(
    val pages: List<ViewerPages>,
)

@Serializable
class ViewerPages(
    @SerialName("drm_hash") val drmHash: String,
    @SerialName("page_image_url") val pageImageUrl: String,
)

@Suppress("unused")
@Serializable
class LoginRequestBody(
    val email: String,
    val password: String,
    val returnSecureToken: Boolean,
)

@Suppress("unused")
@Serializable
class RefreshRequestBody(
    @SerialName("grant_type") val grantType: String,
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
class LoginResponse(
    @JsonNames("id_token") val idToken: String,
    @JsonNames("refresh_token") val refreshToken: String,
)

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT)
