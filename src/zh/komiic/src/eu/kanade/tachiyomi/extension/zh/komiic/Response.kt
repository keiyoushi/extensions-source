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
    val comics: List<Comic>,
    val authors: List<Author>,
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
    val id: String,
    val title: String,
    val status: String,
    val year: Int,
    val imageUrl: String,
    var authors: List<ComicAuthor>,
    val categories: List<ComicCategory>,
    val dateCreated: String = "",
    val dateUpdated: String,
    val monthViews: Int = 0,
    val views: Int,
    val favoriteCount: Int,
    val lastBookUpdate: String,
    val lastChapterUpdate: String,
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
    val id: String,
    val name: String,
    @SerialName("__typename") val typeName: String,
)

@Serializable
data class ComicAuthor(
    val id: String,
    val name: String,
    @SerialName("__typename") val typeName: String,
)

@Serializable
data class Author(
    val id: String,
    val name: String,
    val chName: String,
    val enName: String,
    val wikiLink: String,
    val comicCount: Int,
    val views: Int,
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
    val id: String,
    val serial: String,
    val type: String,
    val dateCreated: String,
    val dateUpdated: String,
    val size: Int,
    @SerialName("__typename") val typeName: String,
)

@Serializable
data class ImagesResponse(
    @SerialName("imagesByChapterId") val images: List<Image>,
)

@Serializable
data class Image(
    val id: String,
    val kid: String,
    val height: Int,
    val width: Int,
    @SerialName("__typename") val typeName: String,
)

@Serializable
data class APILimitData(
    @SerialName("getImageLimit") val getImageLimit: APILimit,
)

@Serializable
data class APILimit(
    val limit: Int,
    val usage: Int,
    val resetInSeconds: String,
    @SerialName("__typename") val typeName: String,
)
