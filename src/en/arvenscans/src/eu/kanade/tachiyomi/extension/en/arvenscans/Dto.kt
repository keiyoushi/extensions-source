package eu.kanade.tachiyomi.extension.en.arvenscans

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
class SearchResponseDto(
    val posts: List<PostSummaryDto>,
    val totalCount: Int,
)

@Serializable
class PostSummaryDto(
    val id: Int,
    val slug: String,
    val postTitle: String,
    val featuredImage: String? = null,
    val seriesStatus: String? = null,
    val genres: List<GenreDto> = emptyList(),
)

@Serializable
class GenreDto(
    val name: String,
)

@Serializable
class PostResponseDto(
    val post: PostDto,
)

@Serializable
class PostDto(
    val id: Int,
    val slug: String,
    val postTitle: String,
    val postContent: String? = null,
    val alternativeTitles: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val featuredImage: String? = null,
    val seriesType: String? = null,
    val seriesStatus: String? = null,
    val genres: List<GenreDto> = emptyList(),
    val chapters: List<PostChapterDto> = emptyList(),
)

@Serializable
class PostChapterDto(
    val id: Int,
    val slug: String,
    val number: JsonPrimitive,
    val title: String? = null,
    val createdAt: String,
    val isLocked: Boolean? = null,
    val isAccessible: Boolean? = null,
)

@Serializable
class ChapterResponseDto(
    val chapter: ChapterDto,
)

@Serializable
class ChapterDto(
    val isLocked: Boolean? = null,
    val isAccessible: Boolean? = null,
    val images: List<ChapterImageDto> = emptyList(),
)

@Serializable
class ChapterImageDto(
    val url: String,
    val order: Int? = null,
)
