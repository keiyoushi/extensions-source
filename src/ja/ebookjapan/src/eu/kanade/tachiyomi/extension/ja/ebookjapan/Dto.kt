package eu.kanade.tachiyomi.extension.ja.ebookjapan

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone


@Serializable
class RankingResponse(
    val rankingPublications: RankingPublications,
)

@Serializable
class RankingPublications(
    val totalResults: Int,
    val items: List<RankingItems>,
)

@Serializable
class RankingItems(
    private val title: RankingTitles,
    private val goods: RankingGoods?,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        url = this@RankingItems.title.titleId
        title = this@RankingItems.title.name
        thumbnail_url = "$cdnUrl/${goods?.imageFileName}"
    }
}

@Serializable
class RankingTitles(
    val titleId: String,
    val name: String,
)

@Serializable
class RankingGoods(
    val imageFileName: String?,
)

@Serializable
class DetailResponse(
    val title: DetailTitles,
    val serialStory: SerialStory,
)

@Serializable
class DetailTitles(
    private val name: String,
    private val summary: String?,
    private val titleAuthor: TitleAuthor?,
    private val publisher: Publisher?,
    private val editorTags: List<EditorTag>?,
    private val isComplete: Boolean?,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        title = name
        author = titleAuthor?.name
        description = buildString {
            summary?.let { append(it) }
            if (publisher != null) {
                append("\n\nPublisher: ${publisher.name}")
            }
        }
        genre = editorTags?.joinToString { it.name }
        status = if (isComplete == true) SManga.COMPLETED else SManga.ONGOING
        thumbnail_url
    }
}

@Serializable
class TitleAuthor(
    val name: String,
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
class SerialStory(
    val serialStoryId: String,
)


@Serializable
class ChapterResponse(
    val stories: List<ChapterStories>,
)

@Serializable
class ChapterStories(
    val name: String,
    val volumeSortNo: Int,
    val sellGoods: Goods?,
    val freeTypeGoods: Goods?,
    val isNormalFree: Boolean?,
    val isPurchased: Boolean?,
    val serialStory: SerialStory,
) {
    val isLocked: Boolean
        get() = isNormalFree == false && isPurchased == false

    fun toSChapter() = SChapter.create().apply {
        val goods = sellGoods ?: freeTypeGoods!!
        val lock = if (isLocked) "🔒 " else ""
        url = "${goods.bookCd}#${serialStory.serialStoryId}"
        name = lock + this@ChapterStories.name
        date_upload = if (sellGoods != null) dateFormat.tryParse(sellGoods.saleStartDatetime) else dateFormat.tryParse(freeTypeGoods?.saleStartDatetime)
        chapter_number = volumeSortNo.toFloat()
    }
}

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
)

@Serializable
class ViewerDrmResponse(
    @SerialName("file_id") val fileId: String,
    val path: String,
    val payload: String,
)
