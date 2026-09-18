package eu.kanade.tachiyomi.extension.ja.ebookjapan

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
class RankingResponse(
    val rankingTitles: RankingTitles,
)

@Serializable
class RankingTitles(
    val totalResults: Int,
    val unificationTitles: List<UnificationTitle>,
)

@Serializable
class UnificationTitle(
    private val unificationTitleId: String,
    private val unificationTitleName: String,
    private val unificationTitleImage: List<CoverImage>?,
) {
    fun toSManga() = SManga.create().apply {
        url = unificationTitleId
        title = unificationTitleName
        thumbnail_url = unificationTitleImage?.firstNotNullOfOrNull { it.url }
    }
}

@Serializable
class CoverImage(
    private val coverImagefile: String?,
    private val coverImagePathType: Int?,
) {
    val url get() = coverUrl(coverImagePathType, coverImagefile)
}

@Serializable
class LatestResponse(
    val totalResults: Int,
    val items: List<LatestItem>,
)

@Serializable
class LatestItem(
    private val title: TitleRef,
    private val goods: Goods?,
) {
    fun toSManga() = SManga.create().apply {
        url = this@LatestItem.title.titleId
        title = this@LatestItem.title.name
        thumbnail_url = goods?.imageFileName?.toThumbnail()
    }
}

@Serializable
class TitleRef(
    val titleId: String,
    val name: String,
)

@Serializable
class Goods(
    val imageFileName: String?,
)

@Serializable
class SearchResponse(
    val totalResults: Int,
    val items: List<SearchTitle>,
)

@Serializable
class SearchTitle(
    private val titleId: String,
    private val name: String,
    private val lastPublication: LastPublication?,
) {
    fun toSManga() = SManga.create().apply {
        url = titleId
        title = name
        thumbnail_url = lastPublication?.goods?.imageFileName?.toThumbnail()
    }
}

@Serializable
class LastPublication(
    val goods: Goods?,
)

@Serializable
class DetailResponse(
    private val title: DetailTitle?,
    val serialStory: SerialStoryDetail?,
) {
    fun toSManga() = title?.toSManga() ?: serialStory!!.toSManga()
}

@Serializable
class DetailTitle(
    private val name: String,
    private val summary: String?,
    private val titleAuthor: Author?,
    private val publisher: Publisher?,
    private val editorTags: List<EditorTag>?,
    private val lastPublication: LastPublication?,
    private val isComplete: Boolean?,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        author = titleAuthor?.name
        description = buildString {
            summary?.let { append(it) }
            publisher?.let { append("\n\nPublisher: ", it.name) }
        }
        genre = editorTags?.joinToString { it.name }
        status = if (isComplete == true) SManga.COMPLETED else SManga.ONGOING
        thumbnail_url = lastPublication?.goods?.imageFileName?.toThumbnail()
    }
}

@Serializable
class SerialStoryDetail(
    val serialStoryId: String,
    private val title: TitleRef,
    private val summary: String?,
    private val author: Author?,
    private val publisher: Publisher?,
    private val editorTags: List<EditorTag>?,
    private val storiesSummary: StoriesSummary,
    private val manualCoverImagePathType: Int?,
    private val manualCoverImageName: String?,
    private val automaticCoverImageName: String?,
) {
    private val cover get() = when {
        !manualCoverImageName.isNullOrEmpty() -> coverUrl(manualCoverImagePathType, manualCoverImageName)
        !automaticCoverImageName.isNullOrEmpty() -> automaticCoverImageName.toThumbnail()
        else -> "$STORY_URL/thumb/${serialStoryId}_s.jpg"
    }

    fun toSManga() = SManga.create().apply {
        title = this@SerialStoryDetail.title.name
        author = this@SerialStoryDetail.author?.name
        description = buildString {
            summary?.let { append(it) }
            publisher?.let { append("\n\nPublisher: ", it.name) }
        }
        genre = editorTags?.joinToString { it.name }
        status = if (storiesSummary.isCompleteSerialStory) SManga.COMPLETED else SManga.ONGOING
        thumbnail_url = cover
    }
}

@Serializable
class StoriesSummary(
    val isCompleteSerialStory: Boolean,
)

@Serializable
class SerialStory(
    val serialStoryId: String,
)

@Serializable
class Author(
    val name: String?,
)

@Serializable
class Publisher(
    val name: String,
)

@Serializable
class EditorTag(
    val name: String,
)

@Serializable
class StoryListResponse(
    val stories: List<Story>,
)

@Serializable
class Story(
    private val name: String,
    private val volumeSortNo: Int?,
    private val sellGoods: StoryGoods?,
    private val freeTypeGoods: StoryGoods?,
    private val isNormalFree: Boolean?,
    private val isPurchased: Boolean?,
    private val serialStory: SerialStory,
) {
    val isLocked get() = isNormalFree != true && isPurchased != true

    fun toSChapter() = SChapter.create().apply {
        val goods = sellGoods ?: freeTypeGoods!!
        url = goods.bookCd
        name = (if (isLocked) "🔒 " else "") + this@Story.name
        date_upload = dateFormat.tryParseDateTime(goods.saleStartDatetime)
        chapter_number = volumeSortNo?.toFloat() ?: -1f
        memo = buildJsonObject {
            put(MEMO_TYPE, if (isPurchased == true) TYPE_PURCHASED else TYPE_STORY)
            put(MEMO_SSID, serialStory.serialStoryId)
        }
    }
}

@Serializable
class StoryGoods(
    val bookCd: String,
    val saleStartDatetime: String?,
)

@Serializable
class PublicationListResponse(
    val publications: List<Publication>?,
)

@Serializable
class Publication(
    private val name: String,
    private val volumeSortNo: Int?,
    private val saleDate: String?,
    private val goods: PublicationGoods,
    private val trialGoods: PublicationGoods?,
    private val isPurchased: Boolean?,
) {
    val isLocked get() = isPurchased != true && goods.isFree != true

    fun toSChapter() = SChapter.create().apply {
        val trial = trialGoods.takeIf { isLocked }
        url = (trial ?: goods).bookCd
        name = when {
            !isLocked -> ""
            trial != null -> "🔒 (Preview) "
            else -> "🔒 "
        } + this@Publication.name
        date_upload = dateFormat.tryParseDateTime(saleDate)
        chapter_number = volumeSortNo?.toFloat() ?: -1f
        memo = buildJsonObject {
            put(
                MEMO_TYPE,
                when {
                    isPurchased == true -> TYPE_PURCHASED
                    trial != null -> TYPE_TRIAL
                    else -> TYPE_FREE
                },
            )
        }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.of("Asia/Tokyo"))

@Serializable
class PublicationGoods(
    val bookCd: String,
    val isFree: Boolean?,
)

@Suppress("unused")
@Serializable
class OpenBookRequest(
    private val type: String,
    private val code: String,
    private val ssid: String?,
    private val light: Boolean,
)

@Serializable
class OpenBookResponse(
    @SerialName("session_id") val sessionId: String,
    val payload: String,
)

@Serializable
class DrmResponse(
    @SerialName("file_id") val fileId: String,
    @SerialName("format_id") private val formatId: String?,
    val code: String,
    val payload: String,
) {
    val isFixedLayout get() = formatId !in REFLOWABLE_FORMATS
}

const val MEMO_TYPE = "type"
const val MEMO_SSID = "ssid"
const val TYPE_FREE = "free"
private const val TYPE_TRIAL = "trial"
private const val TYPE_STORY = "story"
private const val TYPE_PURCHASED = "purchased"
private const val SERIES_COVER = 1
private const val VOLUME_COVER = 2
private const val VERTICAL_COVER = 3
private const val STORY_URL = "https://prod-contents-story-banner.akamaized.net/contents/story"
private val REFLOWABLE_FORMATS = setOf("2", "6", "8")
private fun String.toThumbnail() = "https://cache2-ebookjapan.akamaized.net/contents/thumb/l/$this"
private fun coverUrl(pathType: Int?, fileName: String?) = when {
    fileName.isNullOrEmpty() -> null
    pathType == SERIES_COVER -> "$STORY_URL/kanban/$fileName"
    pathType == VOLUME_COVER -> fileName.toThumbnail()
    pathType == VERTICAL_COVER -> "$STORY_URL/thumb/$fileName"
    else -> null
}
