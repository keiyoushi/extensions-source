package eu.kanade.tachiyomi.extension.en.webnovel

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.util.Locale

@Serializable
class ResponseWrapper<T>(
    val code: Int,
    val data: T?,
    val msg: String,
)

@Serializable
class QuerySearchResponse(
    @SerialName("comicInfo") private val response: BrowseResponse<QuerySearchItem>,
) {
    fun toMangasPage(coverUrl: (id: String, coverUpdatedAt: Long) -> String): MangasPage {
        return response.toMangasPage(coverUrl)
    }
}

typealias FilterSearchResponse = BrowseResponse<FilterSearchItem>

@Serializable
class BrowseResponse<T : ComicItem>(
    private val isLast: Int,
    @JsonNames("comicItems") private val items: List<T>,
) {
    fun toMangasPage(coverUrl: (id: String, coverUpdatedAt: Long) -> String): MangasPage {
        return MangasPage(
            mangas = items.map { it.toSManga(coverUrl) },
            hasNextPage = isLast == 0,
        )
    }
}

@Serializable
class QuerySearchItem(
    @SerialName("comicId") private val id: String,
    @SerialName("bookName") private val title: String,
    @SerialName("categoryName") private val genre: String,
    @SerialName("CV") private val coverUpdatedAt: Long,
) : ComicItem {
    override fun toSManga(coverUrl: (id: String, coverUpdatedAt: Long) -> String): SManga {
        return SManga.create().also {
            it.url = id
            it.title = title
            it.genre = genre
            it.thumbnail_url = coverUrl(id, coverUpdatedAt)
        }
    }
}

@Serializable
class FilterSearchItem(
    @SerialName("bookId") private val id: String,
    @SerialName("bookName") private val title: String,
    @SerialName("authorName") private val author: String,
    private val description: String,
    @SerialName("categoryName") private val genre: String,
    @SerialName("coverUpdateTime") private val coverUpdatedAt: Long,
) : ComicItem {
    override fun toSManga(coverUrl: (id: String, coverUpdatedAt: Long) -> String): SManga {
        return SManga.create().also {
            it.url = id
            it.title = title
            it.author = author
            it.description = description
            it.genre = genre
            it.thumbnail_url = coverUrl(id, coverUpdatedAt)
        }
    }
}

@Serializable
class ComicDetailInfoResponse(
    @SerialName("comicInfo") private val comic: ComicDetailInfo,
) : ComicItem {
    override fun toSManga(coverUrl: (id: String, coverUpdatedAt: Long) -> String): SManga {
        return comic.toSManga(coverUrl)
    }
}

@Serializable
data class ComicDetailInfo(
    @SerialName("comicId") private val id: String,
    @SerialName("comicName") private val title: String,
    @SerialName("authorName") private val author: String,
    private val description: String,
    private val updateCycle: String,
    @SerialName("categoryName") private val genre: String,
    @SerialName("actionStatus") private val status: Int,
    @SerialName("CV") private val coverUpdatedAt: Long,
) : ComicItem {
    override fun toSManga(coverUrl: (id: String, coverUpdatedAt: Long) -> String): SManga {
        return SManga.create().also {
            it.url = id
            it.title = title
            it.author = author
            it.description = buildString {
                append(description)
                if (status == ONGOING && updateCycle.isNotBlank()) {
                    append("\n\nInformation:")
                    append("\n• ${updateCycle.replaceFirstChar { c -> c.uppercase(Locale.ENGLISH) }}")
                }
            }
            it.genre = genre
            it.status = when (status) {
                ONGOING -> SManga.ONGOING
                COMPLETED -> SManga.COMPLETED
                ON_HIATUS -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            it.thumbnail_url = coverUrl(id, coverUpdatedAt)
        }
    }

    companion object {
        private const val ONGOING = 1
        private const val COMPLETED = 2
        private const val ON_HIATUS = 3
    }
}

@Serializable
class ComicChapterListResponse(
    @SerialName("comicInfo") val comic: Comic,
    @SerialName("comicChapters") val chapters: List<ComicChapter>,
) {
    @Serializable
    class Comic(@SerialName("comicId") val id: String)
}

@Serializable
class ComicChapter(
    @SerialName("chapterId") val id: String,
    @SerialName("chapterName") val name: String,
    val publishTime: String,

    private val chapterLevel: Int,
    private val userLevel: Int,
    private val price: Int,
    private val isVip: Int,
    private val isAuth: Int,
) {
    val isLocked = isPremium && !isAccessibleByUser

    // You can pay to get some chapter earlier than others. This privilege is divided into some tiers
    // We check if user's tier same or more than chapter's.
    val isVisible = userLevel >= chapterLevel

    private val isPremium: Boolean get() = isVip != 0 || price != 0

    // This can mean the chapter is free or user has paid to unlock it (check with [isPremium] for this case)
    private val isAccessibleByUser: Boolean get() = isAuth == 1
}

@Serializable
class ChapterContentResponse(
    @SerialName("chapterInfo") val data: ChapterContent,
)

@Serializable
class ChapterContent(
    @SerialName("chapterId") val id: Long,
    @SerialName("chapterPage") val pages: List<ChapterPage>,
)

@Serializable
class ChapterPage(
    @SerialName("pageId") val id: String,
    val url: String,
)

interface ComicItem {
    fun toSManga(coverUrl: (id: String, coverUpdatedAt: Long) -> String): SManga
}
