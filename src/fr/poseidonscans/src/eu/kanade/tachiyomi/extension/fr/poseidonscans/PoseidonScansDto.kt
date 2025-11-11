package eu.kanade.tachiyomi.extension.fr.poseidonscans

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

@Serializable
class LatestApiManga(
    val title: String,
    val slug: String,
    val coverImage: String?,
)

@Serializable
class LatestApiResponse(
    val success: Boolean,
    val data: List<LatestApiManga> = emptyList(),
    val total: Int? = null,
)

@Serializable
class MangaDetailsData(
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
class CategoryData(val name: String)

@Serializable
class ChapterData(
    val number: Float,
    val title: String? = null,
    val createdAt: String,
    val isPremium: Boolean? = false,
    val premiumUntil: String? = null,
    val isVolume: Boolean? = false,
)

@Serializable
class PageImageUrlData(
    val originalUrl: String,
    val order: Int,
)

@Serializable
class PageDataRoot(
    val images: JsonArray? = null,
    val chapter: PageDataChapter? = null,
    val initialData: PageDataInitialData? = null,
)

@Serializable
class PageDataChapter(
    val images: JsonArray? = null,
)

@Serializable
class PageDataInitialData(
    val images: JsonArray? = null,
    val chapter: PageDataChapter? = null,
)
