package eu.kanade.tachiyomi.extension.all.comicklive

import android.graphics.Insets.add
import eu.kanade.tachiyomi.extension.all.comicklive.ComicData.Title
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonArray

@Serializable
class Data<T>(
    val data: T,
)

@Serializable
class SearchResponse(
    val data: List<BrowseComic>,
    @SerialName("next_cursor")
    val cursor: String? = null,
)

@Serializable
class BrowseComic(
    @SerialName("default_thumbnail")
    private val thumbnail: String,
    private val slug: String,
    private val title: String,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@BrowseComic.title
        thumbnail_url = thumbnail
    }
}

@Serializable
class Metadata(
    val genres: List<Name>,
    val tags: List<Name>,
) {
    @Serializable
    class Name(
        val name: String,
        val slug: String,
    )
}

@Serializable
class ComicData(
    val title: String,
    val slug: String,
    @SerialName("default_thumbnail")
    val thumbnail: String,
    val status: Int,
    @SerialName("translation_completed")
    val translationCompleted: Boolean,
    val artists: List<Name>,
    val authors: List<Name>,
    val desc: String,
    @SerialName("content_rating")
    val contentRating: String,
    val country: String,
    @SerialName("md_comic_md_genres")
    val genres: List<Genres>,
    @SerialName("md_titles")
    val titles: List<Title>,
) {
    @Serializable
    class Name(
        val name: String,
    )

    @Serializable
    class Title(
        val title: String,
    )

    @Serializable
    class Genres(
        @SerialName("md_genres")
        val genres: Name,
    )
}

object Transform : JsonTransformingSerializer<ComicData>(ComicData.serializer()) {

    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element !is JsonObject) return element

        val mdTitles = element["md_titles"] ?: return element
        if (mdTitles !is JsonObject) return element

        val titles = buildJsonArray { mdTitles.values.forEach { add(it) } }

        return JsonObject(element.toMutableMap().apply { put("md_titles", titles) })
    }
}

@Serializable
class ChapterList(
    val data: List<Chapter>,
    private val pagination: Pagination,
) {
    fun hasNextPage() = pagination.page < pagination.lastPage

    @Serializable
    class Chapter(
        val hid: String,
        val chap: String,
        val vol: String?,
        val lang: String,
        val title: String?,
        @SerialName("created_at")
        val createdAt: String,
        @SerialName("group_name")
        val groups: List<String>,
    )

    @Serializable
    class Pagination(
        @SerialName("current_page")
        val page: Int,
        @SerialName("last_page")
        val lastPage: Int,
    )
}

@Serializable
class PageListData(
    val chapter: ChapterData,
) {
    @Serializable
    class ChapterData(
        val images: List<Image>,
    ) {
        @Serializable
        class Image(
            val url: String,
        )
    }
}
