package eu.kanade.tachiyomi.extension.fr.hentaizonebeta

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class SearchResponseDto(
    val success: Boolean = false,
    val manga: List<MangaDto> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("has_next_page") val hasNextPage: Boolean = false,
)

@Serializable
data class MangaDto(
    val id: Long,
    val title: String,
    val permalink: String? = null,
    val thumbnail: String? = null,
    val status: String? = null,
    val type: String? = null,
    val chapters: Int? = null,
    val views: Long? = null,
    @SerialName("follower_count") val followerCount: Long? = null,
    @SerialName("last_update") val lastUpdate: Long? = null,
)

@Serializable
data class MangaDetailDto(
    val id: Long,
    val title: String,
    val slug: String,
    @SerialName("alt_title") val altTitle: String? = null,
    val content: String? = null,
    val status: String? = null,
    @SerialName("release_year") val releaseYear: String? = null,
    val authors: List<NamedDto> = emptyList(),
    val artists: List<NamedDto> = emptyList(),
    val genres: List<NamedDto> = emptyList(),
    val tags: List<NamedDto> = emptyList(),
    val views: Long? = null,
    @SerialName("cover_image") val coverImage: String? = null,
    @SerialName("banner_image") val bannerImage: String? = null,
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
data class NamedDto(
    val id: Long? = null,
    val name: String? = null,
    val slug: String? = null,
)

@Serializable
data class ChapterDto(
    val id: Long,
    @SerialName("chapter_slug") val chapterSlug: String,
    val name: String,
    @SerialName("chapter_number") val chapterNumber: JsonPrimitive? = null,
    val permalink: String? = null,
    @SerialName("upload_time") val uploadTime: Long? = null,
    val date: Long? = null,
    @SerialName("date_human") val dateHuman: String? = null,
)

@Serializable
data class ChapterImagesDto(
    val id: JsonPrimitive? = null,
    val title: String? = null,
    val images: List<ImageItemDto> = emptyList(),
)

@Serializable
data class ImageItemDto(
    val url: String,
    val alt: String? = null,
)
