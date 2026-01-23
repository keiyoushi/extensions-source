package eu.kanade.tachiyomi.extension.all.yabai

import eu.kanade.tachiyomi.extension.all.yabai.Yabai.Companion.createdAtFormat
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Date

@Serializable
class QueryDto(
    var cat: String = "",
    var lng: String = "",
    private val qry: String = "",
    private val tag: String = "[]",
    private val cursor: String?,
)

@Serializable
class DataResponse<T>(
    val props: T,
)

@Serializable
class IndexProps(
    @SerialName("post_list")
    val postList: PostList,
)

@Serializable
class PostList(
    val data: List<GalleryItem>,
    val meta: Meta,
)

@Serializable
class GalleryItem(
    private val slug: String,
    private val name: String,
    private val cover: String,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        url = "/g/$slug"
        thumbnail_url = cover
        status = SManga.COMPLETED
    }
}

@Serializable
class Meta(
    @SerialName("next_cursor")
    val nextCursor: String?,
)

@Serializable
class DetailProps(
    val post: Post,
)

@Serializable
class Post(
    val data: Gallery,
)

@Serializable
class Gallery(
    private val slug: String,
    private val name: String,
    private val cover: String,
    private val tags: Map<String, List<Tag>>?,
    private val date: PostDate,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        url = "/g/$slug"
        thumbnail_url = cover
        author = tags
            ?.filterKeys { it == "Group" }
            ?.flatMap { it.value }
            ?.joinToString { it.name }
        artist = tags
            ?.filterKeys { it == "Artist" }
            ?.flatMap { it.value }
            ?.joinToString { it.name }
        genre = tags
            ?.filterKeys { it != "Group" && it != "Artist" }
            ?.flatMap { it.value }
            ?.joinToString { it.fullName ?: it.name }
        status = SManga.COMPLETED
    }

    fun toSChapter() = SChapter.create().apply {
        name = "Chapter"
        url = "/g/$slug"
        date_upload = try {
            date.toDate()!!.time
        } catch (e: Exception) {
            0L
        }
    }
}

@Serializable
class Tag(
    val name: String,
    @SerialName("full_name")
    val fullName: String? = null,
)

@Serializable
class PostDate(
    private val default: String,
) {
    fun toDate(): Date? = createdAtFormat.parse(default)
}

@Serializable
class ReaderProps(
    val pages: Pages,
)

@Serializable
class Pages(
    val data: PagesData,
)

@Serializable
class PagesData(
    val list: PagesList,
)

@Serializable
class PagesList(
    private val root: String,
    private val code: Int,
    private val head: List<String>,
    private val hash: List<String>,
    private val rand: List<String>,
    private val type: List<String>,
) {
    fun toPages() = head
        .mapIndexed { index, pageNumber -> Triple(pageNumber, index, index) }
        .sortedBy { it.first.toInt() }
        .mapIndexed { sortedIndex, (pageNumber, originalIndex, _) ->
            Page(
                sortedIndex,
                imageUrl = "$root/$code/${pageNumber.padStart(4, '0')}-${hash[originalIndex]}-${rand[originalIndex]}.${type[originalIndex]}",
            )
        }
}
