package eu.kanade.tachiyomi.extension.en.ninehentai

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Manga(
    val id: Int,
    @SerialName("total_page") val totalPage: Int,
    private val title: String,
    @SerialName("image_server") private val imageServer: String,
) {
    fun toSManga() = SManga.create().apply {
        url = "/g/$id"
        this.title = this@Manga.title
        thumbnail_url = "$imageServer$id/cover-small.jpg"
    }

    fun getImageUrl() = "$imageServer$id"
}

@Serializable
class SearchRequest(
    private val text: String,
    private val page: Int,
    private val sort: Int,
    private val pages: Range,
    private val tag: Items,
)

@Serializable
class SearchRequestPayload(
    private val search: SearchRequest,
)

@Serializable
class Range(
    private val range: List<Int>,
)

@Serializable
class Items(
    private val items: TagArrays,
)

@Serializable
class TagArrays(
    private val included: List<Tag>,
    private val excluded: List<Tag>,
)

@Serializable
class Tag(
    val id: Int,
    private val name: String,
    private val type: Int = 1,
)

@Serializable
class SearchResponse(
    @SerialName("total_count") val totalCount: Int,
    val results: List<Manga>,
)

@Serializable
class SingleMangaResponse(
    val results: Manga,
)

@Serializable
class IdRequest(
    private val id: Int,
)

@Serializable
class TagRequest(
    @SerialName("tag_name") private val tagName: String,
    @SerialName("tag_type") private val tagType: Int,
)

@Serializable
class TagResponse(
    val results: List<Tag>,
)
