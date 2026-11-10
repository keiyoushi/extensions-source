package eu.kanade.tachiyomi.extension.en.yurivan

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

const val MEMO_STORY_ID = "storyId"
const val MEMO_CHAPTER_INDEX = "chapterIndex"

@Serializable
class FeedResponse(val data: FeedData)

@Serializable
class FeedData(
    val items: List<FeedItem>,
    @SerialName("has_more") val hasMore: Boolean,
)

@Serializable
class FeedItem(
    private val id: String,
    private val title: String,
    @SerialName("thumb_url") private val thumbUrl: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = id
        title = this@FeedItem.title
        thumbnail_url = thumbUrl
    }
}

@Serializable
class LdJsonScript(val children: String)

@Serializable
class ComicSeriesDto(
    private val name: String,
    private val description: String? = null,
    private val image: String? = null,
    private val keywords: String? = null,
    private val creativeWorkStatus: String? = null,
    private val author: PersonDto? = null,
    private val aggregateRating: RatingDto? = null,
) {
    fun toSManga(id: String) = SManga.create().apply {
        url = id
        title = name
        thumbnail_url = image
        author = this@ComicSeriesDto.author?.name
        genre = keywords
        status = when (creativeWorkStatus?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .rating(aggregateRating?.ratingValue, max = 5, votes = aggregateRating?.ratingCount)
            .separator()
            .description(this@ComicSeriesDto.description)
            .build()
    }
}

@Serializable
class PersonDto(val name: String)

@Serializable
class RatingDto(
    val ratingValue: Double,
    val ratingCount: Int,
)

@Serializable
class ChapterListDto(
    private val storyId: String,
    private val chapters: List<ChapterDto>,
) {
    fun toSChapters() = chapters.sortedByDescending { it.chapterIndex }.map { it.toSChapter(storyId) }
}

@Serializable
class ChapterDto(
    private val id: String,
    @SerialName("chapter_index") val chapterIndex: Int,
    private val title: String? = null,
    @SerialName("created_at") private val createdAt: String? = null,
) {
    fun toSChapter(storyId: String) = SChapter.create().apply {
        url = id
        name = title?.takeIf { it.isNotBlank() } ?: "Chapter ${chapterIndex + 1}"
        date_upload = Instant.tryParse(createdAt)
        memo = buildJsonObject {
            put(MEMO_STORY_ID, storyId)
            put(MEMO_CHAPTER_INDEX, chapterIndex)
        }
    }
}

@Serializable
class ReaderChapterResponse(private val chapter: ReaderChapterDto) {
    fun toPages() = chapter.pages.mapIndexed { index, page -> Page(index, imageUrl = page.url) }
}

@Serializable
class ReaderChapterDto(val pages: List<PageDto>)

@Serializable
class PageDto(val url: String)

@Serializable
class TagsDto(val labels: Map<String, TagLabelDto>)

@Serializable
class TagLabelDto(val label: String)
