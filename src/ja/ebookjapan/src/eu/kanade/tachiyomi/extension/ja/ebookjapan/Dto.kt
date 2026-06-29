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
class SearchResponse(
    val totalResults: Int,
    val items: List<Titles>,
)

@Serializable
class Titles(
    val titleId: String,
    val name: String,
    private val lastPublication: LastPublication?,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        url = titleId
        title = name
        thumbnail_url = "$cdnUrl/${lastPublication?.goods?.imageFileName}"
    }
}

@Serializable
class LastPublication(
    val goods: TitleGoods?,
)

@Serializable
class TitleGoods(
    val imageFileName: String?,
)

@Serializable
class DetailResponse(
    val title: DetailTitles,
    val serialStory: SerialStory?,
)

@Serializable
class DetailTitles(
    private val summary: String?,
    private val titleAuthor: TitleAuthor?,
    private val name: String,
    private val lastPublication: LastPublication?,
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
        thumbnail_url = "$cdnUrl/${lastPublication?.goods?.imageFileName}"
    }
}

@Serializable
class SerialStory(
    val serialStoryId: String?,
)

@Serializable
class TitleAuthor(
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
class Goods(
    val bookCd: String,
    val saleStartDatetime: String?,
)

@Serializable
class VolumesResponse(
    val publications: List<Publication>,
)

@Serializable
class Publication(
    private val name: String,
    private val volumeSortNo: Int? = null,
    private val saleDate: String? = null,
    private val goods: PublicationGoods,
    private val isPurchased: Boolean? = null,
) {
    val isLocked: Boolean
        get() = goods.isFree == false && isPurchased == false

    fun toSChapter() = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        url = goods.bookCd
        name = lock + this@Publication.name
        date_upload = dateFormat.tryParse(saleDate)
        chapter_number = volumeSortNo?.toFloat() ?: -1f
    }
}

@Serializable
class PublicationGoods(
    val bookCd: String,
    val isFree: Boolean? = null,
)

private val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("Asia/Tokyo")
}

@Suppress("unused")
@Serializable
class ViewerBody(
    private val type: String,
    private val code: String,
    private val ssid: String?,
    private val light: Boolean,
)

@Suppress("unused")
@Serializable
class ViewerVolumeBody(
    private val type: String,
    private val code: String,
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
    val code: String,
    val payload: String,
)
