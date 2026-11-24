package eu.kanade.tachiyomi.extension.all.lemoon

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale
import kotlin.time.Instant

@Serializable
class PageDto<T>(
    val items: List<T>,
    @SerialName("page_number") private val pageNumber: Int,
    @SerialName("page_last_number") private val pageLastNumber: Int,
) {
    val hasNextPage get() = pageNumber < pageLastNumber
}

@Serializable
class ImageDto(
    private val category: String,
    private val name: String,
) {
    val url get() = "$CDN_URL/$category/$name"
}

@Serializable
class ComicDto(
    @SerialName("public_id") private val publicId: String,
    private val title: String,
    val format: String,
    @SerialName("cover_image") private val coverImage: ImageDto?,
) {
    fun toSManga() = SManga.create().apply {
        url = publicId
        title = this@ComicDto.title
        thumbnail_url = coverImage?.url
    }
}

@Serializable
class ComicDetailsDto(
    @SerialName("public_id") private val publicId: String,
    private val title: String,
    private val description: String?,
    private val status: String,
    private val locale: String,
    @SerialName("cover_image") private val coverImage: ImageDto?,
    private val comic: ComicInfoDto,
) {
    fun toSManga() = SManga.create().apply {
        url = publicId
        title = this@ComicDetailsDto.title
        thumbnail_url = coverImage?.url
        author = comic.createdBy.profile?.displayName
        description = MarkdownDescription.Builder()
            .description(this@ComicDetailsDto.description)
            .field("Language", Locale.forLanguageTag(locale).getDisplayLanguage(Locale.ENGLISH))
            .build()
        genre = comic.categories.joinToString { genreName(it) }.ifEmpty { null }
        status = when (this@ComicDetailsDto.status) {
            "published" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "paused" -> SManga.ON_HIATUS
            "canceled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        update_strategy = if (status == SManga.COMPLETED) UpdateStrategy.ONLY_FETCH_ONCE else UpdateStrategy.ALWAYS_UPDATE
    }
}

@Serializable
class ComicInfoDto(
    val categories: List<String>,
    @SerialName("created_by") val createdBy: CreatorDto,
)

@Serializable
class CreatorDto(
    val profile: ProfileDto?,
)

@Serializable
class ProfileDto(
    @SerialName("display_name") val displayName: String?,
)

@Serializable
class EpisodeDto(
    @SerialName("public_id") private val publicId: String,
    @SerialName("comic_translation") private val comicId: String,
    private val title: String,
    private val position: Int,
    @SerialName("published_at") private val publishedAt: String?,
    @SerialName("is_locked") private val isLocked: Boolean,
    @SerialName("is_member_locked") private val isMemberLocked: Boolean,
    @SerialName("is_mature") private val isMature: Boolean,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "/series/$comicId/$publicId"
        name = if (isLocked || isMemberLocked || isMature) "🔒 $title" else title
        chapter_number = position.toFloat()
        date_upload = Instant.tryParse(publishedAt)
        memo = buildJsonObject {
            put("locked", isLocked || isMemberLocked)
            put("mature", isMature)
        }
    }
}

@Serializable
class ContentDto(
    val media: ImageDto,
)

private const val CDN_URL = "https://cdn.lemoon.io"
