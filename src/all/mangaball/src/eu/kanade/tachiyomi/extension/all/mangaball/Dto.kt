package eu.kanade.tachiyomi.extension.all.mangaball

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchResponse(
    val data: List<SearchManga>,
    private val pagination: Pagination,
) {
    @Serializable
    class Pagination(
        @SerialName("current_page")
        val currentPage: Int,
        @SerialName("last_page")
        val lastPage: Int,
    )

    fun hasNextPage() = pagination.currentPage < pagination.lastPage
}

@Serializable
class SearchManga(
    val url: String,
    val name: String,
    val cover: String,
    val isAdult: Boolean,
)

@Serializable
class ChapterListResponse(
    @SerialName("ALL_CHAPTERS")
    val chapters: List<ChapterContainer>,
)

@Serializable
class ChapterContainer(
    @SerialName("number_float")
    val number: Float,
    val translations: List<Chapter>,
)

@Serializable
class Chapter(
    val id: String,
    val name: String,
    val language: String,
    val group: Group,
    val date: String,
    val volume: Int,
)

@Serializable
class Group(
    @SerialName("_id")
    val id: String,
    val name: String,
)

@Serializable
class Yoast(
    @SerialName("@graph")
    val graph: List<Graph>,
) {
    @Serializable
    class Graph(
        @SerialName("@type")
        val type: String,
        val url: String? = null,
    )
}
