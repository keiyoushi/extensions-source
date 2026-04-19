package eu.kanade.tachiyomi.extension.en.arvenscans

import kotlinx.serialization.Serializable

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
