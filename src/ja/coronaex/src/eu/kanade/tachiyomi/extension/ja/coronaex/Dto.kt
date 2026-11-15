package eu.kanade.tachiyomi.extension.ja.coronaex

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlin.time.Instant

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
    @SerialName("next_cursor") val nextCursor: String?,
    val resources: List<ChapterResources>,
)

@Serializable
class ChapterResources(
    @SerialName("episode_order") private val episodeOrder: Int,
    @SerialName("episode_status") private val episodeStatus: String?,
    private val id: String,
    @SerialName("published_at") private val publishedAt: String,
    private val title: String,
) {
    val isLocked: Boolean
        get() = episodeStatus == "only_for_subscription"

    fun toSChapter(): SChapter = SChapter.create().apply {
        url = id
        name = if (isLocked) {
            "\uD83D\uDCB3 $title"
        } else {
            title
        }

        chapter_number = episodeOrder.toFloat()
        date_upload = Instant.tryParse(publishedAt)
    }
}

@Serializable
class ViewerResponse(
    val pages: List<ViewerPages>,
)

@Serializable
class ViewerPages(
    @SerialName("page_image_url") val pageImageUrl: String,
)

@Suppress("unused")
@Serializable
class LoginRequestBody(
    private val email: String,
    private val password: String,
    private val returnSecureToken: Boolean,
)

@Suppress("unused")
@Serializable
class RefreshRequestBody(
    @SerialName("grant_type") private val grantType: String,
    @SerialName("refresh_token") private val refreshToken: String,
)

@Serializable
class LoginResponse(
    @JsonNames("id_token") val idToken: String,
    @JsonNames("refresh_token") val refreshToken: String,
)
