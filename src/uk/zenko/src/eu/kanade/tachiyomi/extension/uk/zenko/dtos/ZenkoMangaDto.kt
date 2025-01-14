package eu.kanade.tachiyomi.extension.uk.zenko.dtos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ZenkoMangaListResponse(
    @SerialName("data")
    val `data`: List<MangaDetailsResponse>?,
    @SerialName("meta")
    val meta: Meta,
)

@Serializable
data class Meta(
    @SerialName("hasNextPage")
    val hasNextPage: Boolean,
    @SerialName("hasPreviousPage")
    val hasPreviousPage: Boolean,
    @SerialName("limit")
    val limit: Int,
    @SerialName("offset")
    val offset: Int,
    @SerialName("page")
    val page: Int,
    @SerialName("totalPages")
    val totalPages: Int,
    @SerialName("totalRecords")
    val totalRecords: Int,
)

@Serializable
data class MangaDetailsResponse(
    @SerialName("ageLimit")
    val ageLimit: Int,
    @SerialName("author")
    val author: Author? = null,
    @SerialName("authorId")
    val authorId: Int,
    @SerialName("bgImg")
    val bgImg: String,
    @SerialName("bookmarksCount")
    val bookmarksCount: Int? = null,
    @SerialName("category")
    val category: String,
    @SerialName("chaptersCount")
    val chaptersCount: Int? = null,
    @SerialName("commentsCount")
    val commentsCount: Int? = null,
    @SerialName("coverImg")
    val coverImg: String,
    @SerialName("createdAt")
    val createdAt: Long,
    @SerialName("description")
    val description: String,
    @SerialName("engName")
    val engName: String? = null,
    @SerialName("genres")
    val genres: List<Genre>? = null,
    @SerialName("id")
    val id: Int,
    @SerialName("lastChapterCreatedAt")
    val lastChapterCreatedAt: Long? = null,
    @SerialName("likesCount")
    val likesCount: Int? = null,
    @SerialName("name")
    val name: String,
    @SerialName("originalName")
    val originalName: String?,
    @SerialName("originalUrl")
    val originalUrl: String?,
    @SerialName("releaseYear")
    val releaseYear: Int,
    @SerialName("status")
    val status: String,
    @SerialName("tags")
    val tags: List<Tag>? = null,
    @SerialName("translationStatus")
    val translationStatus: String,
    @SerialName("updatedAt")
    val updatedAt: Int? = null,
    @SerialName("viewsCount")
    val viewsCount: Int? = null,
    @SerialName("visibilityStatus")
    val visibilityStatus: String? = null,
)

@Serializable
data class Tag(
    @SerialName("id")
    val id: Int,
    @SerialName("isVerified")
    val isVerified: Boolean,
    @SerialName("name")
    val name: String,
)

@Serializable
data class Genre(
    @SerialName("id")
    val id: Int,
    @SerialName("name")
    val name: String,
)

@Serializable
data class Author(
    @SerialName("id")
    val id: Int,
    @SerialName("username")
    val username: String? = null,
)

@Serializable
data class ChapterResponseItem(
    @SerialName("authorId")
    val authorId: Int?,
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
    @SerialName("updatedAt")
    val updatedAt: Long? = null,
    @SerialName("publisher")
    val publisher: Publisher? = null,
    @SerialName("publisherId")
    val publisherId: Int? = null,
)

@Serializable
data class Page(
    @SerialName("chapterId")
    val chapterId: Int,
    @SerialName("createdAt")
    val createdAt: Int,
    @SerialName("id")
    val id: Int,
    @SerialName("imgUrl")
    val imgUrl: String,
    @SerialName("order")
    val order: Int,
)

@Serializable
data class Publisher(
    @SerialName("avatar")
    val avatar: String? = null,
    @SerialName("bgImg")
    val bgImg: String? = null,
    @SerialName("contactUsLink")
    val contactUsLink: String? = null,
    @SerialName("createdAt")
    val createdAt: Int? = null,
    @SerialName("credits")
    val credits: String? = null,
    @SerialName("description")
    val description: String? = null,
    @SerialName("id")
    val id: Int? = null,
    @SerialName("isModerated")
    val isModerated: Boolean? = null,
    @SerialName("isPDFDisabled")
    val isPDFDisabled: Boolean? = null,
    @SerialName("links")
    val links: List<Link?>? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("ownerId")
    val ownerId: Int? = null,
    @SerialName("totalThanks")
    val totalThanks: Int? = null,
    @SerialName("visibilityStatus")
    val visibilityStatus: String? = null,
)

@Serializable
data class Link(
    @SerialName("isBlank")
    val isBlank: Boolean? = null,
    @SerialName("link")
    val link: String? = null,
    @SerialName("title")
    val title: String? = null,
    @SerialName("type")
    val type: String? = null,
)
