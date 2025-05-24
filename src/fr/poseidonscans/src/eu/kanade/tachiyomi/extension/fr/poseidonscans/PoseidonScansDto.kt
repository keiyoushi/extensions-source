package eu.kanade.tachiyomi.extension.fr.poseidonscans

import kotlinx.serialization.Serializable

@Serializable
data class LatestApiManga(
    val title: String,
    val slug: String,
    val coverImage: String?,
)

@Serializable
data class LatestApiResponse(
    val success: Boolean,
    val data: List<LatestApiManga> = emptyList(),
    val total: Int? = null,
)

@Serializable
data class MangaDetailsData(
    val title: String,
    val slug: String,
    val description: String?,
    val coverImage: String?,
    val type: String?,
    val status: String?,
    val artist: String?,
    val author: String?,
    val alternativeNames: String?,
    val categories: List<CategoryData>? = emptyList(),
    val chapters: List<ChapterData>? = emptyList(),
)

@Serializable
data class CategoryData(val name: String)

@Serializable
data class ChapterData(
    val number: Float,
    val title: String? = null,
    val createdAt: String,
    val isPremium: Boolean? = false,
)

@Serializable
data class PageImageUrlData(
    val originalUrl: String,
    val order: Int,
)
