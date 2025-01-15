package eu.kanade.tachiyomi.extension.uk.zenko.dtos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ZenkoMangaListResponse(
    @SerialName("data")
    val `data`: List<MangaDetailsResponse>,
    @SerialName("meta")
    val meta: Meta,
)

@Serializable
data class Meta(
    @SerialName("hasNextPage")
    val hasNextPage: Boolean,
)

@Serializable
data class MangaDetailsResponse(
    @SerialName("author")
    val author: Author? = null,
    @SerialName("coverImg")
    val coverImg: String,
    @SerialName("description")
    val description: String,
    @SerialName("engName")
    val engName: String? = null,
    @SerialName("genres")
    val genres: List<Genre>? = null,
    @SerialName("id")
    val id: Int,
    @SerialName("name")
    val name: String,
    @SerialName("status")
    val status: String,
)

@Serializable
data class Genre(
    @SerialName("name")
    val name: String,
)

@Serializable
data class Author(
    @SerialName("username")
    val username: String? = null,
)

@Serializable
data class ChapterResponseItem(
    @SerialName("createdAt")
    val createdAt: Long? = null,
    @SerialName("id")
    val id: Int,
    @SerialName("name")
    val name: String?,
    @SerialName("pages")
    val pages: List<Page>? = null,
    @SerialName("titleId")
    val titleId: Int?,
    @SerialName("publisher")
    val publisher: Publisher? = null,
)

@Serializable
data class Page(
    val id: Int,
    @SerialName("imgUrl")
    val imgUrl: String,
)

@Serializable
data class Publisher(
    @SerialName("name")
    val name: String? = null,
)
