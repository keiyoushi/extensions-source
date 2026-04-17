package eu.kanade.tachiyomi.extension.ja.ebookjapan

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class RankingResponse(
    val rankingPublications: Publications,
)

@Serializable
class Publications(
    val totalResults: Int,
    val items: List<Items>,
)

@Serializable
class Items(
    private val title: Titles,
    private val goods: TitleGoods?,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        url = this@Items.title.titleId
        title = this@Items.title.name
        thumbnail_url = "$cdnUrl/${goods?.imageFileName}"
    }
}

@Serializable
class Titles(
    val titleId: String,
    val name: String,
)

@Serializable
class TitleGoods(
    val imageFileName: String?,
)

@Serializable
class DetailResponse(
    val serialStory: DetailTitles,
)

@Serializable
class DetailTitles(
    val serialStoryId: String,
    private val summary: String?,
    private val storyAuthor: String?,
    private val title: Title,
    private val automaticCoverImageName: String?,
    private val publisher: Publisher?,
    private val editorTags: List<EditorTag>?,
    private val storiesSummary: StoriesSummary?,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        title = this@DetailTitles.title.name
        author = storyAuthor
        description = buildString {
            summary?.let { append(it) }
            if (publisher != null) {
                append("\n\nPublisher: ${publisher.name}")
            }
        }
        genre = editorTags?.joinToString { it.name }
        status = if (storiesSummary?.isCompleteSerialStory == true) SManga.COMPLETED else SManga.ONGOING
        thumbnail_url = "$cdnUrl/$automaticCoverImageName"
    }
}

@Serializable
class Title(
    val name: String,
)

@Serializable
class StoriesSummary(
    val isCompleteSerialStory: Boolean?,
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
class ChapterResponse(
    val stories: List<ChapterStories>,
)

@Serializable
class ChapterStories(
    private val name: String,
    private val volumeSortNo: Int?,
    private val sellGoods: Goods?,
    private val freeTypeGoods: Goods?,
    private val isNormalFree: Boolean?,
    private val isPurchased: Boolean?,
    private val serialStory: SerialStory,
) {
    val isLocked: Boolean
        get() = isNormalFree == false && isPurchased == false

    fun toSChapter() = SChapter.create().apply {
        val goods = sellGoods ?: freeTypeGoods!!
        val lock = if (isLocked) "🔒 " else ""
        url = "${goods.bookCd}#${serialStory.serialStoryId}"
        name = lock + this@ChapterStories.name
        date_upload = if (sellGoods != null) dateFormat.tryParse(sellGoods.saleStartDatetime) else dateFormat.tryParse(freeTypeGoods?.saleStartDatetime)
        chapter_number = volumeSortNo?.toFloat() ?: -1f
    }
}

@Serializable
class SerialStory(
    val serialStoryId: String,
)

@Serializable
class Goods(
    val bookCd: String,
    val saleStartDatetime: String?,
)

private val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("Asia/Tokyo") }

@Suppress("unused")
@Serializable
class ViewerBody(
    private val type: String,
    private val code: String,
    private val ssid: String,
    private val light: Boolean,
)

@Serializable
class ViewerOpenBook(
    @SerialName("session_id") val sessionId: String,
    val payload: String,
)

@Serializable
class ViewerDrmResponse(
    @SerialName("file_id") val fileId: String,
    val payload: String,
)
