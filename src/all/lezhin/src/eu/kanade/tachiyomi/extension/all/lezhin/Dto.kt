package eu.kanade.tachiyomi.extension.all.lezhin

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val MEMO_ID = "id"

@Serializable
class DetailPageDto(val ssrDetail: SeriesPageContentDto?)

@Serializable
class ViewerPageDto(
    val episodeData: EpisodeDataDto,
)

@Serializable
class EpisodeDataDto(
    val result: EpisodeDto?,
    val error: ErrorDto?,
)

@Serializable
class ErrorDto(val code: String)

@Serializable
class EpisodeDto(
    val images: List<ImageDto>,
)

@Serializable
class ImageDto(
    val imagePath: String,
)

@Serializable
class ApiResultDto<T>(val data: T)

@Serializable
class ListItemDto(
    private val id: Long,
    private val alias: String,
    private val title: String,
    private val creators: String?,
    private val thumbnails: List<ThumbnailDto>,
) {
    fun toSManga() = SManga.create().apply {
        url = alias
        title = this@ListItemDto.title
        author = creators
        thumbnail_url = thumbnails.cover()
        memo = buildJsonObject { put(MEMO_ID, id) }
    }
}

@Serializable
class ThumbnailDto(
    val imagePath: String,
    val type: String,
)

// The square key art, the largest; the app crops it like the site's portrait card. Related titles only carry the card
private fun List<ThumbnailDto>.cover() = (firstOrNull { it.type == "MAIN" } ?: firstOrNull { it.type == "VERTICAL" })?.imagePath

@Serializable
class ContentDto(
    private val id: Long,
    private val alias: String,
    private val title: String,
    private val creators: List<CreatorDto>,
    private val tags: List<TagDto>,
    private val synopsis: String?,
    private val status: String,
    private val thumbnails: List<ThumbnailDto>,
) {
    // Keeps the other memo keys of a stored manga
    fun toSManga(previousMemo: JsonObject) = SManga.create().apply {
        url = alias
        title = this@ContentDto.title
        author = creators.filter { it.type != "ARTIST" }.joinToString { it.name }.ifEmpty { null }
        artist = creators.filter { it.type == "ARTIST" }.joinToString { it.name }.ifEmpty { null }
        genre = tags.joinToString { it.name }.ifEmpty { null }
        description = MarkdownDescription.Builder().description(synopsis).build()
        status = when (this@ContentDto.status) {
            "SCHEDULED" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "PAUSED" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        thumbnail_url = thumbnails.cover()
        memo = JsonObject(previousMemo + (MEMO_ID to JsonPrimitive(id)))
    }
}

@Serializable
class SeriesPageContentDto(val episodes: List<ContentEpisodeDto>)

@Serializable
class CreatorDto(val name: String, val type: String)

@Serializable
class TagDto(val name: String)

@Serializable
class ContentEpisodeDto(
    private val alias: String,
    private val title: String,
    private val subTitle: String?,
    private val openedAt: Long,
    private val possessionCoin: Int,
    private val eventPossessionCoin: Int?,
    private val purchaseStatus: String?,
) {
    val isPaid get() = (eventPossessionCoin ?: possessionCoin) > 0 && (purchaseStatus == null || purchaseStatus == "NONE")

    fun toSChapter(contentAlias: String) = SChapter.create().apply {
        url = "$contentAlias/$alias"
        name = buildString {
            if (isPaid) append("🔒 ")
            append(title)
            if (!subTitle.isNullOrEmpty()) append(" - ").append(subTitle)
        }
        date_upload = openedAt
    }
}

@Serializable
class SearchDto(
    val contents: List<ListItemDto>,
    val hasNext: Boolean,
    val nextPagination: String?,
)

@Serializable
class DetailsBodyDto(
    private val contentsIds: String,
    private val contentsThumbnailType: String,
    private val contentsType: String,
)

@Serializable
class GenreDto(val id: Long?, val name: String, val internalName: String)
