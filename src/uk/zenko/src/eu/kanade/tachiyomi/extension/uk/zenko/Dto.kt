package eu.kanade.tachiyomi.extension.uk.zenko

import kotlinx.serialization.Serializable

@Serializable
class SearchResponse(
    val data: List<SearchDetailsResponse>,
    val meta: Meta,
)

@Serializable
class Meta(
    val hasNextPage: Boolean,
)

@Serializable
class SearchDetailsResponse(
    val id: Int,
    val name: String,
    val engName: String? = null,
    val coverImg: String,
)

@Serializable
class MangaDetailsResponse(
    val id: Int,
    val coverImg: String,
    val description: String,
    val status: String,
    val name: String,
    val engName: String? = null,
    val originalName: String? = null,
    val genres: List<NameList>? = null,
    val tags: List<NameList>? = null,
    val likesCount: Int? = null,
    val viewsCount: Int? = null,
    val bookmarksCount: Int? = null,
    val writers: List<NameList>? = null,
    val painters: List<NameList>? = null,
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

@Serializable
class NameList(
    val name: String,
)
