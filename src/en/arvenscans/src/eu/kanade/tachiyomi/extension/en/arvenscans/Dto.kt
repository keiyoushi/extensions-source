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
    val id: Int? = null,
    val slug: String? = null,
    val postTitle: String,
    val postContent: String? = null,
    val alternativeTitles: String? = null,
    val author: String? = null,
    val studio: String? = null,
    val artist: String? = null,
    val featuredImage: String? = null,
    val seriesType: String? = null,
    val seriesStatus: String? = null,
    val genres: List<GenreDto> = emptyList(),
)

@Serializable
class ChaptersResponseDto(
    val post: ChaptersPostDto,
)

@Serializable
class ChaptersPostDto(
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    val id: Int,
    val slug: String,
    val number: JsonPrimitive,
    val title: String? = null,
    val createdAt: String,
    val isLocked: Boolean? = null,
    val isAccessible: Boolean? = null,
    val mangaPost: ChapterMangaPostDto? = null,
)

@Serializable
class ChapterMangaPostDto(
    val slug: String,
)
