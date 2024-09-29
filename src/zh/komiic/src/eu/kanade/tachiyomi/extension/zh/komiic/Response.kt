package eu.kanade.tachiyomi.extension.zh.komiic

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Data<T>(val data: T)

interface ComicListResult {
    val comics: List<Comic>
}

@Serializable
data class HotComicsResponse(
    @SerialName("hotComics") override val comics: List<Comic>,
) : ComicListResult

@Serializable
data class RecentUpdateResponse(
    @SerialName("recentUpdate") override val comics: List<Comic>,
) : ComicListResult

interface SearchResult {
    val action: ComicsAndAuthors
}

@Serializable
data class SearchResponse(
    @SerialName("searchComicsAndAuthors") override val action: ComicsAndAuthors,
) : SearchResult

@Serializable
data class ComicsAndAuthors(
    @SerialName("comics") val comics: List<Comic>,
    @SerialName("authors") val authors: List<Author>,
    @SerialName("__typename") val typeName: String,
)

interface ComicResult {
    val comic: Comic
}

@Serializable
data class ComicByIDResponse(
    @SerialName("comicById") override val comic: Comic,
) : ComicResult

@Serializable
data class Comic(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("status") val status: String,
    @SerialName("year") val year: Int,
    @SerialName("imageUrl") val imageUrl: String,
    @SerialName("authors") var authors: List<ComicAuthor>,
    @SerialName("categories") val categories: List<ComicCategory>,
    @SerialName("dateCreated") val dateCreated: String = "",
    @SerialName("dateUpdated") val dateUpdated: String,
    @SerialName("monthViews") val monthViews: Int = 0,
    @SerialName("views") val views: Int,
    @SerialName("favoriteCount") val favoriteCount: Int,
    @SerialName("lastBookUpdate") val lastBookUpdate: String,
    @SerialName("lastChapterUpdate") val lastChapterUpdate: String,
    @SerialName("__typename") val typeName: String,
) {
    private val parseStatus = when (status) {
        "ONGOING" -> SManga.ONGOING
        "END" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    fun toSManga() = SManga.create().apply {
        url = "/comic/$id"
        title = this@Comic.title
        thumbnail_url = this@Comic.imageUrl
        author = this@Comic.authors.joinToString { it.name }
        genre = this@Comic.categories.joinToString { it.name }
        description = buildString {
            append("年份: $year | ")
            append("點閱: ${simplifyNumber(views)} | ")
            append("喜愛: ${simplifyNumber(favoriteCount)}\n")
        }
        status = parseStatus
        initialized = true
    }
}

@Serializable
data class ComicCategory(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("__typename") val typeName: String,
)

@Serializable
data class ComicAuthor(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("__typename") val typeName: String,
)

@Serializable
data class Author(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("chName") val chName: String,
    @SerialName("enName") val enName: String,
    @SerialName("wikiLink") val wikiLink: String,
    @SerialName("comicCount") val comicCount: Int,
    @SerialName("views") val views: Int,
    @SerialName("__typename") val typeName: String,
)

interface ChaptersResult {
    val chapters: List<Chapter>
}

@Serializable
data class ChaptersResponse(
    @SerialName("chaptersByComicId") override val chapters: List<Chapter>,
) : ChaptersResult

@Serializable
data class Chapter(
    @SerialName("id") val id: String,
    @SerialName("serial") val serial: String,
    @SerialName("type") val type: String,
    @SerialName("dateCreated") val dateCreated: String,
    @SerialName("dateUpdated") val dateUpdated: String,
    @SerialName("size") val size: Int,
    @SerialName("__typename") val typeName: String,
)

interface ImagesResult {
    val images: List<Image>
}

@Serializable
data class ImagesResponse(
    @SerialName("imagesByChapterId") override val images: List<Image>,
) : ImagesResult

@Serializable
data class Image(
    @SerialName("id") val id: String,
    @SerialName("kid") val kid: String,
    @SerialName("height") val height: Int,
    @SerialName("width") val width: Int,
    @SerialName("__typename") val typeName: String,
)

interface APILimitResult {
    val getImageLimit: APILimit
}

@Serializable
data class APILimitData(
    @SerialName("getImageLimit") override val getImageLimit: APILimit,
) : APILimitResult

@Serializable
data class APILimit(
    @SerialName("limit") val limit: Int,
    @SerialName("usage") val usage: Int,
    @SerialName("resetInSeconds") val resetInSeconds: String,
    @SerialName("__typename") val typeName: String,
)
