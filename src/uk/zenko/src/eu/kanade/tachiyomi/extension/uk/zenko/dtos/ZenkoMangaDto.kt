package eu.kanade.tachiyomi.extension.uk.zenko.dtos

import kotlinx.serialization.Serializable

@Serializable
class ZenkoMangaListResponse(
    val `data`: List<MangaDetailsResponse>,
    val meta: Meta,
)

@Serializable
class Meta(
    val hasNextPage: Boolean,
)

@Serializable
class MangaDetailsResponse(
    val author: Author? = null,
    val coverImg: String,
    val description: String,
    val engName: String? = null,
    val genres: List<Genre>? = null,
    val id: Int,
    val name: String,
    val status: String,
)

@Serializable
class Genre(
    val name: String,
)

@Serializable
class Author(
    val username: String? = null,
)

@Serializable
class ChapterResponseItem(
    val createdAt: Long? = null,
    val id: Int,
    val name: String?,
    val pages: List<Page>? = null,
    val titleId: Int?,
    val publisher: Publisher? = null,
)

@Serializable
class Page(
    val id: Int,
    val content: String,
)

@Serializable
class Publisher(
    val name: String? = null,
)
