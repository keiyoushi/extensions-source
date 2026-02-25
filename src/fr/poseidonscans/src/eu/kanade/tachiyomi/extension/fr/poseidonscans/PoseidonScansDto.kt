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
    val description: String,
    val status: String,
    val artist: String?,
    val author: String?,
    val categories: List<CategoryData> = emptyList(),
    val chapters: List<ChapterData> = emptyList(),
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
class PageData(
    val currentChapter: CurrentChapterData,
    val initialData: InitalData,
    var isPremiumUser: Boolean,
    var sessionStatus: String,
)

@Serializable
class CurrentChapterData(
    val isPremium: Boolean,
)

@Serializable
class InitalData(
    val images: JsonArray,
)
