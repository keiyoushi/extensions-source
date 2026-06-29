package eu.kanade.tachiyomi.extension.all.yabai

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val createdAtFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Serializable
class QueryDto(
    @SerialName("cat") val cat: String,
    @SerialName("lng") val lng: String,
    @SerialName("qry") val qry: String,
    @SerialName("tag") val tag: String,
    @SerialName("cursor") val cursor: String?,
)

@Serializable
class DataResponse<T>(
    @SerialName("props") val props: T,
)

@Serializable
class IndexProps(
    @SerialName("post_list") val postList: PostList,
)

@Serializable
class PostList(
    @SerialName("data") val data: List<GalleryItem>,
    @SerialName("meta") val meta: Meta,
)

@Serializable
class GalleryItem(
    @SerialName("slug") private val slug: String,
    @SerialName("name") private val name: String,
    @SerialName("cover") private val cover: String,
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
    @SerialName("next_cursor") val nextCursor: String?,
)

@Serializable
class DetailProps(
    @SerialName("post") val post: Post,
)

@Serializable
class Post(
    @SerialName("data") val data: Gallery,
)

@Serializable
class Gallery(
    @SerialName("slug") private val slug: String,
    @SerialName("name") private val name: String,
    @SerialName("cover") private val cover: String,
    @SerialName("tags") private val tags: Map<String, List<Tag>>? = null,
    @SerialName("date") private val date: PostDate? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        url = "/g/$slug"
        thumbnail_url = cover
        author = tags?.get("Group")?.joinToString { it.name }
        artist = tags?.get("Artist")?.joinToString { it.name }
        genre = tags?.filterKeys { it != "Group" && it != "Artist" }
            ?.flatMap { it.value }
            ?.joinToString { it.fullName ?: it.name }
        status = SManga.COMPLETED
    }

    fun toSChapter() = SChapter.create().apply {
        name = "Chapter"
        url = "/g/$slug"
        date_upload = date?.toDate() ?: 0L
    }
}

@Serializable
class Tag(
    @SerialName("name") val name: String,
    @SerialName("full_name") val fullName: String? = null,
)

@Serializable
class PostDate(
    @SerialName("default") private val default: String,
) {
    fun toDate(): Long = createdAtFormat.tryParse(default)
}

@Serializable
class ReaderProps(
    @SerialName("pages") val pages: Pages,
)

@Serializable
class Pages(
    @SerialName("data") val data: PagesData,
)

@Serializable
class PagesData(
    @SerialName("list") val list: PagesList,
)

@Serializable
class PagesList(
    @SerialName("root") private val root: String,
    @SerialName("code") private val code: Int,
    @SerialName("head") private val head: List<String>,
    @SerialName("hash") private val hash: List<String>,
    @SerialName("rand") private val rand: List<String>,
    @SerialName("type") private val type: List<String>,
) {
    fun toPages(): List<Page> = head.mapIndexed { index, pageNumber ->
        Pair(pageNumber, index)
    }
        .sortedBy { it.first.toInt() }
        .mapIndexed { sortedIndex, (pageNumber, originalIndex) ->
            Page(
                sortedIndex,
                imageUrl = "$root/$code/${pageNumber.padStart(4, '0')}-${hash[originalIndex]}-${rand[originalIndex]}.${type[originalIndex]}",
            )
        }
}
