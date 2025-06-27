package eu.kanade.tachiyomi.extension.zh.komiic

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Data<T>(val data: T)

interface ComicListResult { val comics: List<Comic> }

@Serializable
data class HotComicsResponse(@SerialName("hotComics") override val comics: List<Comic>) : ComicListResult

@Serializable
data class RecentUpdateResponse(@SerialName("recentUpdate") override val comics: List<Comic>) : ComicListResult

@Serializable
data class ComicsAndAuthors(val comics: List<Comic>)

@Serializable
data class SearchResponse(@SerialName("searchComicsAndAuthors") val action: ComicsAndAuthors) : ComicListResult {
    override val comics: List<Comic>
        get() = action.comics
}

@Serializable
data class ComicByIDResponse(@SerialName("comicById") val comic: Comic)

@Serializable
data class Comic(
    val id: String,
    val title: String,
    val description: String,
    val status: String,
    val imageUrl: String,
    var authors: List<ComicAuthor>,
    val categories: List<ComicCategory>,
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
        description = this@Comic.description
        status = parseStatus
        initialized = this@Comic.description.isNotEmpty()
    }
}

@Serializable
data class ComicCategory(val id: String, val name: String)

@Serializable
data class ComicAuthor(val id: String, val name: String)

@Serializable
data class ChaptersResponse(@SerialName("chaptersByComicId") val chapters: List<Chapter>)

@Serializable
data class Chapter(
    val id: String,
    val serial: String,
    val type: String,
    val size: Int,
    val dateUpdated: String,
)

@Serializable
data class ImagesResponse(@SerialName("imagesByChapterId") val images: List<Image>)

@Serializable
data class Image(
    val id: String,
    val kid: String,
    val height: Int,
    val width: Int,
)

// @Serializable
// data class APILimitData(@SerialName("reachedImageLimit") val result: Boolean)
