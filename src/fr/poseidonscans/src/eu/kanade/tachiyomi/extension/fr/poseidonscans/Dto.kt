package eu.kanade.tachiyomi.extension.fr.poseidonscans

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
class LatestApiManga(
    val title: String,
    val slug: String,
)

@Serializable
class LatestApiResponse(
    val data: List<LatestApiManga> = emptyList(),
)

@Serializable
class PopularMangaData(
    val id: String,
    val title: String,
    val slug: String,
)

@Serializable
class MangaPageDetailsData(
    val isPremiumUser: Boolean?,
    val manga: MangaDetailsData,
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
    val createdAt: Instant,
    private val isPremium: Boolean? = false,
    val premiumUntil: Instant? = null,
    val isVolume: Boolean? = false,
) {
    val isLocked: Boolean get() = isPremium == true && isBeforeFreeDate(premiumUntil)
}

@Serializable
class PageData(
    val currentChapter: CurrentChapterData,
    val initialData: InitialData,
    var isPremiumUser: Boolean,
    var sessionStatus: String,
)

@Serializable
class CurrentChapterData(
    private val isPremium: Boolean,
    private val premiumUntil: Instant? = null,
) {
    val isLocked: Boolean get() = isPremium && isBeforeFreeDate(premiumUntil)
}

// The site keeps isPremium set after premiumUntil has passed
private fun isBeforeFreeDate(premiumUntil: Instant?) = premiumUntil == null || premiumUntil > Clock.System.now()

@Serializable
class InitialData(
    val images: List<ImageData>,
)

@Serializable
class ImageData(
    val originalUrl: String,
    val order: Int,
)
